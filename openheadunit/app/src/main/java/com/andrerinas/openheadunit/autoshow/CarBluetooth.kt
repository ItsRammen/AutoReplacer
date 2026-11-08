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

    fun start(carAddress: String?) {
        address = carAddress
        val adapter = adapter()
        val valid = BluetoothPlan.isValidAddress(carAddress)
        val permitted = hasPermission()
        val bonded = valid && adapter != null && permitted && try {
            adapter.getRemoteDevice(carAddress).bondState == BluetoothDevice.BOND_BONDED
        } catch (_: Exception) { false }
        val action = BluetoothPlan.decide(valid, permitted, adapter != null, adapter?.isEnabled == true, bonded)
        log("CAR_BLUETOOTH car=${BluetoothPlan.mask(carAddress ?: "")} plan=$action permission=$permitted")
        status(BluetoothPlan.describe(action))
        when (action) {
            BluetoothPlan.Action.PAIR -> pair(adapter!!, carAddress!!)
            BluetoothPlan.Action.CHECK_AUDIO -> checkAudio(adapter!!, carAddress!!, 0)
            else -> {}
        }
    }

    private fun pair(adapter: BluetoothAdapter, car: String) {
        watchBond(car)
        try {
            val started = adapter.getRemoteDevice(car).createBond()
            log("CAR_BLUETOOTH_PAIRING started=$started")
            if (!started) status("Could not start pairing; pair the car in the phone's Bluetooth settings")
        } catch (e: Exception) {
            log("CAR_BLUETOOTH_PAIRING_FAILED ${e.javaClass.simpleName}: ${e.message}")
            status("Could not start pairing; pair the car in the phone's Bluetooth settings")
        }
    }

    private fun watchBond(car: String) {
        if (receiver != null) return
        val bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device == null || !device.address.equals(car, ignoreCase = true)) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> {
                        log("CAR_BLUETOOTH_BONDED")
                        status(BluetoothPlan.describe(BluetoothPlan.Action.CHECK_AUDIO))
                        adapter()?.let { checkAudio(it, car, 0) }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        log("CAR_BLUETOOTH_NOT_PAIRED")
                        status("Pairing with the car was not completed; pair it in the phone's Bluetooth settings")
                    }
                }
            }
        }
        receiver = bondReceiver
        ContextCompat.registerReceiver(context, bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
    }

    /** Looks for the car among connected media (A2DP) and call (headset) devices, retrying while the car sets up. */
    private fun checkAudio(adapter: BluetoothAdapter, car: String, attempt: Int) {
        if (stopped) return
        var media: Boolean? = null
        var calls: Boolean? = null
        fun finish() {
            val m = media ?: return
            val h = calls ?: return
            if (stopped) return
            status(BluetoothPlan.audioStatus(m, h))
            log("CAR_BLUETOOTH_AUDIO media=$m calls=$h attempt=$attempt")
            if (!(m && h) && attempt < 4) main.postDelayed({ checkAudio(adapter, car, attempt + 1) }, 5000)
        }
        fun probe(profile: Int, result: (Boolean) -> Unit) {
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
        probe(BluetoothProfile.A2DP) { media = it; finish() }
        probe(BluetoothProfile.HEADSET) { calls = it; finish() }
    }

    fun stop() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }
}
