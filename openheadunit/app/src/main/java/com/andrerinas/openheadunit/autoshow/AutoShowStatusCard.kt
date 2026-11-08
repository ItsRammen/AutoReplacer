package com.andrerinas.openheadunit.autoshow

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.view.View
import android.widget.TextView
import com.andrerinas.openheadunit.R

/**
 * The main button on the home screen, painted for Auto+ Show mode: it shows what the car session is
 * doing instead of offering to start a connection, because the session starts by itself when the
 * cable goes in.
 */
object AutoShowStatusCard {

    /** The Garmin accessory the system still lists, if the cable is in. */
    fun attachedAccessory(context: Context): UsbAccessory? {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return usb.accessoryList?.firstOrNull { it.manufacturer?.contains("Garmin", ignoreCase = true) == true }
            ?: usb.accessoryList?.firstOrNull()
    }

    /** True when the cable is in, USB access is granted and no session is running: the user can reconnect. */
    fun canReconnect(context: Context, snapshot: AutoShowState.Snapshot): Boolean {
        val accessory = attachedAccessory(context) ?: return false
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usb.hasPermission(accessory) &&
            (snapshot.phase == AutoShowState.Phase.WAITING || snapshot.phase == AutoShowState.Phase.ENDED)
    }

    fun reconnect(context: Context): Boolean {
        val accessory = attachedAccessory(context) ?: return false
        AutoShowService.start(context, accessory)
        return true
    }

    fun title(snapshot: AutoShowState.Snapshot, cableIn: Boolean): String = when (snapshot.phase) {
        AutoShowState.Phase.RUNNING -> if (snapshot.picture) "Showing on the car" else "Android Auto is starting"
        AutoShowState.Phase.CONNECTING, AutoShowState.Phase.STARTING_ANDROID_AUTO -> "Connecting"
        AutoShowState.Phase.ENDED -> "Car disconnected"
        AutoShowState.Phase.WAITING -> if (cableIn) "Cable detected" else "Waiting for the car"
    }

    fun subtitle(snapshot: AutoShowState.Snapshot, cableIn: Boolean): String = when (snapshot.phase) {
        AutoShowState.Phase.WAITING ->
            if (cableIn) "Tap Reconnect to start the car session" else "Plug the phone into the Auto+ Show cable. Nothing needs to be started here."
        AutoShowState.Phase.ENDED -> snapshot.headline + if (cableIn) "\nTap Reconnect, or unplug and plug the cable back in." else "\nPlug the cable back in to start again."
        else -> snapshot.headline
    }

    fun detail(snapshot: AutoShowState.Snapshot): String =
        "Touch: ${snapshot.touch}\nBluetooth: ${snapshot.bluetooth}"

    /** Fills the home card. [onAction] gets a click on the main button when it offers Reconnect. */
    fun apply(root: View, snapshot: AutoShowState.Snapshot, onReconnect: () -> Unit, onSaveLog: () -> Unit) {
        val context = root.context
        val cableIn = attachedAccessory(context) != null
        root.findViewById<TextView>(R.id.home_primary_kicker)?.text = "AutoReplacer · Auto+ Show"
        root.findViewById<TextView>(R.id.home_primary_title)?.apply { text = title(snapshot, cableIn); maxLines = 2 }
        root.findViewById<TextView>(R.id.home_primary_subtitle)?.apply {
            text = subtitle(snapshot, cableIn); maxLines = 4
        }
        root.findViewById<TextView>(R.id.home_primary_detail)?.apply {
            visibility = if (snapshot.phase == AutoShowState.Phase.WAITING) View.GONE else View.VISIBLE
            text = detail(snapshot); maxLines = 3; ellipsize = null
        }
        root.findViewById<View>(R.id.home_primary_progress)?.visibility = View.GONE
        root.findViewById<View>(R.id.home_countdown)?.visibility = View.GONE
        val action = root.findViewById<TextView>(R.id.home_primary_action)
        val cancel = root.findViewById<TextView>(R.id.home_primary_cancel)
        val reconnect = canReconnect(context, snapshot)
        action?.apply {
            visibility = View.VISIBLE
            text = if (reconnect) "Reconnect" else "Save log"
            setOnClickListener { if (reconnect) onReconnect() else onSaveLog() }
        }
        cancel?.apply {
            visibility = if (reconnect) View.VISIBLE else View.GONE
            text = "Save log"
            setOnClickListener { onSaveLog() }
        }
    }
}
