package com.andrerinas.openheadunit.autoshow

import android.content.Context
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Owns the port-8051 server for the life of the process. The car starts its own touch helper a few
 * seconds after the cable goes in; whoever holds the port then keeps it. Starting the server when the
 * app process starts (not when the car session starts) means it is already listening when the helper
 * tries, and a session that ends does not hand the port back to a helper started by the next plug-in.
 */
object AutoShowTouchHub : CarTouchServer.Listener {
    private var server: CarTouchServer? = null
    @Volatile private var target: CarTouchServer.Listener? = null

    @Volatile private var appContext: Context? = null

    private fun str(id: Int): String = appContext?.getString(id) ?: ""

    @Synchronized fun ensureStarted(context: Context? = null) {
        if (context != null) appContext = context.applicationContext
        if (server == null) server = CarTouchServer(this).also { it.start() }
    }

    /** Whether the car's forwarded touch connection is open right now. */
    fun carConnected(): Boolean = server?.isCarConnected() == true

    fun attach(listener: CarTouchServer.Listener) { target = listener; ensureStarted() }

    fun detach(listener: CarTouchServer.Listener) { if (target === listener) target = null }

    override fun log(text: String) {
        val t = target
        if (t != null) t.log(text) else AppLog.i("AutoShow: %s", text)
        when {
            text.startsWith("TS8051_LISTENING") -> AutoShowState.update {
                if (it.touchState == AutoShowState.TouchState.BLOCKED) it.copy(touch = str(R.string.as_touch_waiting_first), touchState = AutoShowState.TouchState.WAITING) else it
            }
            text.startsWith("TS8051_BIND_FAILED") -> AutoShowState.update {
                it.copy(touch = str(R.string.as_touch_blocked), touchState = AutoShowState.TouchState.BLOCKED)
            }
        }
    }

    override fun carSize(phoneWidth: Int, phoneHeight: Int, carWidth: Int, carHeight: Int) { target?.carSize(phoneWidth, phoneHeight, carWidth, carHeight) }

    override fun touch(x: Int, y: Int, b: Int, carWidth: Int, carHeight: Int) { target?.touch(x, y, b, carWidth, carHeight) }
}
