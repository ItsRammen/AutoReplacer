package com.andrerinas.openheadunit.autoshow

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Invisible entry point for the Auto+ Show / Garmin USB Link accessory-attached intent. Hands the
 * accessory to [AutoShowService] and finishes straight away.
 */
class AutoShowLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accessory: UsbAccessory? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        }
        if (accessory == null) {
            AppLog.w("AutoShowLauncher: started without a USB accessory")
            finish()
            return
        }
        val usb = getSystemService(USB_SERVICE) as UsbManager
        if (!usb.hasPermission(accessory)) {
            AppLog.w("AutoShowLauncher: no USB permission for the accessory yet; the system prompt should have granted it")
            Toast.makeText(this, "Allow USB access for AutoReplacer, then reconnect the cable", Toast.LENGTH_LONG).show()
        }
        AutoShowService.start(this, accessory)
        finish()
    }
}
