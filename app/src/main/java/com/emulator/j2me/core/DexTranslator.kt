package com.emulator.j2me.core

import com.android.dx.command.dexer.Main
import java.io.File

object DexTranslator {
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
}
