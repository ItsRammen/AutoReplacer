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
            (snapshot.phase == AutoShowState.Phase.WAITING || snapshot.phase == AutoShowState.Phase.ENDED || snapshot.phase == AutoShowState.Phase.STANDBY)
    }

    /** Opens Android Auto's settings, where Developer settings (and Start head unit server) are reached. */
    fun openAndroidAutoSettings(context: Context): Boolean {
        val intent = android.content.Intent().setClassName(
            AutoShowReadiness.ANDROID_AUTO_PACKAGE,
            "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) {
            try {
                context.startActivity(android.content.Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
                    .setData(android.net.Uri.parse("package:" + AutoShowReadiness.ANDROID_AUTO_PACKAGE))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } catch (_: Exception) { false }
        }
    }

    fun reconnect(context: Context): Boolean {
        val accessory = attachedAccessory(context) ?: return false
        AutoShowService.start(context, accessory)
        return true
    }

    fun title(context: Context, snapshot: AutoShowState.Snapshot, cableIn: Boolean): String = context.getString(when (snapshot.phase) {
        AutoShowState.Phase.RUNNING -> if (snapshot.picture) R.string.as_title_showing else R.string.as_title_aa_starting
        AutoShowState.Phase.CONNECTING, AutoShowState.Phase.STARTING_ANDROID_AUTO -> R.string.as_title_connecting
        AutoShowState.Phase.ENDED, AutoShowState.Phase.STANDBY -> R.string.as_title_disconnected
        AutoShowState.Phase.WAITING -> if (cableIn) R.string.as_title_cable else R.string.as_title_waiting
    })

    fun subtitle(context: Context, snapshot: AutoShowState.Snapshot, cableIn: Boolean): String = when (snapshot.phase) {
        AutoShowState.Phase.WAITING ->
            context.getString(if (cableIn) R.string.as_sub_tap_reconnect else R.string.as_sub_plug_in)
        AutoShowState.Phase.STANDBY -> snapshot.headline
        AutoShowState.Phase.ENDED -> snapshot.headline + "\n" +
            context.getString(if (cableIn) R.string.as_ended_cable_in else R.string.as_ended_cable_out)
        else -> snapshot.headline
    }

    /** True when the server Android Auto needs is known not to be running, so the card should say so first. */
    fun serverWarning(snapshot: AutoShowState.Snapshot): Boolean =
        snapshot.phase != AutoShowState.Phase.RUNNING && AutoShowServerProbe.listening == false

    fun detail(context: Context, snapshot: AutoShowState.Snapshot): String = context.getString(R.string.as_detail,
        snapshot.touch.ifEmpty { context.getString(R.string.as_title_waiting) },
        snapshot.bluetooth.ifEmpty { context.getString(R.string.as_bt_not_checked) })

    private const val WARNING_COLOR = 0xFFFFB74D.toInt()

    /** Amber while the warning shows; the card's own colour comes back afterwards. */
    private fun paintWarning(view: TextView, warn: Boolean) {
        if (view.getTag(R.id.home_primary_kicker) == null) view.setTag(R.id.home_primary_kicker, view.currentTextColor)
        val original = view.getTag(R.id.home_primary_kicker) as Int
        view.setTextColor(if (warn) WARNING_COLOR else original)
    }

    /** Fills the home card. [onAction] gets a click on the main button when it offers Reconnect. */
    fun apply(root: View, snapshot: AutoShowState.Snapshot, onReconnect: () -> Unit, onSaveLog: () -> Unit) {
        val context = root.context
        val cableIn = attachedAccessory(context) != null
        val warning = serverWarning(snapshot)
        root.findViewById<TextView>(R.id.home_primary_kicker)?.apply {
            text = if (warning) context.getString(R.string.as_kicker_setup) else context.getString(R.string.as_kicker_normal, context.getString(R.string.app_name))
            paintWarning(this, warning)
        }
        root.findViewById<TextView>(R.id.home_primary_title)?.apply {
            text = if (warning) context.getString(R.string.as_warning_title) else title(context, snapshot, cableIn)
            maxLines = 2
            paintWarning(this, warning)
        }
        root.findViewById<TextView>(R.id.home_primary_subtitle)?.apply {
            text = if (warning) AutoShowServerProbe.steps(context) else subtitle(context, snapshot, cableIn); maxLines = 5
        }
        root.findViewById<TextView>(R.id.home_primary_detail)?.apply {
            visibility = if (snapshot.phase == AutoShowState.Phase.WAITING) View.GONE else View.VISIBLE
            text = detail(context, snapshot); maxLines = 3; ellipsize = null
        }
        root.findViewById<View>(R.id.home_primary_progress)?.visibility = View.GONE
        root.findViewById<View>(R.id.home_countdown)?.visibility = View.GONE
        val action = root.findViewById<TextView>(R.id.home_primary_action)
        val cancel = root.findViewById<TextView>(R.id.home_primary_cancel)
        val reconnect = canReconnect(context, snapshot)
        // The only button on the card is Reconnect, and only when it can do something.
        action?.apply {
            visibility = if (warning || reconnect) View.VISIBLE else View.GONE
            text = if (warning) context.getString(R.string.as_open_aa) else context.getString(R.string.as_reconnect)
            setOnClickListener { if (warning) openAndroidAutoSettings(context) else onReconnect() }
        }
        cancel?.visibility = View.GONE
        // Nothing from the original home screen that AutoReplacer has no use for: favorite apps, widgets, page dots.
        listOf(R.id.home_favorites_area, R.id.home_widgets_row, R.id.home_page_dots, R.id.apps_button)
            .forEach { root.findViewById<View>(it)?.visibility = View.GONE }
        addOverflow(root, onSaveLog)
    }

    private const val MORE_TAG = "autoshow_more"

    /** A small ⋮ in the card's top corner; the log lives behind it. */
    private fun addOverflow(root: View, onSaveLog: () -> Unit) {
        val card = root.findViewById<android.widget.FrameLayout>(R.id.home_primary_card) ?: return
        val more = card.findViewWithTag<TextView>(MORE_TAG) ?: TextView(root.context).apply {
            tag = MORE_TAG
            text = "\u22EE"
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            alpha = 0.8f
            gravity = android.view.Gravity.CENTER
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            contentDescription = root.context.getString(R.string.as_more)
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            if (root.context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
                setBackgroundResource(outValue.resourceId)
            }
            card.addView(this, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            ).apply { val m = (8 * resources.displayMetrics.density).toInt(); setMargins(m, m, m, m) })
        }
        more.setOnClickListener { anchor ->
            android.widget.PopupMenu(anchor.context, anchor).apply {
                menu.add(0, 1, 0, anchor.context.getString(R.string.as_save_log))
                menu.add(0, 3, 1, anchor.context.getString(R.string.as_calibrate_menu))
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onSaveLog()
                        3 -> anchor.context.startService(android.content.Intent(anchor.context, AutoShowService::class.java)
                            .setAction(AutoShowService.ACTION_CALIBRATE))
                    }
                    true
                }
            }.show()
        }
        more.bringToFront()
    }
}
