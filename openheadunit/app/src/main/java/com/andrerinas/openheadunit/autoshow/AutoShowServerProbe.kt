package com.andrerinas.openheadunit.autoshow

import android.os.Handler
import android.os.Looper
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Android Auto 17.4 and later accepts connections from a head unit only after "Start head unit
 * server" has been tapped in its developer settings. This asks whether anything is listening on
 * the server's port (127.0.0.1:5277) and closes again straight away. It is not run during a session.
 */
object AutoShowServerProbe {
    const val HINT = "Android Auto's head unit server is not running. On the phone open Android Auto, " +
        "then Developer settings, then Start head unit server. Start it before plugging in."

    /** True/false after a check, null until the first one finishes. */
    @Volatile var listening: Boolean? = null
        private set

    fun refresh(onDone: (Boolean) -> Unit = {}) {
        Thread {
            val up = try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", 5277), 400); true }
            } catch (_: Exception) { false }
            listening = up
            Handler(Looper.getMainLooper()).post { onDone(up) }
        }.start()
    }
}
