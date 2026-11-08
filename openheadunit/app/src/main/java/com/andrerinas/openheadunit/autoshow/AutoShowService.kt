package com.andrerinas.openheadunit.autoshow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.aap.protocol.messages.TouchEvent
import com.andrerinas.openheadunit.aap.protocol.proto.Input
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * Auto+ Show mode: runs Android Auto on this phone (Open Headunit's self mode, without its own
 * projection screen), encodes the decoded picture and sends it to the car over Garmin USB Link, and
 * feeds the car's touches back to Android Auto.
 */
class AutoShowService : Service(), GarminLink.Listener, CarTouchServer.Listener {

    companion object {
        const val ACTION_START = "com.andrerinas.openheadunit.autoshow.START"
        const val ACTION_STOP = "com.andrerinas.openheadunit.autoshow.STOP"
        private const val CHANNEL = "autoshow"
        private const val NOTIFICATION = 4201

        fun start(context: Context, accessory: UsbAccessory) {
            val intent = Intent(context, AutoShowService::class.java)
                .setAction(ACTION_START).putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var link: GarminLink? = null
    private var encoder: CarEncoder? = null
    private var touchServer: CarTouchServer? = null
    private var savedResolutionId: Int? = null
    private var projecting = false
    private var ended = false
    private var status = "Waiting for the car"
    private var carWidth = 0
    private var carHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        // Claim port 8051 before the car pushes and starts its own touch helper.
        touchServer = CarTouchServer(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown("Stopped by user"); return START_NOT_STICKY }
        val accessory: UsbAccessory? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        }
        if (accessory == null) { stopSelf(); return START_NOT_STICKY }
        if (link != null) { log("AUTOSHOW_START_IGNORED: a car session is already running"); return START_NOT_STICKY }
        val real = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(real)
        val dpi = resources.displayMetrics.densityDpi
        val usb = getSystemService(USB_SERVICE) as UsbManager
        link = GarminLink(usb, accessory, this, maxOf(real.x, real.y), minOf(real.x, real.y), dpi).also { it.start() }
        val blocker = AutoShowReadiness.carTouchBlocker(this)
        if (blocker != null) log("AUTOSHOW_READINESS: $blocker")
        setStatus("Connecting to the car" + (blocker?.let { " · $it" } ?: ""))
        return START_NOT_STICKY
    }

    // --- GarminLink.Listener -------------------------------------------------

    override fun log(text: String) {
        AppLog.i("AutoShow: %s", text)
        if (text.startsWith("TS8051_BIND_FAILED")) setStatus("Car touch unavailable: restart the phone once, then reconnect")
    }

    override fun videoReady(width: Int, height: Int, encoding: Int) {
        main.post { beginProjection(width, height, encoding) }
    }

    override fun ended(reason: String, error: Boolean) {
        main.post { shutdown(if (error) "Car link error: $reason" else "Car link closed") }
    }

    private fun beginProjection(width: Int, height: Int, encoding: Int) {
        if (ended || projecting) return
        if (encoding == 0) { shutdown("The car asked for raw video, which AutoReplacer does not send"); return }
        projecting = true
        try {
            val currentLink = link ?: return
            val enc = CarEncoder(width, height, object : CarEncoder.Sink {
                override fun frame(data: ByteArray, codecConfig: Boolean) { currentLink.sendVideo(data) }
                override fun log(text: String) { this@AutoShowService.log(text) }
                override fun failed(error: Exception) { main.post { shutdown("Encoder failed: ${error.message}") } }
            })
            val surface = enc.start()
            encoder = enc
            val comp = App.provide(this)
            // Ask Android Auto for a picture the same size as the car's, so no rescaling is needed on either side.
            val wanted = if (width >= 1280) Settings.Resolution._1280x720 else Settings.Resolution._800x480
            savedResolutionId = comp.settings.resolutionId
            comp.settings.resolutionId = wanted.id
            comp.videoDecoder.setSurface(surface)
            log("AUTOSHOW_PROJECTION: car ${width}x$height, Android Auto ${wanted.resName}, decoder -> encoder surface")
            startService(Intent(this, AapService::class.java)
                .setAction(AapService.ACTION_START_SELF_MODE)
                .putExtra(AapService.EXTRA_NO_UI, true))
            setStatus("Starting Android Auto on the phone")
        } catch (e: Exception) {
            log("AUTOSHOW_START_FAILED: ${e.javaClass.simpleName}: ${e.message}")
            shutdown("Could not start projection: ${e.message}")
        }
    }

    // --- CarTouchServer.Listener ---------------------------------------------

    override fun carSize(phoneWidth: Int, phoneHeight: Int, carWidth: Int, carHeight: Int) {
        this.carWidth = carWidth; this.carHeight = carHeight
        setStatus("Car touch connected")
    }

    private var touchLogged = 0

    override fun touch(x: Int, y: Int, b: Int, carWidth: Int, carHeight: Int) {
        val comp = App.provide(this)
        val cw = if (carWidth > 0) carWidth else this.carWidth.takeIf { it > 0 } ?: 1280
        val ch = if (carHeight > 0) carHeight else this.carHeight.takeIf { it > 0 } ?: 720
        val decoder = comp.videoDecoder
        val configured = Settings.Resolution.fromId(comp.settings.resolutionId)
        val vw = decoder.videoWidth.takeIf { it > 0 } ?: configured?.width?.takeIf { it > 0 } ?: 1280
        val vh = decoder.videoHeight.takeIf { it > 0 } ?: configured?.height?.takeIf { it > 0 } ?: 720
        val ax = (x.toLong() * vw / cw).toInt().coerceIn(0, vw - 1)
        val ay = (y.toLong() * vh / ch).toInt().coerceIn(0, vh - 1)
        val action = when (b) {
            0 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN
            1 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_UP
            2 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_MOVE
            else -> return
        }
        if (!comp.commManager.isConnected) {
            if (touchLogged++ < 5) log("AUTOSHOW_TOUCH_DROPPED: Android Auto session not connected yet")
            return
        }
        if (b != 2 || touchLogged++ < 20) log("AUTOSHOW_TOUCH car=$x,$y/${cw}x$ch -> aa=$ax,$ay/${vw}x$vh action=$b")
        comp.commManager.send(TouchEvent(SystemClock.uptimeMillis(), action, 0, listOf(Triple(0, ax, ay))))
    }

    // --- lifecycle ------------------------------------------------------------

    private fun shutdown(reason: String) {
        if (ended) return
        ended = true
        log("AUTOSHOW_SHUTDOWN: $reason")
        try { link?.stop(reason) } catch (_: Exception) {}
        try { encoder?.stop() } catch (_: Exception) {}
        encoder = null
        if (projecting) {
            val comp = App.provide(this)
            try { comp.videoDecoder.setSurface(null) } catch (_: Exception) {}
            savedResolutionId?.let { comp.settings.resolutionId = it }
            try {
                startService(Intent(this, AapService::class.java).setAction(AapService.ACTION_STOP_SELF_MODE))
            } catch (_: Exception) {}
        }
        stopSelf()
    }

    override fun onDestroy() {
        touchServer?.stop()
        if (!ended) { ended = true; try { link?.stop("service destroyed") } catch (_: Exception) {}; try { encoder?.stop() } catch (_: Exception) {} }
        super.onDestroy()
    }

    // --- notification ---------------------------------------------------------

    private fun setStatus(text: String) {
        status = text
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION, buildNotification())
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_stat_aa)
            .setContentTitle("AutoReplacer · Auto+ Show")
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Auto+ Show", NotificationManager.IMPORTANCE_LOW))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION, buildNotification())
        }
    }
}
