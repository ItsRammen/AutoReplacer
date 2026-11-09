package com.andrerinas.openheadunit.autoshow

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

    @Synchronized fun ensureStarted() {
        if (server == null) server = CarTouchServer(this).also { it.start() }
    }

    fun attach(listener: CarTouchServer.Listener) { target = listener; ensureStarted() }

    fun detach(listener: CarTouchServer.Listener) { if (target === listener) target = null }

    override fun log(text: String) {
        val t = target
        if (t != null) t.log(text) else AppLog.i("AutoShow: %s", text)
        when {
            text.startsWith("TS8051_LISTENING") -> AutoShowState.update { if (it.touch.startsWith("Unavailable") || it.touch.startsWith("Blocked")) it.copy(touch = "Waiting for the car's first touch") else it }
            text.startsWith("TS8051_BIND_FAILED") -> AutoShowState.update { it.copy(touch = "Blocked by the car's old touch helper; trying to clear it") }
        }
    }

    override fun carSize(phoneWidth: Int, phoneHeight: Int, carWidth: Int, carHeight: Int) { target?.carSize(phoneWidth, phoneHeight, carWidth, carHeight) }

    override fun touch(x: Int, y: Int, b: Int, carWidth: Int, carHeight: Int) { target?.touch(x, y, b, carWidth, carHeight) }
}
