package com.andrerinas.openheadunit.autoshow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App

/**
 * Android Auto's day/night flag comes from this app (the car does not send one), and in Auto mode it is worked
 * out from sunrise and sunset at a stored position. Before the first real GPS fix the app has none, so at the
 * start of a car session this reads the phone's last known position once and stores it. The position itself is
 * never written to the log; only where it came from and how old it is.
 */
object AutoShowNightLocation {
    fun refresh(context: Context, log: (String) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                val settings = App.provide(app).settings
                val permitted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!permitted) {
                    log("AUTOSHOW_NIGHT_LOCATION: no location permission, so day/night uses: ${com.andrerinas.openheadunit.utils.NightMode(settings, true).locationSource()}")
                    return@Thread
                }
                val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var best: Location? = null
                for (provider in manager.getProviders(true)) {
                    val fix = try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null } ?: continue
                    if (!NightLocation.plausible(fix.latitude, fix.longitude)) continue
                    if (best == null || fix.time > best.time) best = fix
                }
                val found = best
                if (found == null) {
                    log("AUTOSHOW_NIGHT_LOCATION: the phone has no last known position; day/night uses: ${com.andrerinas.openheadunit.utils.NightMode(settings, true).locationSource()}")
                    return@Thread
                }
                settings.lastKnownLocation = found
                val ageMs = System.currentTimeMillis() - found.time
                log("AUTOSHOW_NIGHT_LOCATION: stored the phone's last known position (provider ${found.provider}, ${NightLocation.age(ageMs)} old)")
            } catch (e: Exception) {
                log("AUTOSHOW_NIGHT_LOCATION_FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }
}
