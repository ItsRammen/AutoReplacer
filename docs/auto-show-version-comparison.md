# Auto+ Show touch: comparison of three verified Garmin APKs

Investigated 2026-11-07. This is static analysis; none of these APKs was executed on a phone/head unit here. No probe-app change is part of this investigation.

## Result

An older original 1.1.5.1 APK was successfully downloaded and its Android signature verified. It uses the same Garmin signing certificate as the already inspected 1.3.4.1 and 1.3.5.1 releases. The older release has the same explicit HID-test override and ADB setup mechanism. This weakens the hypothesis that a recent app update removed a different working HID implementation. It does not rule out target-SDK compatibility changes, unrelated regressions, or a car/phone interaction that differs between versions.

## Version comparison

| APK | Version code | Minimum / target Android API | HID setup in initial checks | ADB setup request |
| --- | --- | --- | --- | --- |
| 1.1.5.1 | 1151000 | 19 / 26 | Test marked done; HID-supported false; callback skips test | Service 1, type 8, big-endian integer 1 |
| 1.3.4.1 | 1341000 | 21 / 33 | Same behavior | Same request |
| 1.3.5.1 | 1351000 | 21 / 35 | Same behavior | Same request |

Package for all three: com.garmin.android.apps.autoplusshow.

Signer certificate SHA-256 for all three:

`6e48a76cf2d8f938d409c048a60455dabab2471eff39acc8887976e8359ff340`

The old APK is 11,047,955 bytes. Its SHA-256 is:

`f6a564d9a833e5b2c11fd45bce633c858005a7954f00f28abe5dbf2b1c257ba0`

This matches the archive's published file hash. Signature matching establishes continuity with the newer Garmin-signed APKs, not a general security audit or Android 16 compatibility guarantee.

## Input path traced

In the 1.1.5.1 main activity, the initial-setup method y() sets its HID-test-done field G=true and HID-supported field D=false before deciding between input paths. The car HID-test callback a(int,int) does not run a test; it records the same AdbOverride diagnostic string found in both newer releases. The remaining onTouchEvent code records an initial test coordinate; it is not an implementation that reads raw USB touch packets or injects events into other apps.

The older method E() checks the phone debugging setting and the app's car-reported ADB-available flag. When either is missing, it displays debugging/authorization setup and can request the core service's ADB setup operation, rate-limited to two seconds. That core service reaches the USB transport's service-1/type-8 request with integer 1. The newer initial checks a1(), HID callback h(int,int), and ADB setup e1() follow the equivalent path. The older and newer paths require Bluetooth setup before completing normal launcher setup; our successful video test does not establish that Bluetooth is irrelevant to every head-unit input mode.

The activity's ADB-available flag is initialized from the seventh integer in VIDEO_READY and updated by the car's type-11 status message. The app does not establish successful injection by observing a real car-origin tap in this path. Thus ADB_STATUS=1 is not an end-to-end touch test, and a head unit could report ready while its input implementation is failing.

The inspected Java input paths do not contain a phone-side replacement input-injection engine to transplant into our ordinary app. No car-side ADB input executable/source was recovered from these APKs. A legacy head-unit helper using ADB is a plausible compatibility boundary, but its actual commands/APIs and Android-16 failure mode remain unverified.

## Other protocol steps compared

The old and new USB transports have the same inspected outgoing message families: session/device request (1), video (2), orientation (3), termination (4), alive update (5), ADB request (8), phone-screen state (16), and safety-mask state (17). Received type 8 is a HID-test request with two integer coordinates; it is not a general stream of car touch events for the app to decode. The recent app's callback ignores it, just as 1.1.5.1 does. No separate HID-enable control message was identified in these inspected transport paths.

