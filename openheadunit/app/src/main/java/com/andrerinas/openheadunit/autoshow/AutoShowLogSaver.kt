package com.andrerinas.openheadunit.autoshow

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast

/** Saves this app's log to Downloads so it can be shared without a computer. */
object AutoShowLogSaver {
    fun save(context: Context, log: (String) -> Unit = {}) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val name = "AutoReplacer-log-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt"
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val body = process.inputStream.bufferedReader().use { it.readText() }
                val header = "AutoReplacer log ${java.util.Date()}\n" +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    AutoShowReadiness.summary(AutoShowText.english(app)) + "\n\n"
                val events = AutoShowEventLog.read(app)
                val text = header + body + (if (events.isNotEmpty()) "\n\n===== AutoReplacer event log (kept by the app; the system log above only holds the last few minutes) =====\n" + events else "")
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException(app.getString(com.andrerinas.openheadunit.R.string.as_log_create_failed))
                    app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                } else {
                    val dir = app.getExternalFilesDir(null) ?: app.filesDir
                    java.io.File(dir, name).writeText(text)
                }
                log("AUTOSHOW_LOG_SAVED $name bytes=${text.length}")
                main.post { Toast.makeText(app, app.getString(com.andrerinas.openheadunit.R.string.as_log_saved, name), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                log("AUTOSHOW_LOG_SAVE_FAILED ${e.javaClass.simpleName}: ${e.message}")
                main.post { Toast.makeText(app, app.getString(com.andrerinas.openheadunit.R.string.as_log_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
