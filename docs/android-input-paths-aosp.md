# How each candidate touch path appears to an app — checked against AOSP source

Checked 2026-11-08 by reading AOSP (`frameworks/base`, `frameworks/native` at `main`) and the Android common kernel (`android14-6.1`). Source reading only; nothing was executed on a phone. Vendor kernels (Samsung) can differ from these branches.

## 1. Car registers an AOA HID touchscreen (USB)

- Android's framework does not handle it: `UsbDeviceManager.java` only denies the HID interface class for ordinary USB. AOA HID lives in the kernel gadget driver `drivers/usb/gadget/function/f_accessory.c`.
- The accessory sends the report descriptor and reports as control transfers (`ACCESSORY_SET_HID_REPORT_DESC`, `ACCESSORY_SEND_HID_EVENT`). The driver calls `hid_allocate_device()`, sets `bus = BUS_USB`, `vendor = product = HID_ANY_ID`, never sets a name, and feeds reports with `__hid_report_raw_event`. It ends up as a normal evdev device, so `getevent` sees it.
- Expected signature in our logs (derived, not observed): `external=true`, `vendorId`/`productId` of 65535 (the 32-bit `HID_ANY_ID` truncated to 16 bits), and an empty or near-empty name, plus a usage suffix such as ` Touchscreen` added by `hid-input.c`. `getevent -lp` should show bus `0003`. A Bluetooth HID device would show bus `0005` and a real name. The probe already logs name, vendor and product in `INPUT_DEVICE_*` and `EXTERNAL_INPUT_CAPABILITIES`.
- Display binding (`TouchInputMapper::findViewport`): a touchscreen has an associated display; if external it asks for the EXTERNAL viewport and, when none exists, **falls back to the INTERNAL (phone) display**. Touchscreens are `orientationAware` by default, so raw coordinates are interpreted in the display's natural orientation and rotated with the display. With a landscape car screen driving a phone held in landscape, axes may be transposed or stretched; the grid's `deltaNorm` would show it.
- `touch.wake` defaults to `isExternal()`, so such events are allowed to wake the screen. Whether the waking tap reaches a window was not verified.

## 2. Car injects over ADB (`input` command or similar)

`services/core/java/com/android/server/input/InputShellCommand.java` (the `input` tool now runs inside system_server):

- `tap`: DOWN with pressure **1.0**, UP with pressure **0.0**; every pointer has size **1.0**; precision 1.0/1.0; meta/edge/flags 0; touch-major never set (0); tool type finger for touchscreen source.
- Device ID is the **first device in `InputDevice.getDeviceIds()` that supports the requested source**, else 0. On a phone with a built-in touchscreen that is normally the built-in panel, so injected taps are attributed to e.g. `sec_touchscreen` and are not external.
- Display defaults to the default display for pointer sources.
- Consequence for our logs: a car-injected tap looks like `origin=BUILT_IN` with pressure 1.00 on down/move, 0.00 on up, size 1.00. A real panel reports a nonzero touch-major and a small size. **Field correction (2026-11-08): this Samsung panel reports pressure exactly 1.00, so pressure does not distinguish injection here; use size (panel ≈0.02–0.03 vs 1.00), touch-major (panel ≈2–3 vs 0) and precision (panel 3.79/1.75 vs 1.00/1.00).** This confirms the assumption behind `ContactProfile` (previously recalled from memory).
- Important for existing evidence: the earlier 0.3.0 log recorded 22 `TOUCH` lines from internal device 6 (`sec_touchscreen`) and they were read as phone touches. Pressure alone cannot settle it (see the field correction above), and 0.3.0 did not log size or touch-major, so that log cannot decide it.

## 3. Bluetooth

Auto+ Show 1.3.5.1 only connects HFP and A2DP (see `auto-show-decompile-1.3.5.1.md`). A Bluetooth HID device would show bus `0005` and a real name; the signature in section 1 identifies USB instead.

## 4. What an app can and cannot receive

All three paths end as `MotionEvent`s delivered to the window under the touch on the bound display. None is readable from a service without a window (see the earlier discussion of overlays and shell-privileged helpers).

## 5. Can our app claim the device or its display binding?

Checked against AOSP `main` (`packages/Shell/AndroidManifest.xml`, `InputManagerService.java`, `InputManager.java`).

- **Ordinary app: no.** The AOA HID device is a kernel-created evdev node, not a USB device our app can open (the phone is the USB device side, so the USB Host API does not apply). Apps cannot read `/dev/input` and cannot choose which display an input device is bound to.
- **Re-binding needs `ASSOCIATE_INPUT_DEVICE_TO_DISPLAY`.** It is a signature-level permission that the `shell` package holds. `InputManager.addUniqueIdAssociationByDescriptor(descriptor, displayUniqueId)` is `@hide`, `@TestApi` and behind `@FlaggedApi(FLAG_DEVICE_ASSOCIATIONS)`, so a shell-run helper could call it only if the flag is enabled in the phone's build (unverified for Samsung Android 16). Port-based associations are unlikely to help because gadget HID devices have no useful port.
- **Monitoring input needs `MONITOR_INPUT`, which the Shell manifest does not list.** This corrects an earlier suggestion that a shell helper might use `monitorGestureInput`; that looks unavailable to shell.
- **Raw reads from shell.** A shell-uid process can normally read `/dev/input/event*` (the `getevent` route). That would pick up HID reports below the window layer for the HID path only. Injected (`input tap`) events never appear there.
- **Injection path.** Events injected over ADB go to the default display; there is nothing to re-bind, so they still need a window on display 0.

## 6. ADB authorization for a car acting as the ADB host

From `services/core/java/com/android/server/adb/AdbDebuggingManager.java` and `Settings.java` (AOSP `main`).

- The first time a host key connects, the phone shows an "Allow USB debugging?" prompt. The user chooses one-time allow or "Always allow".
- A one-time allow lasts for that connection only, so the prompt returns on the next USB session.
- An "Always allow" key (`AdbKeyStore.isKeyAuthorized`) is auto-authorized only while the time since its last connection is within `ADB_ALLOWED_CONNECTION_TIME`, whose default `DEFAULT_ADB_ALLOWED_CONNECTION_TIME` is **604,800,000 ms (7 days)**. A value of 0 disables the expiry (the developer option for this is reported as "Disable adb authorization timeout" on Android 11+; its exact wording on Samsung was not checked).
- Consequences for a rental car or a car used rarely: a previously granted key can silently expire after a week, and without "Always allow" every session needs a fresh tap on the phone. The car cannot answer the prompt; if nobody accepts it the car's ADB connection stays unauthorized even while the car's ADB status reads 1 and `adb_enabled` is 1.
- Not verified: how Samsung One UI, Auto Blocker or an unlocked/locked screen affects whether the prompt appears.
- The tension to note: avoiding "Always allow" on a rental car is safer, but it means touch cannot work until the prompt is accepted each time.

## Correction to an earlier note

An earlier message said `InputDevice.isExternal()` and `isEnabled()` are both API 29+. Checked against SDK jars: `isEnabled()` is public from API 28; `isExternal()` is public from API 29 and absent from the API 28 jar (hidden-API surface on 26–28).

## Sources

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/input/InputShellCommand.java
- https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/inputflinger/reader/mapper/TouchInputMapper.cpp
- https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/drivers/usb/gadget/function/f_accessory.c
- https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/drivers/hid/hid-input.c
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/InputDevice.java
