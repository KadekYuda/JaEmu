package com.emulator.j2me.core

import android.util.Log
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

/**
 * Transforms a J2ME game JAR so that every call to
 * `Class.getResourceAsStream(String)` is redirected to the static
 * method `J2meResourceProxy.getResourceAsStream(String)`.
 *
 * Background:
 *   The game calls `"a".getClass().getResourceAsStream(name)`.
 *   On Android, `String.class.getClassLoader()` returns the
 *   BootClassLoader, which has no knowledge of the game JAR's assets.
 *   Patching each call-site in the class files before DEX compilation
 *   is the only reliable way to intercept these lookups.
 *
 * Bytecode transformation:
 *   Before:  INVOKEVIRTUAL java/lang/Class.getResourceAsStream (String)InputStream
 *            (stack: [..., classObj, name])
 *   After:   SWAP   (stack: [..., name, classObj])
 *            POP    (stack: [..., name])
 *            INVOKESTATIC com/emulator/j2me/core/J2meResourceProxy.getResourceAsStream (String)InputStream
 *            (stack: [..., InputStream])
 *   Net stack effect is identical, so max-stack and frame info remain valid.
 */
object JarTransformer {

    private const val TAG = "JarTransformer"

    private const val TARGET_OWNER = "java/lang/Class"
    private const val TARGET_NAME  = "getResourceAsStream"
    private const val TARGET_DESC  = "(Ljava/lang/String;)Ljava/io/InputStream;"
    private const val PROXY_OWNER  = "com/emulator/j2me/core/J2meResourceProxy"

    /**
     * Reads every entry from [inputJar], transforms `.class` files,
     * and writes the result to [outputJar].
     *
     * @return `true` on success, `false` if any unrecoverable error occurred.
     */
    fun transformJar(inputJar: File, outputJar: File): Boolean {
        var patchCount = 0
        return try {
            JarOutputStream(outputJar.outputStream()).use { jos ->
                ZipFile(inputJar).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        jos.putNextEntry(JarEntry(entry.name))
                        if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            val original = zip.getInputStream(entry).readBytes()
                            val (transformed, patched) = transformClass(original)
                            jos.write(transformed)
                            if (patched) patchCount++
                        } else {
                            zip.getInputStream(entry).copyTo(jos)
                        }
                        jos.closeEntry()
                    }
                }
            }
            Log.d(TAG, "Transform complete: $patchCount class(es) patched in ${inputJar.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transform failed: ${e.message}", e)
            false
        }
    }

    /**
     * Applies the ASM transformation to a single class file.
     *
     * @return Pair(transformedBytes, whetherAnyCallWasPatched)
     */
    private fun transformClass(classBytes: ByteArray): Pair<ByteArray, Boolean> {
        var patched = false
        val reader = ClassReader(classBytes)
        val writer = ClassWriter(reader, 0)

        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(
                        opcode: Int, owner: String, name: String,
                        descriptor: String, isInterface: Boolean
                    ) {
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && owner == TARGET_OWNER
                            && name == TARGET_NAME
                            && descriptor == TARGET_DESC
                        ) {
                            // Stack before: [..., classObj, name]
                            mv.visitInsn(Opcodes.SWAP)        // [..., name, classObj]
                            mv.visitInsn(Opcodes.POP)         // [..., name]
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC, PROXY_OWNER,
                                TARGET_NAME, TARGET_DESC, false
                            )                                 // [..., InputStream]
                            patched = true
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }
        }, 0)

        return Pair(writer.toByteArray(), patched)
    }
}
