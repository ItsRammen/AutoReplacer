# AutoReplacer — Rental Display Probe 0.4.0

An Android USB accessory diagnostic for the Garmin USB Link head units used by Auto+ Show. A user has confirmed that version 0.1.1 displayed our generated video on their Taiwan Toyota Corolla Cross. This version adds a shared phone/car touch grid to test the return input path. It does not include Android Auto or Open Headunit yet.

## Install

Use **Stop connection** in the old app, then install `rental-display-probe-0.4.0.apk` over the supplied earlier version. You can leave the cable attached: reopen Auto+ Show on the car if needed, then use **Retry connection** in the updated app. It uses the same signing certificate and Android package. Android 8.0+ is required. This development-signed APK may require allowing installation from the app opening it.

This version returns to the simpler 0.1.1 USB sequence that displayed successfully. It removes the experimental Bluetooth-name exchange and Bluetooth permissions from 0.1.2. It requests USB accessory access and uses a connected-device foreground service, a partial CPU wake lock and an optional notification permission for background streaming. It does not record the phone screen or enable USB debugging, accessibility access or root. Passive observation remains the default; an optional user-selected mode asks the car to initiate its existing ADB input setup.

## Phone baseline

1. Keep USB unplugged and open the app.
2. Tap **Phone input check (no USB)**. The phone switches to a fullscreen landscape grid.
3. Tap the glowing targets in order **1 → 4 → 9 → 12 → 6**. A magenta mark should follow a single finger; a second pointer uses cyan.
4. Try a short drag and two fingers.
5. Tap **Logs** on the phone to return to the logs and controls. Android Back also works.

This establishes that the local view receives input. These events are logged as `phase=PHONE` and are cleared from the grid state before starting the car phase; the log retains them as baseline evidence.

## Car touch test

1. Park the car. Select **Auto+ Show** on its display. Close the Garmin phone app so it does not own the accessory.
2. Connect with the same data cable/USB port that displayed version 0.1.1. If Android asks which app to use, choose **Rental Display Probe → Just once** and allow USB access. It should open the car grid automatically. Otherwise tap **Start car touch grid**.
3. Wait until the car displays the grid and its frame counter changes. Leave the phone showing the same fullscreen grid.
4. **Touch only the car screen** during this phase. Tap the glowing targets **1 → 4 → 9 → 12 → 6**, then try a drag and two fingers. Matching cells advance the target; the logged offsets measure how close the event was to the intended crosshair. Completion confirms cell hits, not pixel-perfect calibration.
5. Look for magenta/cyan marks, a rising event count, and target advancement on the phone and car. A short tap remains marked for up to four seconds, subject to the bounded trail history.
6. Tap **Logs** on the phone to hide the grid and open logs while USB streaming continues. Use **Stop connection** to disconnect. If you touched only the car, tap **Record: I touched the car screen only**. That records your origin confirmation separately from device metadata.
7. Tap **Copy all logs**, **Share full log**, or **Save full log**. All three operate on the full retained log, including connection setup, not just the recent preview.

If nothing reacts on the car, tap **Logs** and save/share the log anyway. Include whether the phone grid still worked, whether the video counter kept changing, whether any markers appeared, whether the phone left the app, and whether USB debugging was already enabled. This app does not require you to enable it for the test.

The generated stream is now owned by a foreground service and is designed to continue when the app is hidden or the phone is locked. This needs confirmation on the actual phone/head unit. After a connection failure, use **Retry connection** first. If the car no longer accepts a new session, leave and reopen Auto+ Show on the car, then retry; reconnect the cable if it remains stuck.

## Retry without unplugging (0.3.2)

**Retry connection (keep cable plugged in)** on the log screen, or **Retry** on the phone grid, requests one new session on the existing accessory. It also works after a failure; the old persistent “physical reconnect required” flag is now advisory rather than a blanket block.

