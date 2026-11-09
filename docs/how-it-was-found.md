# How this was found out

None of this protocol is documented. This page records how it was worked out, including the wrong turns, so you can judge how much to trust each claim. The notes with the raw evidence are in [`research/`](research).

## The starting question

Video from a phone to an Auto+ Show unit worked in an early probe, but touching the car screen did nothing. The question was whether the touch could be obtained at all, and how.

## Step 1: a probe app

A small diagnostic app (`app/`, the "Rental Display Probe", kept for reference) implemented the USB link from observation, drew a numbered grid on both the phone and the car, and logged every input event with where it came from. Its test plans are in [`research/probe-test-plans.md`](research/probe-test-plans.md).

## Step 2: static analysis of the Auto+ Show app

Three releases of Garmin's own phone app (1.1.5.1, 1.3.4.1, 1.3.5.1) were downloaded from public app stores, signature-checked, and decompiled with `jadx`. The finding: **the phone app contains no touch code at all.** It checks whether USB debugging is on, asks the car (service 1, type 8) to set up ADB, and the car does the rest. The comparison is in [`research/auto-show-decompile-1.3.5.1.md`](research/auto-show-decompile-1.3.5.1.md) and [`research/auto-show-version-comparison.md`](research/auto-show-version-comparison.md).

## Step 3: where does touch arrive on the phone?

The candidate paths were checked against Android's source ([`research/android-input-paths-aosp.md`](research/android-input-paths-aosp.md)):

- **USB HID touchscreen.** The phone log showed the car registering an external touchscreen (vendor/product `ffff`) every session, but no events from it ever reached an app. Not the path.
- **ADB injection.** The car's ADB host pushing and running a helper. Promising.
- **Bluetooth.** Auto+ Show uses it for audio only (its code has no HID).

One wrong assumption here: the first plan used touch *pressure* to tell injected taps from real ones. On the test Samsung every real tap reports pressure 1.00 too, so size and touch-major were used instead.

## Step 4: capturing the car's ADB traffic

With the phone's own on-device ADB (LADB, running as the shell user), the car's commands showed up in `logcat`: it ran `getprop ro.build.version.sdk`, pushed `usblinkts` and `usblinkts_service` to `/data/local/tmp`, made them executable, and ran the service. The files were pulled and decompiled ([`research/usblinkts-helper.md`](research/usblinkts-helper.md)):

- The helper listens on **TCP 8051** and reads text lines (`a=init …`, `a=ts …`).
- It injects through the hidden `InputManager.getInstance()`, which on Android 14+ needs an `Application` that a bare `app_process` does not have. It throws a `NullPointerException` on every touch: **the car sends touches and the phone drops them**.

That explained the failure, and it also suggested the fix: **be the thing listening on 8051**.

## Step 5: binding port 8051

The probe bound `127.0.0.1:8051` before the car's helper started, so the car's forwarded connection reached the probe. In the **first** car run the car connected but sent no touch lines at all, the same as it had with its own helper. The decompiled Garmin app showed one more thing it does: it **answers every safety-state message from the car with a type-17 reply**. The probe never did. With a reply added (`mask=0, allowed=1`, sent only when the car reports no restriction and no motion), the car sent `a=init` and then `a=ts` lines, and all five grid targets were hit from the car screen. That was the first time car touch reached an app.

