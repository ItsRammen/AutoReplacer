package com.andrerinas.openheadunit.autoshow

import android.content.Context
import android.provider.Settings
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
            Check("Android Auto installed", androidAuto != null,
                "Install or update Android Auto from the Play Store."),
            Check("Developer options on", global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                "Settings > About phone > tap Build number seven times."),
            Check("USB debugging on", global(context, Settings.Global.ADB_ENABLED),
                "Settings > Developer options > USB debugging. Car touch needs it. Turn it on, then unplug and replug the cable."),
            Check("Notifications allowed", NotificationManagerCompat.from(context).areNotificationsEnabled(),
                "Allow notifications so the car connection status is visible.")
        )
    }

    /** One line per check: a tick or cross, and the fix when it is not met. */
    fun summary(context: Context): String = checks(context).joinToString("\n") {
        if (it.ok) "✓ ${it.title}" else "✗ ${it.title}: ${it.hint}"
    }

    /** What to tell the user right now if car touch cannot work yet; null when the basics are on. */
    fun carTouchBlocker(context: Context): String? = when {
        !global(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) -> "Turn on Developer options for car touch"
        !global(context, Settings.Global.ADB_ENABLED) -> "Turn on USB debugging for car touch, then replug the cable"
        else -> null
    }
}