For a running session, Retry requests a graceful stop first. The termination message now matches the observed Garmin shutdown frame: service 1/type 4 with its zero-filled 61-byte parameter buffer. Earlier probes sent an empty termination payload. A completed termination write is logged, but does not establish a head-unit acknowledgement.

Before reopening, the app checks that the old descriptor cleanup has completed and the USB reader, writer and encoder have exited, then allows at least 750 ms settling time. The UI waits up to eight seconds for this cleanup and makes one fresh handshake attempt. It cancels a queued retry if you hide the phone UI or press Stop, and does not run an automatic reconnect loop. Existing car-grid target progress is retained.

A native operation that ignores cancellation must finish before another session can open. If cleanup remains stuck, the app reports that rather than overlapping old/new USB sessions. If the host no longer lists the accessory or refuses a fresh handshake, leave/reopen Auto+ Show on the car and retry. A physical reconnect remains the fallback: a normal Android accessory app has no public API to force the car's USB host to re-enumerate the phone or reset its firmware state. Successful soft recovery on this head unit still needs the field test.

Useful records: SOFT_RETRY_REQUESTED, SESSION_TERMINATE_BEGIN, SESSION_TERMINATE_SENT, CLOSED, SOFT_RETRY_OPEN, SOFT_RETRY_BLOCKED, SOFT_RETRY_CANCELLED, USB_OPEN and VIDEO_READY. A new VIDEO_READY plus an advancing visible car counter establishes recovery; opening a file descriptor alone does not.

## Fix based on the supplied 0.3.0 log

All three 0.3.0 streams in the supplied log were ended by our own 15-second receive-silence watchdog, while outgoing video was still advancing. Version 0.3.1 removes that unsupported heartbeat assumption. Missing initial VIDEO_READY, missing first video output, stalled video output, blocked USB writes, USB errors/detach and reported motion/unknown safety fields still stop the test. Incoming silence during advancing video is logged as RX_QUIET rather than treated as a disconnect. Completed writes still do not prove visual display or fresh safety telemetry.

The parked run reported restricted=0, speed=0 and an external touchscreen was registered, but no external touch events reached the grid. This rules out an active reported restriction in that run; it does not identify whether the car sends HID reports, Android routes them, or the car's ADB helper works. All supplied sessions used passive input observation; none sent the optional ADB setup request. Phone debugging was enabled, but that alone does not establish car authorization.

Additional logs now record external-device enabled state and axis ranges, activity-window focus/display ID, and motion events entering the activity before view routing (WINDOW_INPUT). These use public APIs and observe only this app's window, not global/background input. Device registration/capabilities alone do not prove actual HID reports. No new permission or input injection is added.

For the next test, keep the phone grid open while parked, tap only the car, then leave the stream in the background for at least one minute and export the full log. An advancing car counter after the old 15-second timeout verifies the watchdog fix on this head unit. If it stops again, the recorded reason should distinguish a real transport/output failure from this now-removed timeout.

## Background and screen-off test

1. Stop any existing stream before updating, then use **Retry connection** and confirm the car grid counter changes. If needed, reopen Auto+ Show on the car first.
2. Allow notifications when asked so stream controls are available outside the app. Denying notifications does not prevent starting the service; use the app or Android active-app controls to stop it.
3. Press **Background** or Home and open another phone app. Check that the car counter continues and the generated card says **BACKGROUND DISPLAY TEST**.
4. Lock the phone with its power button. Check that the counter still advances. This streams only our generated display; it does not capture the lock screen or other phone apps.
5. Unlock and tap **Open grid** in the stream notification, or **Resume car touch grid** in the app. The same USB session and target progress are retained. Test touch only while parked with the phone grid visible.
6. Use the notification **Share log** action or app log controls to export the complete log without disconnecting. Look for `FOREGROUND_STREAM_STARTED`, `UI_BACKGROUND`, `PHONE_SCREEN_OFF`, `PHONE_SCREEN_ON`, `PHONE_UNLOCKED` and `UI_GRID_RESUMED`.
7. Tap **Stop** in the notification or **Stop connection** in the app to end the stream and release the wake lock.

