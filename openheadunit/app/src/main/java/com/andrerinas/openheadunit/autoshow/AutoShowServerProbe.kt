package com.andrerinas.openheadunit.autoshow

import android.os.Handler
import android.os.Looper

/**
 * Android Auto 17.4 and later accepts connections from a head unit only after "Start head unit
 * server" has been tapped in its developer settings. This asks whether anything is listening on
 * the server's port (127.0.0.1:5277) by trying to bind it, which does not touch the server. It is not run during a session.
 */
object AutoShowServerProbe {
    fun headline(context: android.content.Context): String = context.getString(com.andrerinas.openheadunit.R.string.as_server_down_headline)
    fun steps(context: android.content.Context): String = context.getString(com.andrerinas.openheadunit.R.string.as_server_down_steps)
    fun hint(context: android.content.Context): String = AutoShowText.join(context, headline(context), steps(context))

    /** True/false after a check, null until the first one finishes. */
    @Volatile var listening: Boolean? = null
        private set

    fun refresh(onDone: (Boolean) -> Unit = {}) {
        Thread {
            // Never connect: the server takes one connection per start and a probe would use it up.
            val up = PortListening.isListening(5277)
            listening = up
            Handler(Looper.getMainLooper()).post { onDone(up) }
        }.start()
    }
}
