package com.emulator.j2me.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.emulator.j2me.data.GameModel
import dalvik.system.DexClassLoader
import javax.microedition.lcdui.Display
import javax.microedition.lcdui.Displayable
import javax.microedition.midlet.MIDlet
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class J2meClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    parent: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, null, parent) {
    override fun getResourceAsStream(name: String): InputStream? {
        return EmulatorEngine.getResourceAsStream(name) ?: super.getResourceAsStream(name)
    }
}

object EmulatorEngine {
    private var zipFile: ZipFile? = null
    private var classLoader: J2meClassLoader? = null

    @get:JvmStatic
    var currentMidlet: MIDlet? = null
        private set

    @get:JvmStatic
    var rmsDir: String = ""
        private set

    @get:JvmStatic
    var properties: Map<String, String> = emptyMap()
        private set

    private var gameThread: Thread? = null
    private var isRunning = false
    private var isStopping = false
    private var startupComplete = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Saved original dexElements for system classloader and app classloader
    private var originalSystemElements: Array<*>? = null
    private var originalAppElements: Array<*>? = null

    // Temp directory where we extract JAR resources for the system classloader
    private var resourceExtractDir: File? = null

    // Kept for fallback JAR injection when Element(File) constructor is unavailable
    private var jarFileForInjection: File? = null
    private var codeCacheDirForInjection: File? = null

