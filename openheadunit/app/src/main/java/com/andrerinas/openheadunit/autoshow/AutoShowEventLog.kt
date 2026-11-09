package com.andrerinas.openheadunit.autoshow

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's own event log. The system log keeps only the last few minutes (a long car session scrolls
 * the touch lines out of it), so every AutoShow line is also written here, to a file that is capped
 * and kept across sessions, and appended to the log the user saves.
 */
object AutoShowEventLog {
    private const val FILE = "autoshow-events.log"
    private const val MAX_BYTES = 1_500_000L
    private const val KEEP_BYTES = 600_000L
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var stream: FileOutputStream? = null
    private var file: File? = null

    @Synchronized fun append(context: Context, text: String) {
        try {
            val f = file ?: File(context.applicationContext.filesDir, FILE).also { file = it }
            if (f.length() > MAX_BYTES) trim(f)
            val out = stream ?: FileOutputStream(f, true).also { stream = it }
            out.write((clock.format(Date()) + " " + text + "\n").toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun trim(f: File) {
        try {
            stream?.close(); stream = null
            val bytes = f.readBytes()
            val tail = bytes.copyOfRange((bytes.size - KEEP_BYTES.toInt()).coerceAtLeast(0), bytes.size)
            f.writeBytes(tail)
        } catch (_: Exception) {}
    }

    /** The saved events, newest part last, for the log the user shares. */
    @Synchronized fun read(context: Context): String = try {
        val f = file ?: File(context.applicationContext.filesDir, FILE)
        if (!f.exists()) "" else String(f.readBytes())
    } catch (_: Exception) { "" }
}