The service keeps the CPU available while streaming but does not keep the phone screen on. Unplugging, USB errors, reported motion/unknown safety state, force-stop or OS termination can still end it. The service does not automatically restart after termination. OEM battery behavior and actual screen-off operation require the field test above.

While the grid is hidden, its touch collection is disabled and targets are not highlighted. Native car input may be routed by Android to another foreground app; do not test car touch in this state. This version neither captures background input nor adds global input injection. A future Android Auto receiver would need its own video/input integration; whole-phone screen mirroring is not implemented.

## Logs available during the grid

A phone-only toolbar sits over the grid header with five controls:

- **Copy logs** copies the full retained log and keeps the test running. It does not record a calibration touch.
- **Share logs** hides the grid, keeps the stream running, returns to the log screen and opens Android’s share chooser with a full `.txt` snapshot attached. Small snapshots also include the full text for apps that accept pasted text.
- **Logs** hides the grid, keeps the stream running and opens the main log/control screen, where **Copy all logs**, **Share full log** and **Save full log** are at the top.

- **Background** sends the phone UI to the background while the stream continues.
- **Retry** closes any existing session and tries a fresh connection with the cable attached, retaining the car-grid progress.

The toolbar is not rendered into the generated car video and does not resize the grid. It accounts for phone cutouts/system bars. The same normalized touch geometry is retained.

The visible log preview keeps the latest 180 records; exports include **all retained records** in the persistent file. New versions no longer truncate that file at 512 KiB. Data already discarded by older builds cannot be recovered. The log is kept across app restarts and updates until Android app data is cleared/uninstalled.

Copying uses the Android clipboard. If the full log exceeds the 256 KiB text limit or Android refuses it, the app reports that and directs you to share/save the full file; it never silently copies only a tail. Sharing always attaches the complete snapshot and uses an explicitly granted, read-only content URI. The live log and other private files are not exposed by the provider. Copy, share and save capture immutable snapshots of the records available at that moment without stopping the stream. Subsequent stream events remain in the live log for the next export.

**Save full log** uses Android’s document picker and writes the same immutable snapshot. Clipboard or document-picker apps may impose their own size/format restrictions. No storage, overlay or clipboard permission is requested by this app.

## Optional ADB touch diagnosis

Use this only if you are comfortable granting the connected car debugging access. Authorization can give it broad access beyond this grid. For a rental car, avoid **Always allow from this computer** in Android’s authorization prompt, and disable debugging/revoke authorization after the experiment as appropriate.

Version 0.2.0 displayed the grid and accepted local touches but the user reported no car-screen response. Garmin Auto+ Show 1.3.5.1 and 1.3.4.1 also failed to provide touch on that same setup. Static inspection of both Garmin releases shows their HID test skipped with an ADB-override message; their setup path checks debugging and can send a USB `useAdb` request. Version 0.2.0 did not send that request.

1. Leave the advanced option off for a passive test; the full log includes phone model, debugging setting, USB state if available, and input-device snapshots.
2. If USB debugging is **already enabled**, return to the main controls and tick **Advanced: ask car to set up ADB touch**. Use **Retry connection** to start a new session with that option.
3. The app sends the observed service-1/type-8 message with integer payload 1 once during connection setup. It does not enable debugging or authorize the car itself. Handle any Android authorization prompt yourself.
4. Tap only the car grid again. Use **Logs** and **Save full log**, including the phone-debug and ADB-status lines. If no events arrive, that remains a negative result; an ADB status flag alone does not prove working touch injection.

If debugging is disabled or its state cannot be read, the optional request is skipped and the passive grid still runs. The app does not request a speculative HID-switch command, install a helper on the car or change firmware. The advanced option is off again when the control screen is rebuilt.

## What the log establishes

