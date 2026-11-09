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
        const val ACTION_CALIBRATE = "com.andrerinas.openheadunit.autoshow.CALIBRATE"
        const val ACTION_MARK = "com.andrerinas.openheadunit.autoshow.MARK"
        /** How long Android Auto is kept after the car link drops. */
        private const val STANDBY_MS = 90_000L

        /** True from the moment the car encoder owns the decoder until the session ends. */
        @Volatile var carSessionActive = false

        /** True while this service exists; Android Auto's service then shows the same notification. */
        @Volatile var serviceRunning = false

        fun start(context: Context, accessory: UsbAccessory) {
            val intent = Intent(context, AutoShowService::class.java)
                .setAction(ACTION_START).putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var link: GarminLink? = null
    private var encoder: CarEncoder? = null
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
    private var lastStartId = 0
    /** True while the car link is gone but Android Auto and the encoder are kept running for a returning cable. */
    private var standby = false
    /** The phone's battery, temperature, charging and thermal state, for lining a drop up with something on the phone. */
    private fun phoneContext(): String {
        return try {
            val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val plugged = when (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
                0 -> "none"; android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"; android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"; else -> "other"
            }
            val status = when (battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0) ?: 0) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"; android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"; android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"; else -> "unknown"
            }
            val thermal = if (Build.VERSION.SDK_INT >= 29) "thermal=${(getSystemService(POWER_SERVICE) as android.os.PowerManager).currentThermalStatus}" else "thermal=n/a"
            "battery=${if (level >= 0) level * 100 / scale else -1}% temp=${temp}C plugged=$plugged status=$status $thermal"
        } catch (e: Exception) { "phone state unavailable (${e.javaClass.simpleName})" }
    }

    /** Android Auto's picture has reached the car encoder at least once in this car session. */
    @Volatile private var aaEverRan = false
    private var encoderSurface: android.view.Surface? = null
    private val standbyTimeout = Runnable { shutdown("The car did not come back") }
    private var splash: CarSplash? = null
    private val splashLock = Any()
    private var splashOpen = false
    private val aaPictureFrames = java.util.concurrent.atomic.AtomicLong()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceRunning = true
        goForeground()
        // Keep the notification in step with every state change (touch, Bluetooth, Android Auto), not only the ones that set a status.
        scope.launch { AutoShowState.state.collect { refreshNotification() } }
        // Port 8051 is held by the process-wide hub (started with the app), so the car's own touch helper cannot take it.
        AutoShowTouchHub.attach(this)
        // Phone state changes are logged with the time so they can be lined up with touch silence.
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // USB_STATE says what the cable is doing electrically and as a function; POWER_* says whether the phone
                // lost power. A link that dies while both stay unchanged was not unplugged.
                val extras = if (intent?.action == "android.hardware.usb.action.USB_STATE") {
                    intent.extras?.keySet()?.sorted()?.joinToString(" ", " ") { key -> "$key=${intent.extras?.get(key)}" } ?: ""
                } else ""
                log("PHONE_STATE ${intent?.action?.substringAfterLast('.')}$extras touchLines=$touchLines lastTouch=${if (lastTouchAt == 0L) "never" else "${(SystemClock.elapsedRealtime() - lastTouchAt) / 1000}s ago"}")
            }
        }
        phoneStateReceiver = receiver
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown("Stopped by user"); return START_NOT_STICKY }
        if (intent?.action == ACTION_SAVE_LOG) { saveLog(); return START_NOT_STICKY }
        if (intent?.action == ACTION_CALIBRATE) { startCalibration(startId); return START_NOT_STICKY }
        if (intent?.action == ACTION_MARK) {
            // A timestamp the user puts in the log by hand ("the car just did something"), next to the phone's own state.
            log("USER_MARK: marked by the user; ${phoneContext()}")
            toast("Marked in the log at " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
            if (link == null && !projecting) stopSelf(startId)
            return START_NOT_STICKY
        }
        val accessory: UsbAccessory? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        }
        lastStartId = startId
        if (accessory == null) { if (link == null) stopSelf(startId); return START_NOT_STICKY }
        // The cable was plugged back in while the previous session was still closing: start afresh in this instance.
        if (ended) resetForNewSession()
        link?.let { existing ->
            if (existing.isVideoReady() && !existing.isClosing()) {
                log("AUTOSHOW_START_IGNORED: a car session is already running")
                return START_NOT_STICKY
            }
            // A cable that bounced gives a new accessory while the old link is dead or never got ready: use the new one.
            log("AUTOSHOW_LINK_REPLACED: a new cable connection arrived before the previous one was ready")
            link = null
            // Closed at once (not after the usual second): the new link opens the same accessory next.
            try { existing.abort("replaced by a new connection") } catch (_: Exception) {}
        }
        val real = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(real)
        val dpi = resources.displayMetrics.densityDpi
        val usb = getSystemService(USB_SERVICE) as UsbManager
        link = GarminLink(usb, accessory, this, maxOf(real.x, real.y), minOf(real.x, real.y), dpi).also { it.start() }
        val blocker = AutoShowReadiness.carTouchBlocker(this)
        if (blocker != null) log("AUTOSHOW_READINESS: $blocker")
        AutoShowServerProbe.refresh { up ->
            if (!up && !ended && !projecting) {
                log("AUTOSHOW_HEAD_UNIT_SERVER_DOWN: nothing is listening on 127.0.0.1:5277")
                setStatus("Connecting to the car. " + AutoShowServerProbe.HINT)
            }
        }
        val earlierTouch = AutoShowState.state.value.touch
        if (standby) {
            // Android Auto is still running from before: only the car side has to come back.
            AutoShowState.update { it.copy(headline = "Reconnecting to the car (Android Auto kept running)") }
        } else AutoShowState.reset(AutoShowState.Phase.CONNECTING, "Connecting to the car")
        // A port-8051 failure was found when the service started, before this session began.
        if (earlierTouch.startsWith("Unavailable") || earlierTouch.startsWith("Blocked")) AutoShowState.update { it.copy(touch = earlierTouch) }
        setStatus((if (standby) "Reconnecting to the car (Android Auto kept running)" else "Connecting to the car") + (blocker?.let { " · $it" } ?: ""))
        return START_NOT_STICKY
    }

    // --- GarminLink.Listener -------------------------------------------------

    override fun log(text: String) {
        AppLog.i("AutoShow: %s", text)
        AutoShowEventLog.append(this, text)
        // The car has opened its touch link; its first touch (which sends the screen size) has not come yet.
        if (text.startsWith("TS8051_CONNECTED")) {
            AutoShowState.update {
                if (it.touch.startsWith("Blocked") || it.touch.startsWith("Working")) it
                else it.copy(touch = "Connected; waiting for the first touch")
            }
        }
        if (text.startsWith("TS8051_BIND_FAILED")) setStatus("Car touch is blocked by the car's old touch helper; trying to clear it. Unplug and replug if it stays blocked.")
    }

    override fun carBluetooth(addressOrNull: String?) {
        main.post {
            if (ended) return@post
            carBluetooth?.stop()
            carBluetooth = CarBluetooth(this, { text -> log(text) }, { text ->
                main.post { bluetoothStatus = text; AutoShowState.update { it.copy(bluetooth = text) }; refreshNotification() }
            }).also { it.start(addressOrNull) }
        }
    }

    override fun videoReady(width: Int, height: Int, encoding: Int) {
        main.post { beginProjection(width, height, encoding) }
    }

    override fun ended(reason: String, error: Boolean) {
        main.post {
            // A link that was replaced or already handled reports its end late; only the current one counts.
            val current = link
            if (current != null && !current.isClosing()) return@post
            if (current == null && standby) return@post
            // The technical reason goes to the event log; the person using the app gets a sentence.
            log("AUTOSHOW_LINK_ENDED: error=$error reason=$reason")
            log("AUTOSHOW_PHONE_CONTEXT: ${phoneContext()}")
            val friendly = DisconnectReason.describe(reason, error)
            if (canStandBy()) enterStandby(friendly) else shutdown(friendly)
        }
    }

    /** Android Auto is up and its picture has been flowing, so a dropped cable is worth waiting out. */
    private fun canStandBy(): Boolean {
        if (ended || !projecting || !aaEverRan) return false
        val phase = AutoShowState.state.value.phase
        return phase == AutoShowState.Phase.RUNNING || phase == AutoShowState.Phase.STANDBY ||
            phase == AutoShowState.Phase.STARTING_ANDROID_AUTO
    }

    /**
     * The car link is gone (the cable dropped, or the car restarted its side). Android Auto's own session is
     * the slow part to bring back, and its head unit server stops once the session ends, so keep it, and the
     * encoder it draws into, for a while. A returning cable then only has to rebuild the car link.
     */
    private fun enterStandby(reason: String) {
        if (calibrating) endCalibration("Touch calibration stopped: the car disconnected")
        try { link?.stop(reason) } catch (_: Exception) {}
        link = null
        val firstTime = !standby
        standby = true
        if (firstTime) {
            main.removeCallbacks(standbyTimeout)
            main.postDelayed(standbyTimeout, STANDBY_MS)
        }
        log("AUTOSHOW_STANDBY: $reason Keeping Android Auto for ${STANDBY_MS / 1000} s")
        // The picture flag stays as it is: Android Auto may well still be drawing, and it is cleared if its session ends.
        AutoShowState.update { it.copy(phase = AutoShowState.Phase.STANDBY) }
        setStatus("$reason Android Auto is kept ready for ${STANDBY_MS / 1000} s: plug the cable back in.")
    }

    /** The cable is back and the car is ready for video: send it the picture Android Auto is already drawing. */
    private fun resumeFromStandby(width: Int, height: Int, encoding: Int) {
        if (encoding == 0 || width != pictureW || height != pictureH) {
            log("AUTOSHOW_STANDBY_MISMATCH: car now ${width}x$height (was ${pictureW}x$pictureH), starting over")
            shutdown("The car's picture settings changed; plug the cable in again")
            return
        }
        standby = false
        main.removeCallbacks(standbyTimeout)
        val e = encoder
        val l = link ?: return
        if (AutoShowState.state.value.picture) {
            // Android Auto is drawing: the car joins the stream with its parameters and a key frame.
            e?.lastConfig()?.let { config -> l.sendVideo(config) }
            e?.requestKeyFrame()
            AutoShowState.update { it.copy(phase = AutoShowState.Phase.RUNNING) }
            setStatus("Android Auto is running")
            log("AUTOSHOW_RESUMED: the car is back and Android Auto's picture is flowing")
        } else {
            // Android Auto is still coming back (its session restarted): show the waiting picture, not black.
            startSplash(l, width, height)
            AutoShowState.update { it.copy(phase = AutoShowState.Phase.STARTING_ANDROID_AUTO) }
            setStatus("Waiting for Android Auto to come back")
            log("AUTOSHOW_RESUMED: the car is back, waiting for Android Auto's picture")
        }
    }

    /** The Android Auto picture on its way to the car: counts frames, hands over from the waiting picture, sends to the current link. */
    private fun mainSink(): CarEncoder.Sink = object : CarEncoder.Sink {
        override fun frame(data: ByteArray, codecConfig: Boolean) {
            if (aaPictureFrames.incrementAndGet() == 1L) aaEverRan = true
            // While the calibration picture is on the car, Android Auto's picture waits.
            if (calibrating) return
            // The first Android Auto picture replaces the waiting picture; no splash frame may follow it.
            var wasSplash = false
            synchronized(splashLock) { if (splashOpen) { splashOpen = false; wasSplash = true } }
            if (wasSplash) {
                log("AUTOSHOW_SPLASH_DONE: the first Android Auto picture replaces the waiting picture")
                val old = splash; splash = null
                Thread { try { old?.stop() } catch (_: Exception) {} }.start()
            }
            // The current link, not the one from when the projection began: a cable that comes back has a new one.
            link?.sendVideo(data)
        }
        override fun log(text: String) { this@AutoShowService.log(text) }
        override fun failed(error: Exception) {
            main.post {
                log("AUTOSHOW_ENCODER_FAILED: ${error.javaClass.simpleName}: ${error.message}")
                shutdown("The phone could not encode the picture for the car. Plug in again.")
            }
        }
    }

    private fun beginProjection(width: Int, height: Int, encoding: Int) {
        if (standby && !ended) { resumeFromStandby(width, height, encoding); return }
        if (ended || projecting) return
        if (encoding == 0) { shutdown("The car asked for a picture format AutoReplacer does not support."); return }
        projecting = true
        try {
            val currentLink = link ?: return
            val enc = CarEncoder(width, height, mainSink())
            pictureW = width; pictureH = height
            startSplash(currentLink, width, height)
            val surface = enc.start()
            encoder = enc
            encoderSurface = surface
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
            comp.videoDecoder.onFirstFrameListener = { log("AUTOSHOW_FIRST_FRAME: Android Auto picture reached the car encoder"); AutoShowState.update { s -> s.copy(picture = true) } }
            watchStats(surface)
            log("AUTOSHOW_PROJECTION: car ${width}x$height, Android Auto ${wanted.resName}, decoder -> encoder surface")
            startService(Intent(this, AapService::class.java)
                .setAction(AapService.ACTION_START_SELF_MODE)
                .putExtra(AapService.EXTRA_NO_UI, true))
            AutoShowState.update { it.copy(phase = AutoShowState.Phase.STARTING_ANDROID_AUTO) }
            setStatus("Starting Android Auto on the phone")
            watchAndroidAuto(comp)
        } catch (e: Exception) {
            log("AUTOSHOW_START_FAILED: ${e.javaClass.simpleName}: ${e.message}")
            shutdown("Could not start Android Auto on the phone (${e.javaClass.simpleName}). Plug in again.")
        }
    }

    /** Shows a waiting picture on the car until Android Auto's first picture arrives. Failure here is not fatal. */
    private var splashGeneration = 0

    private fun startSplash(currentLink: GarminLink, width: Int, height: Int) {
        try {
            // A waiting picture from an earlier link must not be reused: it was sending to that link, and a new link
            // needs a stream that starts with its own parameters and a key frame. Replace it.
            val stale = splash
            if (stale != null) { splash = null; Thread { try { stale.stop() } catch (_: Exception) {} }.start() }
            val generation = synchronized(splashLock) { splashOpen = true; ++splashGeneration }
            splash = CarSplash(width, height, object : CarEncoder.Sink {
                override fun frame(data: ByteArray, codecConfig: Boolean) {
                    synchronized(splashLock) { if (splashOpen && generation == splashGeneration) link?.sendVideo(data) }
                }
                override fun log(text: String) { this@AutoShowService.log(text.replace("CAR_ENCODER", "CAR_SPLASH_ENCODER")) }
                override fun failed(error: Exception) { this@AutoShowService.log("CAR_SPLASH_FAILED: ${error.message}") }
            }, CarSplash.Text { AutoShowState.state.value.headline }).also { it.start() }
        } catch (e: Exception) {
            synchronized(splashLock) { splashOpen = false }
            splash = null
            log("CAR_SPLASH_START_FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // --- touch calibration ----------------------------------------------------

    private var pictureW = 0
    private var pictureH = 0
    @Volatile private var calibrating = false
    private var calibrationScreen: CarCalibrationScreen? = null
    private var calibrationIndex = 0
    private var calibrationMessage = ""
    private var calibrationDown = false
    private val calibrationTargets = arrayOf(
        doubleArrayOf(0.10, 0.08), doubleArrayOf(0.90, 0.08), doubleArrayOf(0.90, 0.92), doubleArrayOf(0.10, 0.92),
        doubleArrayOf(0.50, 0.50), doubleArrayOf(0.50, 0.08), doubleArrayOf(0.50, 0.92), doubleArrayOf(0.10, 0.50), doubleArrayOf(0.90, 0.50))
    private val measuredX = DoubleArray(calibrationTargets.size)
    private val measuredY = DoubleArray(calibrationTargets.size)
    private val calibrationTimeout = Runnable { endCalibration("Touch calibration timed out") }

    private fun toast(text: String) = main.post { android.widget.Toast.makeText(applicationContext, text, android.widget.Toast.LENGTH_LONG).show() }

    /** Shows a grid of crosses on the car and measures where the car reports each tap. */
    private fun startCalibration(startId: Int) {
        val currentLink = link
        if (currentLink == null || !projecting || aaPictureFrames.get() == 0L || calibrating || pictureW == 0) {
            toast("Start the car session and wait for the Android Auto picture first")
            if (link == null) stopSelf(startId)
            return
        }
        try {
            calibrationIndex = 0; calibrationMessage = ""; calibrationDown = false
            val screen = CarCalibrationScreen(pictureW, pictureH, object : CarEncoder.Sink {
                override fun frame(data: ByteArray, codecConfig: Boolean) { synchronized(splashLock) { if (calibrating) currentLink.sendVideo(data) } }
                override fun log(text: String) { this@AutoShowService.log(text.replace("CAR_ENCODER", "CAR_CALIBRATION_ENCODER")) }
                override fun failed(error: Exception) { this@AutoShowService.log("CAR_CALIBRATION_FAILED: ${error.message}") }
            }, object : CarCalibrationScreen.Progress {
                override fun index() = calibrationIndex
                override fun total() = calibrationTargets.size
                override fun target(index: Int) = calibrationTargets[index]
                override fun message() = calibrationMessage
            })
            calibrationScreen = screen
            calibrating = true
            screen.start()
            main.removeCallbacks(calibrationTimeout)
            main.postDelayed(calibrationTimeout, 180000L)
            log("AUTOSHOW_CALIBRATION_START: ${calibrationTargets.size} targets on the car")
            toast("Look at the car screen and tap the middle of each cross")
        } catch (e: Exception) {
            calibrating = false
            log("AUTOSHOW_CALIBRATION_START_FAILED: ${e.javaClass.simpleName}: ${e.message}")
            toast("Could not start the touch calibration")
        }
    }

    private fun calibrationTouch(x: Int, y: Int, b: Int, cw: Int, ch: Int) {
        if (b == 0 && !calibrationDown) {
            calibrationDown = true
            measuredX[calibrationIndex] = x.toDouble() / cw
            measuredY[calibrationIndex] = y.toDouble() / ch
            log("AUTOSHOW_CALIBRATION_TAP target=${calibrationIndex} wanted=${calibrationTargets[calibrationIndex][0]},${calibrationTargets[calibrationIndex][1]} raw=$x,$y/${cw}x$ch")
            main.removeCallbacks(calibrationTimeout)
            main.postDelayed(calibrationTimeout, 180000L)
        } else if (b == 1 && calibrationDown) {
            calibrationDown = false
            calibrationMessage = ""
            calibrationIndex++
            if (calibrationIndex >= calibrationTargets.size) finishCalibration()
        }
    }

    private fun finishCalibration() {
        val tx = DoubleArray(calibrationTargets.size) { calibrationTargets[it][0] }
        val ty = DoubleArray(calibrationTargets.size) { calibrationTargets[it][1] }
        val fx = TouchArea.fit(tx, measuredX)
        val fy = TouchArea.fit(ty, measuredY)
        if (fx != null && fy != null) {
            AutoShowTouchArea.saveCalibration(this, fx[0], fx[1], fy[0], fy[1])
            log("AUTOSHOW_CALIBRATION_DONE: x measured = %.4f * picture %+.4f, y measured = %.4f * picture %+.4f".format(fx[0], fx[1], fy[0], fy[1]))
            endCalibration("Touch calibrated: ${AutoShowTouchArea.describe(this)}")
        } else {
            log("AUTOSHOW_CALIBRATION_REFUSED: the taps did not describe a plausible screen")
            calibrationIndex = 0
            calibrationMessage = "The taps did not look right. Start again with the first cross."
        }
    }

    private fun endCalibration(message: String) {
        if (!calibrating) return
        main.removeCallbacks(calibrationTimeout)
        synchronized(splashLock) { calibrating = false }
        val screen = calibrationScreen; calibrationScreen = null
        Thread { try { screen?.stop() } catch (_: Exception) {} }.start()
        // Android Auto's picture goes back on the car: its own parameters and a key frame first.
        encoder?.let { e ->
            e.lastConfig()?.let { config -> link?.sendVideo(config) }
            e.requestKeyFrame()
        }
        log("AUTOSHOW_CALIBRATION_END: $message")
        toast(message)
    }

    // --- CarTouchServer.Listener ---------------------------------------------

    override fun carSize(phoneWidth: Int, phoneHeight: Int, carWidth: Int, carHeight: Int) {
        this.carWidth = carWidth; this.carHeight = carHeight
        AutoShowState.update { it.copy(touch = "Working (car screen ${carWidth}x$carHeight)") }
        setStatus("Car touch connected")
    }

    /**
     * The car's driving-restriction flag. It is recorded, not acted on: in the logs the car reports speed=0 even
     * while driving, and touch has worked with the flag set and unanswered, so it is not a reliable gate. The
     * app still answers "allowed" only for an unrestricted, stationary car.
     */
    private var lastRestrictionFlag = -1
    override fun safetyState(restricted: Int, speed: Int, unlocked: Boolean) {
        main.post {
            if (restricted == lastRestrictionFlag) return@post
            lastRestrictionFlag = restricted
            log("AUTOSHOW_CAR_RESTRICTION_FLAG: restricted=$restricted speed=$speed (informational; touchLines=$touchLines)")
        }
    }

    private var touchLogged = 0
    private var touchLines = 0L
    private var touchAreaW = 0
    private var touchAreaH = 0
    private var touchesDropped = 0L
    private var lastTouchAt = 0L
    private var phoneStateReceiver: android.content.BroadcastReceiver? = null

    override fun touch(x: Int, y: Int, b: Int, carWidth: Int, carHeight: Int) {
        val now = SystemClock.elapsedRealtime()
        touchLines++
        // A touch after a long gap: shows when touches stop reaching the app and when they come back.
        if (lastTouchAt != 0L && now - lastTouchAt > 8000) log("AUTOSHOW_TOUCH_RESUMED: first touch after ${(now - lastTouchAt) / 1000} s of silence")
        lastTouchAt = now
        val comp = App.provide(this)
        val cw = if (carWidth > 0) carWidth else this.carWidth.takeIf { it > 0 } ?: 1280
        val ch = if (carHeight > 0) carHeight else this.carHeight.takeIf { it > 0 } ?: 720
        if (calibrating) { calibrationTouch(x, y, b, cw, ch); return }
        val decoder = comp.videoDecoder
        val configured = Settings.Resolution.fromId(comp.settings.resolutionId)
        val vw = decoder.videoWidth.takeIf { it > 0 } ?: configured?.width?.takeIf { it > 0 } ?: 1280
        val vh = decoder.videoHeight.takeIf { it > 0 } ?: configured?.height?.takeIf { it > 0 } ?: 720
        // The car's touches can run past the picture size it declares (a taller panel): use the real extent.
        AutoShowTouchArea.rememberDeclared(this, cw, ch)
        AutoShowTouchArea.learn(this, x, y)
        val tw = AutoShowTouchArea.widthExtent(this, cw)
        val th = AutoShowTouchArea.heightExtent(this, ch)
        if (tw != touchAreaW || th != touchAreaH) {
            touchAreaW = tw; touchAreaH = th
            log("AUTOSHOW_TOUCH_AREA: declared ${cw}x$ch, using ${tw}x$th (largest seen ${AutoShowTouchArea.maxX(this)},${AutoShowTouchArea.maxY(this)}, ${if (AutoShowTouchArea.isCalibrated(this)) "calibrated" else "not calibrated"})")
        }
        // A calibration (measured from taps on a target grid) replaces the learned extent.
        val calibration = AutoShowTouchArea.calibration(this)
        val ax = if (calibration != null) TouchArea.unmap(x, calibration[0], calibration[1], cw, vw) else TouchArea.map(x, tw, vw)
        val ay = if (calibration != null) TouchArea.unmap(y, calibration[2], calibration[3], ch, vh) else TouchArea.map(y, th, vh)
        val action = when (b) {
            0 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN
            1 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_UP
            2 -> Input.TouchEvent.PointerAction.TOUCH_ACTION_MOVE
            else -> return
        }
        if (!comp.commManager.isConnected) {
            if (touchesDropped++ < 5) log("AUTOSHOW_TOUCH_DROPPED: Android Auto session not connected yet")
            return
        }
        if (b != 2 || touchLogged++ < 20) log("AUTOSHOW_TOUCH car=$x,$y/${cw}x$ch -> aa=$ax,$ay/${vw}x$vh action=$b area=${tw}x$th")
        comp.commManager.send(TouchEvent(SystemClock.uptimeMillis(), action, 0, listOf(Triple(0, ax, ay))))
    }

    /**
     * Open Headunit starts reading Android Auto's messages only when its projection screen has a
     * surface. There is no such screen in car mode, so the message loop is started here as soon as
     * the handshake is complete; without it Android Auto waits for an answer and sends no video.
     */
    private fun watchAndroidAuto(comp: com.andrerinas.openheadunit.AppComponent) {
        stateJob?.cancel()
        // The state starts as Disconnected before the attempt has begun; only a Disconnected after a live state is a failure.
        var sawLive = false
        androidAutoRetries = 0
        stateJob = scope.launch {
            comp.commManager.connectionState.collect { state ->
                if (state !is CommManager.ConnectionState.Disconnected) sawLive = true
                when (state) {
                    is CommManager.ConnectionState.Disconnected -> {
                        if (sawLive && !ended) {
                            sawLive = false
                            // The encoder has timestamps from the old session; the new one restarts from zero and
                            // every frame would be dropped as "going backward in time". Start a fresh encoder.
                            if (aaPictureFrames.get() > 0) rebuildEncoder("Android Auto's session ended")
                            retryAndroidAuto(comp)
                        }
                    }
                    is CommManager.ConnectionState.HandshakeComplete -> {
                        log("AUTOSHOW_AA_HANDSHAKE_COMPLETE: starting the Android Auto message loop")
                        launch { comp.commManager.startReading() }
                    }
                    is CommManager.ConnectionState.TransportStarted -> {
                        androidAutoRetries = 0
                        if (!standby) {
                            AutoShowState.update { it.copy(phase = AutoShowState.Phase.RUNNING) }
                            setStatus("Android Auto is running")
                        }
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

    private var androidAutoRetries = 0

    /**
     * Android Auto 17.4 and later only answers once "Start head unit server" has been tapped in its
     * developer settings, and Open Headunit does not retry by itself. Say so on the phone and the car
     * and keep trying, so the picture comes up as soon as the server is started.
     */
    private fun retryAndroidAuto(comp: com.andrerinas.openheadunit.AppComponent) {
        if (androidAutoRetries >= 100) { shutdown("Android Auto did not answer"); return }
        androidAutoRetries++
        // Once per session, best effort: Android may refuse to open it from the background, so the home card has a button too.
        if (androidAutoRetries == 1 && !aaEverRan) AutoShowStatusCard.openAndroidAutoSettings(this)
        log("AUTOSHOW_AA_RETRY: attempt $androidAutoRetries, Android Auto's head unit server did not answer or the session ended")
        // After a session that was running, Android Auto's server is normally still there: come back quickly.
        val quick = aaEverRan && androidAutoRetries <= 3
        val hint = when {
            quick -> "Android Auto restarted. Reconnecting."
            aaEverRan -> "Android Auto is not answering. In Android Auto's developer settings, stop the head unit server and start it again."
            else -> "Android Auto is not answering. On the phone open Android Auto, then Developer settings, then Start head unit server."
        }
        AutoShowState.update {
            it.copy(phase = if (standby) AutoShowState.Phase.STANDBY else AutoShowState.Phase.STARTING_ANDROID_AUTO,
                headline = if (standby) it.headline else hint, picture = false)
        }
        if (!standby) setStatus(hint)
        main.postDelayed({
            if (!ended && !comp.commManager.isConnected) {
                startService(Intent(this, AapService::class.java)
                    .setAction(AapService.ACTION_START_SELF_MODE)
                    .putExtra(AapService.EXTRA_NO_UI, true))
            }
        }, if (quick) (if (androidAutoRetries <= 1) 3000L else 4500L) else 6000L)
    }

    /**
     * Replaces the car encoder (and the decoder's output with it). Android Auto's decoder restarts with
     * timestamps from zero, and an encoder that has seen later ones drops all of them.
     */
    private fun rebuildEncoder(reason: String) {
        if (ended || !projecting || pictureW == 0) return
        try {
            val comp = App.provide(this)
            val old = encoder
            val enc = CarEncoder(pictureW, pictureH, mainSink())
            val surface = enc.start()
            encoder = enc
            encoderSurface = surface
            comp.videoDecoder.surfaceLock = null
            comp.videoDecoder.setSurface(surface)
            comp.videoDecoder.surfaceLock = surface
            comp.videoDecoder.onFirstFrameListener = {
                log("AUTOSHOW_FIRST_FRAME: Android Auto picture reached the car encoder")
                AutoShowState.update { s -> s.copy(picture = true) }
            }
            aaPictureFrames.set(0)
            Thread { try { old?.stop() } catch (_: Exception) {} }.start()
            log("AUTOSHOW_ENCODER_REBUILT: $reason")
            // With the car connected, it shows the waiting picture until Android Auto's first new frame.
            val l = link
            if (l != null) startSplash(l, pictureW, pictureH)
        } catch (e: Exception) {
            log("AUTOSHOW_ENCODER_REBUILD_FAILED: ${e.javaClass.simpleName}: ${e.message}")
            shutdown("The phone could not restart the picture for the car. Plug in again.")
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
                    " decoderOnCarSurface=${encoderSurface?.let { d.isCurrentSurface(it) } == true} lastDecodedFrame=$rendered" +
                    " decoded=${d.videoWidth}x${d.videoHeight} carFrames=${link?.frames() ?: 0} aaPictureFrames=${aaPictureFrames.get()} splash=${splashOpen}" +
                    " touchLines=$touchLines touchesDropped=$touchesDropped lastTouch=${if (lastTouchAt == 0L) "never" else "${(SystemClock.elapsedRealtime() - lastTouchAt) / 1000}s ago"}")
                ticks++
                main.postDelayed(this, if (ticks < 12) 5000L else 30000L)
            }
        }
        main.postDelayed(task, 5000L)
    }

    private fun saveLog() = AutoShowLogSaver.save(applicationContext) { log(it) }

    // --- lifecycle ------------------------------------------------------------

    private fun shutdown(reason: String) {
        if (ended) return
        ended = true
        standby = false
        main.removeCallbacks(standbyTimeout)
        log("AUTOSHOW_SHUTDOWN: $reason")
        AutoShowState.update { it.copy(phase = AutoShowState.Phase.ENDED, headline = reason, picture = false) }
        try { link?.stop(reason) } catch (_: Exception) {}
        try { encoder?.stop() } catch (_: Exception) {}
        encoder = null
        synchronized(splashLock) { splashOpen = false; calibrating = false }
        splash?.let { s -> splash = null; Thread { try { s.stop() } catch (_: Exception) {} }.start() }
        calibrationScreen?.let { s -> calibrationScreen = null; Thread { try { s.stop() } catch (_: Exception) {} }.start() }
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
        // Not stopSelf(): a replug may already have queued a newer start, which must keep this service alive.
        stopSelf(lastStartId)
    }

    /** Clears what a finished session left behind so the same service instance can run the next one. */
    private fun resetForNewSession() {
        main.removeCallbacksAndMessages(null)
        carBluetooth?.stop(); carBluetooth = null
        link = null; encoder = null
        projecting = false; ended = false; standby = false; aaEverRan = false; lastRestrictionFlag = -1
        aaPictureFrames.set(0)
        bluetoothStatus = null
        touchLogged = 0
        carWidth = 0; carHeight = 0
        // The previous touch helper connection is gone with the cable; keep the listening socket.
    }

    override fun onDestroy() {
        serviceRunning = false
        carSessionActive = false
        HeadUnitScreenConfig.carCanvas = null
        scope.cancel()
        try { App.provide(this).videoDecoder.surfaceLock = null } catch (_: Exception) {}
        carBluetooth?.stop()
        AutoShowTouchHub.detach(this)
        phoneStateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        if (!ended) { ended = true; try { link?.stop("service destroyed") } catch (_: Exception) {}; try { encoder?.stop() } catch (_: Exception) {}; try { splash?.stop() } catch (_: Exception) {} }
        super.onDestroy()
        // The shared notification goes with this service; Android Auto's service, if it is still up, shows its own again.
        Handler(Looper.getMainLooper()).postDelayed({ AapService.refreshNotificationIfRunning() }, 500L)
    }

    // --- notification ---------------------------------------------------------

    private fun setStatus(text: String) {
        status = text
        AutoShowState.update { it.copy(headline = text) }
        refreshNotification()
    }

    private fun refreshNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(AutoShowNotification.ID, AutoShowNotification.build(this))
    }

    private fun goForeground() {
        // The same id and channel as Android Auto's own service use, so the two share one notification.
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(AutoShowNotification.ID, AutoShowNotification.build(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(AutoShowNotification.ID, AutoShowNotification.build(this))
        }
    }
}
