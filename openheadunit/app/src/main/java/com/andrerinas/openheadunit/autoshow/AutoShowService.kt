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
import com.andrerinas.openheadunit.aap.protocol.messages.VideoFocusEvent
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.utils.HeadUnitScreenConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        const val ACTION_SAVE_LOG = "com.andrerinas.openheadunit.autoshow.SAVE_LOG"

        /** True from the moment the car encoder owns the decoder until the session ends. */
        @Volatile var carSessionActive = false
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private var carBluetooth: CarBluetooth? = null
    private var bluetoothStatus: String? = null
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
        if (intent?.action == ACTION_SAVE_LOG) { saveLog(); return START_NOT_STICKY }
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

    override fun carBluetooth(addressOrNull: String?) {
        main.post {
            if (ended) return@post
            carBluetooth?.stop()
            carBluetooth = CarBluetooth(this, { text -> log(text) }, { text ->
                main.post { bluetoothStatus = text; refreshNotification() }
            }).also { it.start(addressOrNull) }
        }
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
            // Android Auto draws for the car's landscape screen, not the phone's.
            HeadUnitScreenConfig.carCanvas = Pair(maxOf(width, height), minOf(width, height))
            HeadUnitScreenConfig.unlockResolution()
            comp.videoDecoder.surfaceLock = null
            comp.videoDecoder.setSurface(surface)
            comp.videoDecoder.surfaceLock = surface
            carSessionActive = true
            comp.videoDecoder.onFirstFrameListener = { log("AUTOSHOW_FIRST_FRAME: Android Auto picture reached the car encoder") }
            watchStats(surface)
            log("AUTOSHOW_PROJECTION: car ${width}x$height, Android Auto ${wanted.resName}, decoder -> encoder surface")
            startService(Intent(this, AapService::class.java)
                .setAction(AapService.ACTION_START_SELF_MODE)
                .putExtra(AapService.EXTRA_NO_UI, true))
            setStatus("Starting Android Auto on the phone")
            watchAndroidAuto(comp)
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

    /**
     * Open Headunit starts reading Android Auto's messages only when its projection screen has a
     * surface. There is no such screen in car mode, so the message loop is started here as soon as
     * the handshake is complete; without it Android Auto waits for an answer and sends no video.
     */
    private fun watchAndroidAuto(comp: com.andrerinas.openheadunit.AppComponent) {
        stateJob?.cancel()
        stateJob = scope.launch {
            comp.commManager.connectionState.collect { state ->
                when (state) {
                    is CommManager.ConnectionState.HandshakeComplete -> {
                        log("AUTOSHOW_AA_HANDSHAKE_COMPLETE: starting the Android Auto message loop")
                        launch { comp.commManager.startReading() }
                    }
                    is CommManager.ConnectionState.TransportStarted -> {
                        setStatus("Android Auto is running")
                        main.postDelayed({
                            if (!ended && comp.videoDecoder.lastFrameRenderedMs == 0L && comp.commManager.isConnected) {
                                log("AUTOSHOW_VIDEO_FOCUS_NUDGE: no picture yet, asking for video focus")
                                comp.commManager.send(VideoFocusEvent(gain = true, unsolicited = true))
                            }
                        }, 6000L)
                    }
                    else -> {}
                }
            }
        }
    }

    // --- diagnostics ----------------------------------------------------------

    /** Every 5 s for the first minute, then every 30 s: shows where the picture stops if the car stays blank. */
    private fun watchStats(surface: android.view.Surface) {
        var ticks = 0
        val task = object : Runnable {
            override fun run() {
                if (ended) return
                val comp = App.provide(this@AutoShowService)
                val d = comp.videoDecoder
                val rendered = if (d.lastFrameRenderedMs > 0) "${(SystemClock.elapsedRealtime() - d.lastFrameRenderedMs)}ms ago" else "never"
                log("AUTOSHOW_STATS aaConnected=${comp.commManager.isConnected} aaState=${comp.commManager.connectionState.value.javaClass.simpleName}" +
                    " decoderOnCarSurface=${d.isCurrentSurface(surface)} lastDecodedFrame=$rendered" +
                    " decoded=${d.videoWidth}x${d.videoHeight} carFrames=${link?.frames() ?: 0}")
                ticks++
                main.postDelayed(this, if (ticks < 12) 5000L else 30000L)
            }
        }
        main.postDelayed(task, 5000L)
    }

    /** Saves this app's log to Downloads so it can be shared without a computer. */
    private fun saveLog() {
        Thread {
            val name = "AutoReplacer-log-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt"
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val body = process.inputStream.bufferedReader().use { it.readText() }
                val header = "AutoReplacer log ${java.util.Date()}\n" +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    AutoShowReadiness.summary(this) + "\n\n"
                val text = header + body
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException("Could not create the file in Downloads")
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                } else {
                    val dir = getExternalFilesDir(null) ?: filesDir
                    java.io.File(dir, name).writeText(text)
                }
                log("AUTOSHOW_LOG_SAVED $name bytes=${text.length}")
                main.post { android.widget.Toast.makeText(this, "Log saved to Downloads: $name", android.widget.Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                log("AUTOSHOW_LOG_SAVE_FAILED ${e.javaClass.simpleName}: ${e.message}")
                main.post { android.widget.Toast.makeText(this, "Could not save the log: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // --- lifecycle ------------------------------------------------------------

    private fun shutdown(reason: String) {
        if (ended) return
        ended = true
        log("AUTOSHOW_SHUTDOWN: $reason")
        try { link?.stop(reason) } catch (_: Exception) {}
        try { encoder?.stop() } catch (_: Exception) {}
        encoder = null
        carSessionActive = false
        stateJob?.cancel()
        HeadUnitScreenConfig.carCanvas = null
        if (projecting) {
            val comp = App.provide(this)
            HeadUnitScreenConfig.unlockResolution()
            comp.videoDecoder.surfaceLock = null
            try { comp.videoDecoder.setSurface(null) } catch (_: Exception) {}
            savedResolutionId?.let { comp.settings.resolutionId = it }
            try {
                startService(Intent(this, AapService::class.java).setAction(AapService.ACTION_STOP_SELF_MODE))
            } catch (_: Exception) {}
        }
        stopSelf()
    }

    override fun onDestroy() {
        carSessionActive = false
        HeadUnitScreenConfig.carCanvas = null
        scope.cancel()
        try { App.provide(this).videoDecoder.surfaceLock = null } catch (_: Exception) {}
        carBluetooth?.stop()
        touchServer?.stop()
        if (!ended) { ended = true; try { link?.stop("service destroyed") } catch (_: Exception) {}; try { encoder?.stop() } catch (_: Exception) {} }
        super.onDestroy()
    }

    // --- notification ---------------------------------------------------------

    private fun setStatus(text: String) {
        status = text
        refreshNotification()
    }

    private fun refreshNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION, buildNotification())
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val text = status + (bluetoothStatus?.let { "\nBluetooth: $it" } ?: "")
        return builder.setSmallIcon(R.drawable.ic_stat_aa)
            .setContentTitle("AutoReplacer · Auto+ Show")
            .setContentText(status)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_aa), "Save log",
                android.app.PendingIntent.getService(this, 1,
                    Intent(this, AutoShowService::class.java).setAction(ACTION_SAVE_LOG),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)).build())
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