| Evidence | Interpretation |
| --- | --- |
| `FIRST_VIDEO_BUFFER_SENT` | A USB video write completed. Actual display still needs visual confirmation. |
| `TOUCH phase=PHONE` | Input received during the local baseline. |
| `TOUCH phase=CAR` | Input received during the car phase; origin still depends on touching only the car. |
| `device`, `external`, `virtual`, `source`, `tool`, `deviceId` | Android metadata that can help distinguish a built-in panel, USB input, mouse or injected input. It does not always establish physical origin. |
| `count`, `pointer`, `actionIndex` | Pointer and multi-touch behavior as delivered to our view. |
| `view`, `raw`, `norm` | View-local, Android raw-screen and normalized coordinates. |
| `inferredVideo` | A calculated position in the negotiated video frame, not a position reported directly by the car. |
| `cell`, `expected`, `deltaNorm` | Received cell, currently requested cell and normalized offset from its crosshair. |
| `PHONE_DEBUG_STATE`, `PHONE_USB_STATE` | Phone-side debugging/USB function observations. Neither establishes car authorization or successful input. |
| `ADB_SETUP_REQUEST_SENT` | The optional observed setup request was sent; input still needs verification. |
| `ADB_STATUS` | A head-unit status report, distinct from phone authorization and working injection. |
| `CAR_HID_PROBE_REQUEST` | A protocol request from the car, distinct from an observed Android input event. |
| `USER_CAR_ORIGIN_CONFIRMATION` | Your explicit report that you touched only the car during this phase. |

Move/hover log lines are limited to roughly 10 per second; the model still receives every event. Pointer counts and the final summary reflect received events, not just logged lines.

The full phone view and encoded frame use the same normalized grid, stretched to fill each screen without letterboxing. Shapes may have different proportions on the two screens. The app advertises the actual landscape phone-view dimensions in the device report so the head unit has the relevant input geometry. Mapping quirks are measured rather than silently corrected.

A working video path does not guarantee touch. Some head units use Android Open Accessory HID, others inject through ADB, and some provide no return input for this mode. This test observes the input already delivered to our app; the app itself does not inject input into other apps or turn on debugging. In advanced mode, the car may use an already-authorized ADB connection to deliver input. Device names are recorded for diagnosis, but device descriptors, accessory serial numbers and received car identifier payloads are omitted.

## Stationary restriction handling

The user’s successful display run was stopped by our earlier probe when the head unit reported `restricted=1, speed=0`. Version 0.3.1 keeps this generated stationary grid running for the observed restriction values 0 or 1 when reported speed is zero. It stops for nonzero/unknown speed or unknown restriction values. It sends no restriction-mask override and does not change car settings.

## Build and validation