That run suggested that the car withholds touch until the phone answers its safety state. Later logs from AutoReplacer show touch also arriving in a few sessions without a fresh reply, so treat it as "this is what made it start working", not as an exact rule (see [Things we got wrong](#things-we-got-wrong-and-corrected)).

## Step 6: Android Auto on the phone

Open Headunit was chosen as the base because it already speaks the Android Auto protocol and has a loopback "self mode". The work was then to make it headless, aim it at a car screen, and feed it the car's video and touch. Field logs drove almost every fix:

| Symptom | Cause found in the log | Fix |
| --- | --- | --- |
| Blank car screen, link dropped after 15 s | `AapTransport.startReading` was never called without a projection screen | Start the message loop when the handshake completes |
| Android Auto drew a portrait canvas with margins | Geometry came from the phone's screen | `HeadUnitScreenConfig.carCanvas` |
| Home screen opened and closed the projection screen thousands of times a second | Two guards fighting over who owned the surface | Guard the home screen's raise as well |
| Android Auto "connected" but the handshake went unanswered | A connect-and-close **probe** of port 5277 (my own) used up the server's single accepted connection | Check by binding, never by connecting |
| After a reconnect the car stayed black although the decoder was rendering | `GraphicBufferSource: Dropping frame that's going backward in time` (timestamps restarted) | Rebuild the encoder when Android Auto's session restarts |
| Video sent before `VIDEO_READY` made the car reset its USB | Standby fed frames to a new link early | Drop buffers until the car is ready |
| A second reconnect showed a black screen | A stale waiting picture still sent to the old link | Always start a fresh one |

## Step 7: calibrating touch

Touches near the bottom row of Android Auto's screen only worked at the very top of each icon. A log showed taps in the bottom strip reporting y values of 740–795 on a 720-pixel picture, which the app was clamping. A guided calibration then recorded nine taps: `x measured = 1.034 × picture − 0.011`, `y measured = 1.276 × picture − 0.146`. Un-mapping with that fixed it.

## Things we got wrong and corrected

These are kept because they show the limits of reasoning from a few logs:

- **The "restricted" flag.** The car's safety message carries a restriction flag. Early on it seemed that touch only worked when the flag was 0 at session start, and a "Locked by the car" status was added. A later log showed touch working with the flag at 1 and unanswered, so the status was removed. The flag changes in the logs while the speed field is always 0, including while driving. The honest summary: touch sometimes arrives under `restricted=1` and sometimes does not, and nothing seen on the phone explains the difference. The app logs the flag and does not override the car.
- **A standby that kept Android Auto alive.** The idea was to keep Android Auto's session across a USB drop. The log showed Android Auto ends its own session about 4 ms after the USB state changes, so the standby only keeps the app waiting; the real recovery is rebuilding the encoder and reconnecting.
- **The cable.** Many drops are `EIO` on the accessory streams. One log showed the link dying 14 seconds before the phone saw power or the accessory leave, and turning the cable round fixed it for a long stretch. That points at the connection or the car's USB handling; the phone cannot tell which.

## Method

The same loop each time: save a log from a real car run, read the app's event log first, build a table of sessions (start time, car state, touch or not, how it ended) with a short script, form a hypothesis, check it against **every** log not just the latest, change one thing, and record the result in [`research/field-notes.md`](research/field-notes.md). When the logs could not decide something, the app was changed to log what was missing (the drop traffic recorder, the USB power state, the raw touch bytes).

## Where the evidence lives

| File | What it holds |
| --- | --- |
| [`research/field-notes.md`](research/field-notes.md) | Dated results from real car runs, newest last |
| [`research/touch-investigation.md`](research/touch-investigation.md), [`research/log-analysis-2026-11-07.md`](research/log-analysis-2026-11-07.md) | Early touch investigation and log reading |
| [`research/auto-show-decompile-1.3.5.1.md`](research/auto-show-decompile-1.3.5.1.md), [`research/auto-show-version-comparison.md`](research/auto-show-version-comparison.md) | What the Auto+ Show app does and does not do |
| [`research/usblinkts-helper.md`](research/usblinkts-helper.md) | The car's touch helper and why it fails on Android 14+ |
| [`research/android-input-paths-aosp.md`](research/android-input-paths-aosp.md) | How each touch path looks to an app, from AOSP source |
| [`research/autoreplacer-on-openheadunit.md`](research/autoreplacer-on-openheadunit.md), [`research/android-auto-plan.md`](research/android-auto-plan.md) | Why Open Headunit, and the plan |
| [`research/probe-*.md`](research) | The probe app's history, tests and validation |
| [`openheadunit/UPSTREAM.md`](../openheadunit/UPSTREAM.md) | Every change made on top of the Open Headunit import |
| [`openheadunit/CHANGELOG-AUTOREPLACER.md`](../openheadunit/CHANGELOG-AUTOREPLACER.md) | User-facing change history |
