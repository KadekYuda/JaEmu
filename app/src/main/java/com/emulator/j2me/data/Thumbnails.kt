package com.emulator.j2me.data

import android.content.Context
import java.io.File

/** Resolves on-disk locations for per-game thumbnails captured from the first rendered frame. */
object Thumbnails {
    private const val DIR_NAME = "thumbnails"

    fun file(context: Context, gameId: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        return File(dir, "$gameId.png")
    }
}