Requires Python 3, JDK 17+ recommended, official Android SDK Platform 35 and Build-Tools 35.0.0:

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 build.py
# Pure Java tests only:
python3 build.py --tests-only
# Optional if adb is already configured on your computer:
adb install -r build/rental-display-probe-0.4.0.apk
```

No Gradle or third-party app libraries are needed. The script compiles Java/XML, creates DEX, aligns the APK, signs it with a local development key and verifies its signature/alignment. `build/local-debug.keystore` is not a production key; rebuilding with a different key requires uninstalling the previous APK before installing that build.

Tests cover full log retention above the old size limit, snapshot consistency, clipboard size handling, export-path confinement, wire framing, bounded independent USB writing, normalized target mapping across multiple screen aspect ratios, guided progress, pointer/event counts, immutable snapshots and bounded trails, phase reset, stationary-state decisions, service lifecycle transitions and preserving grid state while its UI is hidden. These do not replace Android runtime or hardware tests. Version 0.3.3 has not yet been tested on a physical phone/head unit here; foreground-service startup, notification controls, sharing, background/screen-off rendering, graceful stop/retry, orientation, input delivery and actual touch mapping need the field tests above.

Earlier investigation and experimental 0.1.x changes are retained in `HISTORY-through-0.1.2.md`. The later user-confirmed display on 0.1.1 established that the experimental Bluetooth exchange was not necessary for that successful run. Intermittent earlier USB stalls remain an open issue.

## Test settings and Android Auto check (0.4.0)

The test options moved to **Test settings & Android Auto check**. The safety reply (default on) and the Auto+ Show screen size are saved between launches; the ADB request stays off and applies to the current session only. The same screen has **Check Android Auto connection**, the first Android Auto hook-up step: it confirms the Android Auto app, connects to its developer head unit server on 127.0.0.1:5277 and exchanges AAP version messages (`AA_*` log lines). It stops before encryption. See `docs/android-auto-plan.md` for the remaining stages and the head-unit certificate decision.

## Car touch in the background (0.3.8)

Car touches from port 8051 no longer depend on the phone grid being visible. While the car's touch connection is open, the car video keeps showing the live grid ("CAR TOUCH ACTIVE - phone app in background"), targets keep advancing and marks keep appearing, even with the probe in the background or the phone screen off. Phone-window touches are still ignored while the phone grid is hidden. Whether the car keeps sending touch with the phone screen off or locked has not been tested yet.

## Status: car touch works (0.3.7, 2026-11-08)

With the safety-mask reply enabled, the car sent `a=init` and then `a=ts` touches to the probe on port 8051, and all five grid targets were hit from the car screen (see `FIELD-2026-11-08.md`). The car withholds touch until the phone answers its safety state; its own helper cannot inject on Android 14+, so the probe receives the coordinates directly.

## Auto+ Show parity options (0.3.7)

In the first 0.3.6 car run the car connected to the probe's port 8051 but sent no touch lines, the same as with its own helper. Two options test whether the car waits for something Auto+ Show sends:

- **Reply to car safety state like Auto+ Show** (on by default): when the car reports `restricted=0, speed=0`, send service 1 type 17 `(mask=0, allowed=1)`, which is what Auto+ Show sends in that state (`SAFETY_MASK_SENT`). Nothing is sent when the car reports restriction or motion.
- **Advertise screen size like Auto+ Show** (off by default): send the phone's full portrait real size (for example 1080×2340) in the session request and orientation report instead of the landscape grid view size.

`SESSION_OPTIONS` records the choice per session.

## Car touch on port 8051 (0.3.6)

The head unit delivers touch by pushing a helper (`usblinkts`) over ADB that listens on TCP 8051 and injects with the hidden `InputManager.getInstance()`. On Android 14+ that call throws in a bare `app_process`, so every touch is dropped (see `docs/usblinkts-helper.md`). On Android 14+ this version listens on `127.0.0.1:8051` from the moment the app starts. If it holds the port before the car starts its helper, the car's forwarded connection reaches the app instead: it sends the same `USBLINKTS v1.0` banner, logs every line, and maps `a=ts` touches onto the grid using the `a=init` car size (or the negotiated video size). Nothing is injected into other apps. INTERNET permission is added for the local socket only; the listener binds to loopback.

Log lines: `TS8051_LISTENING`, `TS8051_BIND_FAILED`, `TS8051_CONNECTED`, `TS8051_INIT`, `CAR_TOUCH_8051`, `TS8051_RX_UNKNOWN`, `TS8051_CLOSED`. Order matters: stop any running car helper (`pkill -f UsbLinkTS` as shell, or restart the phone), open this app, then plug in. Not yet run on the car.

## Contact-data hints (0.3.5)

`TOUCH` lines now also record `size`, `major` (touch-major), `precision`, `flags`, `meta` and a `profile` hint. Events injected through shell/API paths such as `input tap` typically report pressure 1.0, size 1.0 and no touch-major, whereas a phone panel reports varying values. `profile=SYNTHETIC_LIKE` means only "no panel-like contact data" (a simple HID touchscreen can look the same), `PANEL_LIKE` means real contact data was present, and `INCONCLUSIVE`/`N/A` cover everything else, including releases and hover. These are weak hints, not proof of origin. When the grid is left, `INPUT_TALLY` summarises primary touch-downs by `origin/profile`, for example `BUILT_IN/PANEL_LIKE=3, UNKNOWN_DEVICE/SYNTHETIC_LIKE=5`. The injected-tap control test (`adb shell input tap`) shows which profile and origin an injected tap gets on this phone, which is the baseline for judging car taps. Not run on a phone/head unit.

## Input attribution and ADB retry (0.3.4)

Static inspection of Auto+ Show 1.3.5.1 (see `docs/auto-show-decompile-1.3.5.1.md`) found no phone-side touch receiver: the app mirrors the phone screen with MediaProjection, hard-codes HID as unsupported, and gates on developer options, USB debugging and a car-reported ADB flag. If the car injects touch over ADB, Android may attribute the events to the built-in touchscreen or to no device, so earlier "no external events" results do not rule out car touches.

- `WINDOW_INPUT` (formerly `WINDOW_EXTERNAL_INPUT`) now logs every motion event reaching the activity during a car session, not just those from external devices. Each carries `origin=EXTERNAL|BUILT_IN|VIRTUAL|UNKNOWN_DEVICE`; `TOUCH` records carry the same label. `BUILT_IN` cannot separate a phone tap from an injected one, so keep touching only the car and use `USER_CAR_ORIGIN_CONFIRMATION`.
- `PHONE_DEVELOPER_OPTIONS` records `development_settings_enabled`, which the inspected Garmin client checks before its ADB step.
- In advanced mode, after the first video buffer the `useAdb` request is repeated every 2 s (at most 5 requests per session, `ADB_SETUP_REQUEST_REPEATED`) while the car has not reported ADB available, mirroring the inspected client's repeat. It stops once VIDEO_READY's seventh integer or an ADB-status message equals 1. Passive mode still sends nothing.

None of this has been run on a phone/head unit.

## Phone-only input inspection (0.3.3)

While parked, keep the phone unlocked and turn on Developer options → Pointer location / Show taps. First verify the overlay with a phone tap. Start the car grid and wait for visible video. Tap **Inspect** on the phone grid, then touch only the car for 20 seconds, watching the physical phone for pointer activity. The grid and stream stay active. After 20 seconds use **Share logs**. Repeat without changing ADB settings to establish the passive baseline.

**Inspect input · 20 seconds** on the main screen resumes the running grid. Start a baseline/car grid before inspection. The test records start/end markers, USB/debugging state and input-device capabilities. For the marked window it logs up to 128 complete incoming accessory messages with 64-byte payload prefixes (including normally quiet type 6); VIDEO_READY is limited to its first 28 bytes to omit its known identifier tail. Lengths/truncation and count of omitted messages are explicit. Unknown payloads may contain device information; review before sharing. This is protocol-message logging, not a bus capture: separate HID reports, ADB traffic, incomplete frames and host control transfers are not observable through the accessory stream.

The app also attempts `/system/bin/getevent -lt` for 15 seconds, then `getevent -lp` and `dumpsys input` with one-second command deadlines. Each command retains at most 24 KiB of merged output. These run under the app UID, not ADB shell; access is commonly denied on stock phones. Errors, exit codes, deadlines and truncation are logged. Empty output/permission denial cannot establish that no input was sent. Captured kernel events can include phone touches as well as external input. No root, pairing, security-setting changes or extra permission is requested.

**Phone-only inspection help** opens the instructions, links to the phone's Developer options, and copies commands for a separately paired on-device wireless ADB client. The app does not include an ADB pairing client or gain shell privileges. Run `dumpsys input`, `getevent -lp`, then `getevent -lt` in that client's shell; stop the live command with Ctrl+C. Kernel capture may remain denied even to shell. Framework device IDs are not `/dev/input/eventN` numbers; software-injected ADB input may bypass kernel events. Do not type private information during input capture.
