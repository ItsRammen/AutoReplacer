# Auto+ Show 1.3.5.1 — decompiled phone-side input findings

Investigated 2026-11-08 with jadx 1.5.1 on `com.garmin.android.apps.autoplusshow` 1.3.5.1 (APK SHA-256 `a89363b7dad49de3b8ffeccf5adec683e0e9246e22572bf29bf30c86ed4bc517`, obtained from Aptoide; signer SHA-256 `6e48a76cf2d8f938d409c048a60455dabab2471eff39acc8887976e8359ff340`, matching the earlier-verified Garmin certificate). Static analysis only; nothing was executed on a phone or head unit. jadx marked a few methods "decompiled incorrectly"; the VIDEO_READY flag assignment below was read from the bytecode dump.

## Result

The phone app contains no receiver for car touch. Input must be delivered to Android by the head unit.

- No AccessibilityService, uinput/UHID, InputManager injection, `su`, `Runtime.exec` or `ProcessBuilder`. `libsr.so` has no input/adb/socket symbols; `libyuvtorgb-lib.so` is a YUV converter.
- Video is `MediaProjection.createVirtualDisplay("GarminUSBLink", …)` of the phone's own screen (`service/ScreenCaptureH264Thread.java`).
- `GarminUsbLinkServerActivity` sets `mHidTestDone = true; mHidSupported = false` unconditionally (~line 775) and its HID-test callback only logs `skipping … due to AdbOverride` (~line 1360). The car's type-8 message is parsed as two ints (`handleClientHidTest x=,y=`) and ignored.
- Because of those flags `passInitialSetupPermissionCheck` always takes the ADB branch. It needs: Bluetooth connected, developer options (`development_settings_enabled`), `adb_enabled == 1` and the car-reported ADB flag; then mock-location selection and default-launcher status.
- That car-reported flag is set to `(seventh VIDEO_READY int == 1)` and also set by a type-11 ADB-status message equal to 1. While it is false, the client re-sends `useAdb` (service 1, type 8, int 1) when more than 2 s have elapsed (`e1()`).
- Session request fields: `{realWidth, realHeight, densityDpi, 0, 1, 2, stage}`. Width/height come from `GarminUsbLinkServerApp.k()`, which is `Display.getRealSize()` (not `getSize()`), captured once in `Application.onCreate` in whatever orientation the app started in (normally portrait, so width < height). The app logs it as "Real Screen". The device report (service 1, type 3) repeats the same two values plus `Configuration.orientation` (1 portrait, 2 landscape) and is re-sent after a video frame whenever orientation changes. *Correction: an earlier version of this note said `getSize()`.*

## Implication

The Garmin design is a screen mirror whose touch is expected to be injected into the phone's default display by the head unit's ADB client. Injected events are commonly attributed to an existing touchscreen or to no device, so filtering on `InputDevice.isExternal()` would hide them. The external touchscreens registered in earlier logs show the car also registers HID at the OS level; the phone APK cannot say which path delivers real taps. What the car firmware actually runs is not visible from this APK.

Probe 0.3.4 responds by logging every window-level motion event with an origin label, recording `development_settings_enabled`, and repeating the opt-in `useAdb` request on the inspected client's cadence (bounded).

## Further findings (second pass)

- **Bluetooth is audio only in this app.** The head unit's Bluetooth address arrives in VIDEO_READY (byte arrays after the seven ints). `service/b.java` bonds with it and connects only the HEADSET (HFP) and A2DP profiles, via reflection. There is no `BluetoothHidDevice`/`BluetoothHidHost` code. Setup still blocks until the phone is paired and connected to the car (`f1()`), so the car may gate its input path on Bluetooth; the APK cannot say. The probe sends no Bluetooth exchange and ignores those byte arrays.
- **Geometry differs from the probe.** Garmin advertises natural-orientation real display size plus a separate orientation flag. Probe 0.3.5 advertises the landscape grid-view size (`PHONE_VIEW`) and sends orientation `2`. If the car's injector rotates by the orientation flag, landscape dimensions plus orientation 2 could be rotated twice, putting taps in the wrong place or off-screen. This is an untested hypothesis; the car's handling is not visible from the APK.
- **`libsr.so` is an environment check, not input code.** `EnvironmentCheck` (JNI `a/b/c`) reads the package signature and emulator/architecture properties; `onCreate` finishes the app if it fails.
- **Mock location is for car GPS.** `service/c.java` registers a test location provider and feeds locations to it (`addTestProvider`/`setTestProviderLocation`), which explains the `ACCESS_MOCK_LOCATION` and mock-location setup step. It is unrelated to touch.
- **No sockets or helper process.** No `Socket`, `LocalSocket`, `localhost` or `5555` references in Garmin's own code, so the phone side does not run or talk to an ADB client itself.
