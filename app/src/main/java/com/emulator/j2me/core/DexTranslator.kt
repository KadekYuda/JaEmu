package com.emulator.j2me.core

import android.content.Context
import android.util.Log
import com.android.dx.command.dexer.Main
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

object DexTranslator {
    private const val TAG = "DexTranslator"

    fun translateJarToDex(jarFile: File, dexFile: File): Boolean {
        return try {
            val args = Main.Arguments().apply {
                outName = dexFile.absolutePath
                fileNames = arrayOf(jarFile.absolutePath)
                jarOutput = false
                keepClassesInJar = false
                verbose = false
                strictNameCheck = false
                emptyOk = true
            }
            val result = Main.run(args)
            result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a JAR containing Nokia API stub classes that need to be available
     * to games (e.g., com.nokia.mid.sound.Sound, com.nokia.mid.sound.SoundListener).
     * These classes are compiled into the app but must be added to the game's DEX
     * classpath for games that reference them.
     */
    fun createNokiaApiJar(context: Context, outputFile: File): Boolean {
        return try {
            JarOutputStream(FileOutputStream(outputFile)).use { jos ->
                // Load the compiled class files from the app's classpath
                val soundClass = loadClassBytes(context, "com.nokia.mid.sound.Sound")
                if (soundClass != null) {
                    addClassBytesToJar(jos, "com/nokia/mid/sound/Sound.class", soundClass)
                }
                
                val soundListenerClass = loadClassBytes(context, "com.nokia.mid.sound.SoundListener")
                if (soundListenerClass != null) {
                    addClassBytesToJar(jos, "com/nokia/mid/sound/SoundListener.class", soundListenerClass)
                }
            }
            Log.d(TAG, "Nokia API JAR created: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Nokia API JAR: ${e.message}", e)
            false
        }
    }

    private fun loadClassBytes(context: Context, className: String): ByteArray? {
        return try {
            val classLoader = context.classLoader
            val resourceName = className.replace('.', '/') + ".class"
            classLoader.getResourceAsStream(resourceName)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load class bytes for $className: ${e.message}")
            null
        }
    }

    private fun addClassBytesToJar(jos: JarOutputStream, entryName: String, bytes: ByteArray) {
        jos.putNextEntry(JarEntry(entryName))
        jos.write(bytes)
        jos.closeEntry()
    }

    /**
     * Merges multiple JAR files into a single JAR file.
     * Used to combine the game JAR with Nokia API stub JAR before DEX conversion.
     */
    fun mergeJars(inputJars: List<File>, outputFile: File): Boolean {
        return try {
            JarOutputStream(FileOutputStream(outputFile)).use { jos ->
                val addedEntries = mutableSetOf<String>()
                for (jarFile in inputJars) {
                    java.util.zip.ZipFile(jarFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory && !addedEntries.contains(entry.name)) {
                                jos.putNextEntry(JarEntry(entry.name))
                                zip.getInputStream(entry).copyTo(jos)
                                jos.closeEntry()
                                addedEntries.add(entry.name)
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Merged ${inputJars.size} JARs into ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge JARs: ${e.message}", e)
            false
        }
    }
}
