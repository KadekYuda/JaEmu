package com.emulator.j2me.data
 
import android.content.Context
import java.io.File
 
/** Resolves on-disk locations for per-game thumbnails. */
object Thumbnails {
    private const val DIR_NAME = "thumbnails"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Auto-captured gameplay thumbnail (overwritten on each play). */
    fun file(context: Context, gameId: String): File = File(dir(context), "$gameId.png")

    /**
     * User-picked custom thumbnail. Takes precedence over the auto-captured one
     * and is never overwritten by gameplay capture, so it persists permanently.
     */
    fun customFile(context: Context, gameId: String): File = File(dir(context), "${gameId}_custom.png")

    /** Whether the user has set a custom thumbnail for this game. */
    fun hasCustom(context: Context, gameId: String): Boolean = customFile(context, gameId).exists()

    /** Best thumbnail to display: custom > auto-captured > null (caller falls back to the JAR icon). */
    fun display(context: Context, gameId: String): File? {
        customFile(context, gameId).let { if (it.exists()) return it }
        file(context, gameId).let { if (it.exists()) return it }
        return null
    }

    /** Remove both custom and auto thumbnails so the preview reverts to the original JAR icon. */
    fun reset(context: Context, gameId: String) {
        customFile(context, gameId).delete()
        file(context, gameId).delete()
    }
}