Session request stage is used for setup/launcher states: 1 for phone setup, 2 for Bluetooth setup, then 0 for projection. Our probe uses stage 0. The APK evidence does not show that sending those setup stages enables HID. Phone screen-off/on changes report type 16 values 0/1 in the original app; our probe does not currently send those state messages. This is a possible controlled protocol-comparison variable, not an established touch fix, and it cannot by itself explain failure while the phone grid is visibly open. No safety-mask override is proposed or added.

## Relation to the supplied phone log

The phone is Samsung SM-S9480, Android API 36. In the final 0.3.0 test, the head unit reports restricted=0, speed=0 and Android registers external touchscreen device 30 with source 0x1002. No external touch event reaches the grid. Earlier touch records identify the built-in sec_touchscreen instead. This is evidence of device registration, not proof that the head unit sends valid HID reports or that Android routes them correctly.

All supplied sessions use passive observation and none logs ADB_SETUP_REQUEST_SENT. The existing advanced option in our probe can explicitly send the observed request when phone debugging is already enabled. It does not enable debugging or grant car authorization, and this static comparison does not establish that the option will repair touch. The original app also reporting failed touch is evidence against assuming a single missing probe message is the whole problem.

## What would distinguish the remaining causes

1. Use probe 0.3.2 with phone grid visible while parked. External axis/capability and WINDOW_INPUT logs can distinguish events arriving at the activity but missing the grid from events never reaching the app window.
2. If needed, make a separately selected ADB-setup test using the existing advanced control and compare actual car-origin events. Record whether Android ever requests authorization; the status flag alone is insufficient. Authorization remains an explicit user action.
3. If no app-window events arrive, Android system input diagnostics during car taps could distinguish missing HID reports from input mapping/routing. A normal app cannot read arbitrary /dev/input devices or global input via ordinary runtime permissions.
4. An older-phone comparison on the same car would help separate phone/OS compatibility from head-unit failure. Installing the old app on the same Android 16 phone is a secondary control, not an expected repair: its inspected input architecture is the same, though target SDK and other behavior differ.
5. A confirmed head-unit ADB-helper incompatibility would require its code/firmware or a separate supported return-input path. It cannot be assumed repairable by downgrading only the phone APK.

## Reproducible evidence locations

Local analysis copies (temporary; not distributed as rewritten Garmin source):

- /tmp/autoplus-115-activity.java: initial checks at 838, HID callback at 922, ADB setup at 346, VIDEO_READY flag assignment in the instruction dump near 953.
- /tmp/autoplus-115-decoded/sources/com/garmin/android/apps/garminusblinkserver/service/a.java: incoming HID-test parser at 183, ADB setup sender at 319.
- /tmp/autoplus-134-activity.java: initial input checks near 846, HID callback near 1380.
- /tmp/autoplus-134-transport.java: ADB request near 339.
- /tmp/autoplus-decoded/sources/com/garmin/android/apps/garminusblinkserver/GarminUsbLinkServerActivity.java: initial checks near 769, ADB request near 886, HID callback near 1354, VIDEO_READY flag assignment near 1387.
- /tmp/autoplus-decoded/sources/com/garmin/android/apps/garminusblinkserver/service/a.java: ADB request near 339.

Decompilation can misrepresent complex control flow; flag assignments in the VIDEO_READY callback were checked against the emitted instruction dump, rather than treating its decompiler placeholder as executable source.

## Sources and original download

- Older release archive: https://www.apkmirror.com/apk/garmin-corporation/auto-show/auto-show-1-1-5-1-release/auto-show-1-1-5-1-android-apk-download/
- Android Open Accessory 2.0 HID documentation: https://source.android.com/docs/core/interaction/accessories/aoa2
- Android input routing architecture: https://source.android.com/docs/core/display/multi_display/input-routing

Android's AOA documentation puts HID registration and reports on the accessory/USB-host side; they pass into Android's input system separately from the app's bulk data stream. This explains why adding a touch-packet parser to our video stream would not automatically collect AOA HID events. It does not prove which input mode the car is actually using for real taps.
