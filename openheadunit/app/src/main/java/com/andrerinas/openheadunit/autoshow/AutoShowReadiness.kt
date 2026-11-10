package com.andrerinas.openheadunit.autoshow

import android.content.Context
import android.provider.Settings
import com.andrerinas.openheadunit.R
import androidx.core.app.NotificationManagerCompat

/**
 * Phone settings Auto+ Show mode depends on, in plain words. Only things an app may read are
 * checked: the car's ADB key and the car's Bluetooth pairing are shown by what happens on the cable.
 */
object AutoShowReadiness {
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

    data class Check(val title: String, val ok: Boolean, val hint: String)

    private fun global(context: Context, key: String): Boolean = try {
        Settings.Global.getInt(context.contentResolver, key, 0) == 1
    } catch (_: Exception) { false }

    fun checks(context: Context): List<Check> {
        val androidAuto = try {
            context.packageManager.getPackageInfo(ANDROID_AUTO_PACKAGE, 0).versionName
        } catch (_: Exception) { null }
        return listOf(
            Check(context.getString(R.string.as_check_aa), androidAuto != null, context.getString(R.string.as_check_aa_hint)),
            Check(context.getString(R.string.as_check_dev), global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                context.getString(R.string.as_check_dev_hint)),
            Check(context.getString(R.string.as_check_usb), global(context, Settings.Global.ADB_ENABLED),
                context.getString(R.string.as_check_usb_hint)),
            Check(context.getString(R.string.as_check_notif), NotificationManagerCompat.from(context).areNotificationsEnabled(),
                context.getString(R.string.as_check_notif_hint))
        )
    }

    /** One line per check: a tick or cross, and the fix when it is not met. */
    fun summary(context: Context): String = checks(context).joinToString("\n") {
        if (it.ok) context.getString(R.string.as_check_ok, it.title) else context.getString(R.string.as_check_bad, it.title, it.hint)
    }

    /** What the phone still needs before the car can set up touch. */
    enum class Setup { OK, DEVELOPER_OPTIONS, USB_DEBUGGING }

    fun missing(context: Context): Setup = when {
        !global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) -> Setup.DEVELOPER_OPTIONS
        !global(context, Settings.Global.ADB_ENABLED) -> Setup.USB_DEBUGGING
        else -> Setup.OK
    }

    /** Opens the Settings screen where the missing item is turned on: About phone (to reveal Developer options) or Developer options. */
    fun openSettings(context: Context, setup: Setup): Boolean {
        val action = when (setup) {
            Setup.DEVELOPER_OPTIONS -> Settings.ACTION_DEVICE_INFO_SETTINGS
            Setup.USB_DEBUGGING -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            Setup.OK -> return false
        }
        for (candidate in listOf(action, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(android.content.Intent(candidate).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /** What to tell the user right now if car touch cannot work yet; null when the basics are on. */
    fun carTouchBlocker(context: Context): String? = when {
        !global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) -> context.getString(R.string.as_blocker_dev)
        !global(context, Settings.Global.ADB_ENABLED) -> context.getString(R.string.as_blocker_usb)
        else -> null
    }
}
