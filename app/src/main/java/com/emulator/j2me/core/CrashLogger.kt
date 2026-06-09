package com.emulator.j2me.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global uncaught-exception handler that writes a detailed crash
 * report to the public Downloads folder so it is easily accessible for
 * debugging without ADB.
 *
 * On Android 10+ (API 29+): uses MediaStore.Downloads — no permission needed.
 * On Android 6-9: writes directly to the Downloads directory using
 *   Environment.getExternalStoragePublicDirectory; requires
 *   WRITE_EXTERNAL_STORAGE (declared in AndroidManifest with maxSdkVersion=28).
 * Fallback: internal files dir when external storage is unavailable.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_DIR_NAME = "J2ME_Emulator_Logs"

    fun install(context: Context, gameName: String) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val path = save(appCtx, gameName, thread, throwable)
                Log.e(TAG, "Crash saved → $path", throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Could not save crash log: ${e.message}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Immediately writes [throwable] to Downloads and returns the file path.
     * Can also be called directly (e.g. from a caught exception) to save a
     * non-fatal issue.
     */
    fun save(context: Context, gameName: String, thread: Thread, throwable: Throwable): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = gameName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        val fileName = "J2ME_crash_${safe}_$ts.txt"
        val content = buildReport(gameName, thread, throwable, ts)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, fileName, content)
        } else {
            saveViaFile(context, fileName, content)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildReport(
        gameName: String, thread: Thread, throwable: Throwable, ts: String
    ): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("========================================")
            appendLine(" J2ME EMULATOR — CRASH REPORT")
            appendLine("========================================")
            appendLine("Time      : $ts")
            appendLine("Game      : $gameName")
            appendLine("Thread    : ${thread.name} (id=${thread.id})")
            appendLine("Android   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Exception : ${throwable.javaClass.name}")
            appendLine("Message   : ${throwable.message}")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(sw.toString())
        }
    }

    private fun saveViaMediaStore(context: Context, fileName: String, content: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/$LOG_DIR_NAME")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return saveViaFile(context, fileName, content) // fallback

        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return "Downloads/$LOG_DIR_NAME/$fileName"
    }

    private fun saveViaFile(context: Context, fileName: String, content: String): String {
        // Try public Downloads first
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadsDir, LOG_DIR_NAME).also { it.mkdirs() }
        val target = File(logDir, fileName)

        return try {
            target.writeText(content)
            target.absolutePath
        } catch (e: Exception) {
            // Final fallback: app-internal files dir (always writable)
            val internalDir = File(context.filesDir, LOG_DIR_NAME).also { it.mkdirs() }
            val internalFile = File(internalDir, fileName)
            internalFile.writeText(content)
            internalFile.absolutePath
        }
    }
}