    // ──────────────────────────────────────────────────────────────────────────
    //  Resource extraction & injection
    //
    //  Strategy:
    //    Android's ClassLoader.scl / systemClassLoader fields do not exist in
    //    ART (they were removed from libcore). We therefore cannot replace the
    //    system classloader object.
    //
    //    However, DexPathList.findResource() iterates through dexElements and,
    //    for DIRECTORY elements, simply checks whether File(element.path, name)
    //    exists. This is guaranteed to work on all Android versions.
    //
    //    So we:
    //      1. Extract every non-class entry from the game JAR into a temp dir.
    //      2. Create a DexPathList.Element that wraps that directory.
    //      3. Prepend it to the system classloader's dexElements array.
    //
    //    After this, ClassLoader.getSystemResourceAsStream("5") will find the
    //    file at <extractDir>/5 and return a valid stream, which is exactly
    //    what the game's h.ak() needs.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Extract all non-class entries from [jarFile] into [destDir].
     * Returns the destination directory on success, null on failure.
     */
    private fun extractJarResources(jarFile: File, destDir: File): File? {
        return try {
            destDir.mkdirs()
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    // Skip class files and META-INF — we only want game assets
                    if (name.endsWith(".class") || name.startsWith("META-INF/")) continue
                    val outFile = File(destDir, name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            Log.d("ClassLoaderHook", "Extracted JAR resources to: ${destDir.absolutePath}")
            destDir
        } catch (e: Exception) {
            Log.e("ClassLoaderHook", "Failed to extract JAR resources: ${e.message}", e)
            null
        }
    }

    /**
     * Inject a directory [Element] into [targetCL]'s DexPathList so that
     * PathClassLoader.getResourceAsStream(name) finds files in [dir].
     */
    @Suppress("UNCHECKED_CAST")
    private fun injectDirectoryElement(targetCL: ClassLoader, dir: File, isSystem: Boolean) {
        try {
            val pathListField =
                dalvik.system.BaseDexClassLoader::class.java.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(targetCL)

            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val current = dexElementsField.get(pathList) as Array<*>

            // Save original so we can restore on stop
            if (isSystem) {
                if (originalSystemElements == null) originalSystemElements = current
            } else {
                if (originalAppElements == null) originalAppElements = current
            }

            // Create a directory Element via reflection.
            // Element(File path) constructor creates a "file system" element:
            //   if path.isDirectory()  → directory element (findResource looks for file inside)
            //   if path.isFile()       → zip element (findResource looks inside the zip)
            val elementClass = Class.forName("dalvik.system.DexPathList\$Element")
            val ctor = elementClass.getDeclaredConstructors()
                .firstOrNull { c ->
                    c.parameterCount == 1 && c.parameterTypes[0] == File::class.java
                }

            if (ctor == null) {
                Log.w("ClassLoaderHook", "Could not find Element(File) constructor — trying JAR fallback")
                injectJarFallback(targetCL, isSystem)
                return
            }
            ctor.isAccessible = true
            val dirElement = ctor.newInstance(dir)

            // Prepend the new element so it's searched first
            val combined = java.lang.reflect.Array.newInstance(
                elementClass, current.size + 1
            ) as Array<Any?>
            combined[0] = dirElement
            System.arraycopy(current, 0, combined, 1, current.size)
            dexElementsField.set(pathList, combined)

            Log.d("ClassLoaderHook",
                "Injected directory element '${dir.name}' into ${if (isSystem) "system" else "app"} CL")
        } catch (e: Exception) {
            Log.e("ClassLoaderHook", "Failed to inject directory element: ${e.message}", e)
        }
    }

    /**
     * Fallback: inject the game JAR directly via DexClassLoader so its ZIP
     * entries are searchable by the target classloader's findResource().
     * This mirrors the approach that worked on previous Android versions.
     */
    @Suppress("UNCHECKED_CAST")
    private fun injectJarFallback(targetCL: ClassLoader, isSystem: Boolean) {
        val jar = jarFileForInjection ?: return
        val cacheDir = codeCacheDirForInjection ?: return
        try {
            val pathListField =
                dalvik.system.BaseDexClassLoader::class.java.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(targetCL)
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val current = dexElementsField.get(pathList) as Array<*>

            if (isSystem) {
                if (originalSystemElements == null) originalSystemElements = current
            } else {
                if (originalAppElements == null) originalAppElements = current
            }

            val tempLoader = dalvik.system.DexClassLoader(
                jar.absolutePath, cacheDir.absolutePath, null, targetCL
            )
            val tempPathList = pathListField.get(tempLoader)
            val tempElements = dexElementsField.get(tempPathList) as Array<*>

            val combined = java.lang.reflect.Array.newInstance(
                current.javaClass.componentType!!, current.size + tempElements.size
            ) as Array<Any?>
            System.arraycopy(current, 0, combined, 0, current.size)
            System.arraycopy(tempElements, 0, combined, current.size, tempElements.size)
            dexElementsField.set(pathList, combined)

            Log.d("ClassLoaderHook",
                "JAR fallback injection succeeded for ${if (isSystem) "system" else "app"} CL")
        } catch (e: Exception) {
            Log.e("ClassLoaderHook", "JAR fallback injection failed: ${e.message}", e)
        }
    }

    /** Restore a classloader's dexElements to its original state. */
    @Suppress("UNCHECKED_CAST")
    private fun restoreElements(targetCL: ClassLoader, original: Array<*>?) {
        if (original == null) return
        try {
            val pathListField =
                dalvik.system.BaseDexClassLoader::class.java.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(targetCL)
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            dexElementsField.set(pathList, original)
        } catch (e: Exception) {
            Log.e("ClassLoaderHook", "Failed to restore CL elements: ${e.message}", e)
        }
    }

    @JvmStatic
    fun startGame(
        context: Context,
        game: GameModel,
        onDisplayChanged: (Displayable?) -> Unit,
        onFinished: () -> Unit
    ) {
        isRunning = true
        isStopping = false
        startupComplete = false

        val rmsFolder = File(context.filesDir, "rms/${game.id}")
        rmsFolder.mkdirs()
        rmsDir = rmsFolder.absolutePath

        zipFile = ZipFile(File(game.jarPath))
        properties = parseManifest(File(game.jarPath))

        MIDlet.propertiesProvider = MIDlet.PropertiesProvider { key -> properties[key] }

        Display.setListener(object : Display.DisplayListener {
            override fun onCurrentDisplayableChanged(next: Displayable?) {
                if (next is javax.microedition.lcdui.Canvas) {
                    next.setDimensions(game.targetWidth, game.targetHeight)
                }
                mainHandler.post { onDisplayChanged(next) }
            }

            override fun onVibrate(duration: Int) {
                val vibrator =
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(
                                duration.toLong(),
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration.toLong())
                    }
                }
            }
        })

        MIDlet.listener = object : MIDlet.MIDletListener {
            override fun onMIDletDestroyed() {
                if (startupComplete && isRunning && !isStopping) {
                    Log.d("EmulatorEngine", "MIDlet notifyDestroyed() called - stopping game")
                    stopGame(onFinished)
                } else {
                    Log.w("EmulatorEngine",
                        "notifyDestroyed() called too early (startupComplete=$startupComplete) - ignoring")
                }
            }

            override fun onMIDletPaused() {}
        }

        val codeCacheDir = context.codeCacheDir
        val dexFile = File(game.dexPath)
        if (dexFile.exists()) dexFile.setReadOnly()

