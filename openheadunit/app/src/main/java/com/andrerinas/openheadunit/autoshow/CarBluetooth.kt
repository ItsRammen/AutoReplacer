package com.andrerinas.openheadunit.autoshow

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Makes sure the phone is paired with the car's Bluetooth (so media and navigation sound can reach the
 * car's speakers) and reports whether the car has connected its audio. Uses public APIs only: it can
 * start pairing, but the car connects the audio profiles itself once paired. Never enables Bluetooth.
 */
class CarBluetooth(
    private val context: Context,
    private val log: (String) -> Unit,
    private val status: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var address: String? = null
    private var receiver: BroadcastReceiver? = null
    private var stopped = false

    private fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun adapter(): BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var startedAt = 0L
    private var connectAttempts = 0
    private var lastMedia: Boolean? = null
    private var lastCalls: Boolean? = null
    private val periodic = Runnable { check("timer") }

    fun start(carAddress: String?) {
        address = carAddress
        startedAt = android.os.SystemClock.elapsedRealtime()
        val adapter = adapter()
        val valid = BluetoothPlan.isValidAddress(carAddress)
        val permitted = hasPermission()
        val bonded = valid && adapter != null && permitted && try {
            adapter.getRemoteDevice(carAddress).bondState == BluetoothDevice.BOND_BONDED
        } catch (_: Exception) { false }
        val action = BluetoothPlan.decide(valid, permitted, adapter != null, adapter?.isEnabled == true, bonded)
        log("CAR_BLUETOOTH car=${BluetoothPlan.mask(carAddress ?: "")} plan=$action permission=$permitted")
        status(BluetoothPlan.describe(action))
        // Follow the car's Bluetooth for the whole session, so the status stays right when it changes
        // (the car connects late, or the user connects it by hand in the phone's settings).
        if (valid && permitted && adapter != null) watchEvents(carAddress!!)
        when (action) {
            BluetoothPlan.Action.PAIR -> pair(adapter!!, carAddress!!)
            BluetoothPlan.Action.CHECK_AUDIO -> check("start")
            else -> {}
        }
    }

    private fun pair(adapter: BluetoothAdapter, car: String) {
        try {
            val started = adapter.getRemoteDevice(car).createBond()
            log("CAR_BLUETOOTH_PAIRING started=$started")
            if (!started) status("Could not start pairing; pair the car in the phone's Bluetooth settings")
        } catch (e: Exception) {
            log("CAR_BLUETOOTH_PAIRING_FAILED ${e.javaClass.simpleName}: ${e.message}")
            status("Could not start pairing; pair the car in the phone's Bluetooth settings")
        }
    }

    private fun watchEvents(car: String) {
        if (receiver != null) return
        val events = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device == null || !device.address.equals(car, ignoreCase = true)) return
                if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> { log("CAR_BLUETOOTH_BONDED"); status(BluetoothPlan.describe(BluetoothPlan.Action.CHECK_AUDIO)) }
                        BluetoothDevice.BOND_NONE -> {
                            log("CAR_BLUETOOTH_NOT_PAIRED")
                            status("Pairing with the car was not completed; pair it in the phone's Bluetooth settings")
                            return
                        }
                        else -> return
                    }
                }
                log("CAR_BLUETOOTH_EVENT ${intent.action?.substringAfterLast('.')}")
                // Let the profile state settle, then look again; several events arrive together.
                main.removeCallbacks(settle)
                main.postDelayed(settle, 700L)
            }
        }
        receiver = events
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, events, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private val settle = Runnable { check("event") }

    /** Asks one profile service whether the car is connected. */
    private fun probe(adapter: BluetoothAdapter, car: String, profile: Int, result: (Boolean) -> Unit) {
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                val connected = try { proxy.connectedDevices.any { it.address.equals(car, ignoreCase = true) } } catch (_: Exception) { false }
                try { adapter.closeProfileProxy(p, proxy) } catch (_: Exception) {}
                main.post { result(connected) }
            }
            override fun onServiceDisconnected(p: Int) {}
        }
        val ok = try { adapter.getProfileProxy(context, listener, profile) } catch (_: Exception) { false }
        if (!ok) main.post { result(false) }
    }

    /**
     * Looks for the car among connected media (A2DP) and call (headset) devices and reports it. When the
     * car is paired but an audio profile is not connected, asks Android to connect it, as the Auto+ Show app does.
     */
    private fun check(reason: String) {
        if (stopped) return
        val adapter = adapter() ?: return
        val car = address ?: return
        var media: Boolean? = null
        var calls: Boolean? = null
        fun finish() {
            val m = media ?: return
            val h = calls ?: return
            if (stopped) return
            status(BluetoothPlan.audioStatus(m, h))
            if (m != lastMedia || h != lastCalls) {
                log("CAR_BLUETOOTH_AUDIO media=$m calls=$h reason=$reason")
                lastMedia = m; lastCalls = h
            }
            // The car often connects by itself in the first seconds; only then does the phone step in.
            if (!(m && h) && connectAttempts < 3 && android.os.SystemClock.elapsedRealtime() - startedAt > 6000) {
                connectAttempts++
                if (!m) connect(adapter, car, BluetoothProfile.A2DP, "A2DP")
                if (!h) connect(adapter, car, BluetoothProfile.HEADSET, "HEADSET")
            }
            main.removeCallbacks(periodic)
            main.postDelayed(periodic, if (m && h) 60000L else 15000L)
        }
        probe(adapter, car, BluetoothProfile.A2DP) { media = it; finish() }
        probe(adapter, car, BluetoothProfile.HEADSET) { calls = it; finish() }
    }

    /**
     * Connects one audio profile to the car. Android keeps this call for system apps (it needs
     * BLUETOOTH_PRIVILEGED), so on most phones it is refused; the result is logged either way and the
     * status keeps telling the user to connect it in the phone's Bluetooth settings.
     */
    private fun connect(adapter: BluetoothAdapter, car: String, profile: Int, name: String) {
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                try {
                    val device = adapter.getRemoteDevice(car)
                    val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    val result = method.invoke(proxy, device)
                    log("CAR_BLUETOOTH_CONNECT $name asked result=$result")
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    log("CAR_BLUETOOTH_CONNECT $name refused: ${e.targetException?.javaClass?.simpleName}: ${e.targetException?.message}")
                } catch (e: Exception) {
                    log("CAR_BLUETOOTH_CONNECT $name unavailable: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    try { adapter.closeProfileProxy(p, proxy) } catch (_: Exception) {}
                }
            }
            override fun onServiceDisconnected(p: Int) {}
        }
        try { adapter.getProfileProxy(context, listener, profile) } catch (e: Exception) { log("CAR_BLUETOOTH_CONNECT $name failed: ${e.message}") }
    }

    fun stop() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }
}
