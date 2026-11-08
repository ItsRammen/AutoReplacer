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
- Consequence for our logs: a car-injected tap looks like `origin=BUILT_IN` with pressure 1.00 on down/move, 0.00 on up, size 1.00. A real panel reports fractional pressure and a nonzero touch-major. This confirms the assumption behind `ContactProfile` (previously recalled from memory).
- Important for existing evidence: the earlier 0.3.0 log recorded 22 `TOUCH` lines from internal device 6 (`sec_touchscreen`) and they were read as phone touches. Their `pressure=` field settles it: values of exactly 1.00 would fit injected taps, fractional values would fit the panel. That can be checked in the already-exported log without a new field test.

## 3. Bluetooth

Auto+ Show 1.3.5.1 only connects HFP and A2DP (see `auto-show-decompile-1.3.5.1.md`). A Bluetooth HID device would show bus `0005` and a real name; the signature in section 1 identifies USB instead.

## 4. What an app can and cannot receive

All three paths end as `MotionEvent`s delivered to the window under the touch on the bound display. None is readable from a service without a window (see the earlier discussion of overlays and shell-privileged helpers).

## Correction to an earlier note

An earlier message said `InputDevice.isExternal()` and `isEnabled()` are both API 29+. Checked against SDK jars: `isEnabled()` is public from API 28; `isExternal()` is public from API 29 and absent from the API 28 jar (hidden-API surface on 26–28).

## Sources

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/input/InputShellCommand.java
- https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/inputflinger/reader/mapper/TouchInputMapper.cpp
- https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/drivers/usb/gadget/function/f_accessory.c
- https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/drivers/hid/hid-input.c
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/InputDevice.java