        // Store for use by injectJarFallback()
        jarFileForInjection = File(game.jarPath).also { if (it.exists()) it.setReadOnly() }
        codeCacheDirForInjection = codeCacheDir

        // ── Resource injection ────────────────────────────────────────────────
        // Extract game JAR non-class assets to a directory, then inject that
        // directory as an Element into both the system CL and the app CL so
        // that ClassLoader.getSystemResourceAsStream(name) returns a valid
        // stream when the game calls String.class.getResourceAsStream(name).
        val extractDir = File(context.cacheDir, "j2me_res_${game.id}")
        val extracted = extractJarResources(File(game.jarPath), extractDir)
        context_classLoader_ref = context.classLoader
        if (extracted != null) {
            resourceExtractDir = extracted
            ClassLoader.getSystemClassLoader()?.let { sys ->
                injectDirectoryElement(sys, extracted, isSystem = true)
            }
            context_classLoader_ref?.let { app ->
                injectDirectoryElement(app, extracted, isSystem = false)
            }
        } else {
            Log.w("ClassLoaderHook", "Resource extraction failed — falling back to ZipFile lookup only")
        }
        // ─────────────────────────────────────────────────────────────────────

        // Catch silent crashes in any game-spawned thread (loader threads, AMS callbacks, etc.)
        // Gameloft / obfuscated MIDlets often swallow exceptions in worker threads, leaving
        // static arrays uninitialized — which then surfaces as NPE in paint() much later.
        val previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("J2ME-ThreadCrash",
                "Uncaught ${e.javaClass.simpleName} in thread '${t.name}' (id=${t.id}): ${e.message}", e)
            previousDefaultHandler?.uncaughtException(t, e)
        }

        gameThread = Thread {
            try {
                // Ensure a patched DEX exists (transforms Class.getResourceAsStream
                // call-sites so they route through J2meResourceProxy instead of
                // the BootClassLoader which cannot find JAR assets on Android).
                val activeDexPath = ensurePatchedDex(game, codeCacheDir)

                val cl = J2meClassLoader(activeDexPath, codeCacheDir.absolutePath, context.classLoader)
                classLoader = cl
                // Belt-and-suspenders: set context classloader so any thread the
                // game spawns inherits a classloader that can serve JAR resources.
                Thread.currentThread().contextClassLoader = cl

                Log.d("EmulatorEngine", "Loading MIDlet class: ${game.mainClass}")
                val midletClass = cl.loadClass(game.mainClass)
                val midletInstance = midletClass.getDeclaredConstructor().newInstance() as MIDlet
                currentMidlet = midletInstance

                Log.d("EmulatorEngine", "Calling startApp()")
                val startAppMethod = midletClass.getDeclaredMethod("startApp")
                startAppMethod.isAccessible = true
                startAppMethod.invoke(midletInstance)

                startupComplete = true
                Log.d("EmulatorEngine", "startApp() returned successfully - game is running")

                while (isRunning && !isStopping) {
                    Thread.sleep(500)
                }
            } catch (e: InterruptedException) {
                Log.d("EmulatorEngine", "Game thread interrupted - stopping")
            } catch (e: Exception) {
                Log.e("EmulatorEngine",
                    "Exception in game thread: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                if (isRunning && !isStopping) {
                    isRunning = false
                    mainHandler.post { onFinished() }
                }
            }
        }.apply {
            name = "J2ME-GameThread"
            isDaemon = true
            start()
        }
    }

    @JvmStatic
    fun stopGame(onFinished: () -> Unit) {
        if (!isRunning || isStopping) return
        isRunning = false
        isStopping = true

        gameThread?.interrupt()

        Thread {
            try {
                currentMidlet?.let { midlet ->
                    try {
                        val destroyMethod =
                            midlet.javaClass.getDeclaredMethod("destroyApp", Boolean::class.java)
                        destroyMethod.isAccessible = true
                        destroyMethod.invoke(midlet, true)
                    } catch (e: Exception) {
                        Log.w("EmulatorEngine", "destroyApp() threw: ${e.message}")
                    }
                }
            } finally {
                currentMidlet = null
                startupComplete = false
                isStopping = false

                // Restore classloader elements
                ClassLoader.getSystemClassLoader()?.let { sys ->
                    restoreElements(sys, originalSystemElements)
                }
                context_classLoader_ref?.let { app ->
                    restoreElements(app, originalAppElements)
                }
                originalSystemElements = null
                originalAppElements = null

                // Clean up extracted resources
                resourceExtractDir?.deleteRecursively()
                resourceExtractDir = null

                try { zipFile?.close() } catch (ignored: Exception) {}
                zipFile = null
                classLoader = null
                gameThread = null
                mainHandler.post { onFinished() }
            }
        }.apply {
            name = "J2ME-StopThread"
            isDaemon = true
            start()
        }
    }

    @JvmStatic
    fun getResourceAsStream(path: String): InputStream? {
        val zip = zipFile ?: return null
        val cleanPath = path.trimStart('/')

        // 1. Exact match
        var entry = zip.getEntry(cleanPath)

        // 2. Basename-only match: game class may be in an obfuscated package so the JVM
        //    prepends the package path to relative resource names (e.g. "a/sprite.dat"
        //    instead of "sprite.dat"). Strip the package prefix and retry.
        if (entry == null && cleanPath.contains('/')) {
            val baseName = cleanPath.substringAfterLast('/')
            entry = zip.getEntry(baseName)
        }

        // 3. Case-insensitive fallback (some games use mixed-case paths)
        if (entry == null) {
            val lowerPath = cleanPath.lowercase()
            val lowerBase = lowerPath.substringAfterLast('/')
            val allEntries = zip.entries()
            while (allEntries.hasMoreElements()) {
                val e = allEntries.nextElement()
                val eName = e.name.lowercase()
                if (eName == lowerPath || eName == lowerBase) {
                    entry = e
                    break
                }
            }
        }

        if (entry == null) {
            Log.w("J2ME-Resource", "Resource not found in jar: $cleanPath")
            return null
        }
        Log.d("J2ME-Resource", "Resource loaded: $cleanPath → ${entry.name} (${entry.size} bytes)")
        return zip.getInputStream(entry)
    }

    /**
     * Returns the path to a DEX whose class files have been patched so that
     * every `Class.getResourceAsStream(name)` call is replaced with a call to
     * `J2meResourceProxy.getResourceAsStream(name)`.
     *
     * The patched DEX is cached next to the original as `classes_patched.dex`.
     * If patching or compilation fails, falls back to the original DEX path.
     */
    private fun ensurePatchedDex(game: GameModel, codeCacheDir: File): String {
        val origDex = File(game.dexPath)
        val patchedDex = File(origDex.parent, "classes_patched.dex")

        if (patchedDex.exists()) {
            Log.d("EmulatorEngine", "Using cached patched DEX: ${patchedDex.name}")
            return patchedDex.absolutePath
        }

        Log.d("EmulatorEngine", "Building patched DEX for ${game.name}...")
        val tempJar = File(codeCacheDir, "j2me_patching_${game.id}.jar")
        return try {
            if (!JarTransformer.transformJar(File(game.jarPath), tempJar)) {
                Log.w("EmulatorEngine", "JAR transform failed – using original DEX")
                return game.dexPath
            }

            if (!DexTranslator.translateJarToDex(tempJar, patchedDex)) {
                Log.w("EmulatorEngine", "Patched DEX compilation failed – using original DEX")
                return game.dexPath
            }

            patchedDex.setReadOnly()
            Log.d("EmulatorEngine", "Patched DEX ready: ${patchedDex.absolutePath}")
            patchedDex.absolutePath
        } catch (e: Exception) {
            Log.e("EmulatorEngine", "ensurePatchedDex error: ${e.message}", e)
            game.dexPath
        } finally {
            tempJar.delete()
        }
    }

    private fun parseManifest(jarFile: File): Map<String, String> {
        val props = mutableMapOf<String, String>()
        try {
            ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("META-INF/MANIFEST.MF") ?: return emptyMap()
                zip.getInputStream(entry).bufferedReader().useLines { lines ->
                    var currentKey = ""
                    var currentValue = ""
                    for (line in lines) {
                        if (line.startsWith(" ")) {
                            currentValue += line.substring(1)
                            if (currentKey.isNotEmpty()) props[currentKey] = currentValue
                        } else {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                currentKey = line.substring(0, colonIndex).trim()
                                currentValue = line.substring(colonIndex + 1).trim()
                                props[currentKey] = currentValue
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return props
    }

    // App classloader reference captured at startGame for use in stopGame cleanup.
    private var context_classLoader_ref: ClassLoader? = null
}
