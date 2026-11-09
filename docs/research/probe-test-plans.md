# Parked touch test — Probe 0.3.7

## Run order for 0.3.7 (one car trip)

Before each probe run: car unplugged, LADB as shell `pkill -f UsbLinkTS`, open the probe, check `TS8051_LISTENING`, then plug in. Touch only the car for at least 30 s with the grid visible (do not swipe up on the phone), then Logs → Save full log.

1. **Run 1:** safety reply on (default), screen size off.
2. **Run 2:** safety reply on, **screen size on**.
3. **Run 3 (control, no probe):** force-stop the probe (Settings → Apps → Rental Display Probe → Force stop), `pkill -f UsbLinkTS`, plug in and choose **Auto+ Show**, finish its setup, touch the car for 30 s, then in LADB: `logcat -d -v time | grep -i usblinkts > /sdcard/Download/usblinkts-garmin.txt`.

Look for `TS8051_INIT` / `CAR_TOUCH_8051` in runs 1–2 and `a=init` / `a=ts` lines in run 3.

# Port-8051 bridge test (0.3.6)

## First: port-8051 bridge test (the likely fix)

1. Car unplugged. In LADB as shell (`id` shows uid 2000): `pkill -f UsbLinkTS` (or restart the phone).
2. Open Rental Display Probe and check the log shows `TS8051_LISTENING`. If it shows `TS8051_BIND_FAILED`, repeat step 1.
3. Plug in, choose the probe, start the car grid, wait for video.
4. Touch only the car: targets 1 → 4 → 9 → 12 → 6, a drag. Watch the car and phone grids.
5. Logs → Save full log. Look for `TS8051_CONNECTED`, `TS8051_INIT`, `CAR_TOUCH_8051`.
6. Optional cross-check in LADB: `ps -A | grep app_process` and `logcat -d | grep -i usblinkts | tail -20` (the car's helper should have failed to start or never received a connection).

The older plan below is still useful if the bridge sees no connection.

# Earlier plan — Probe 0.3.5

Goal: find out how car touch reaches the phone (USB HID, ADB injection, both, or neither) and where it stops. Park the car with the parking brake on. Do not operate the phone while driving.

Evidence behind this plan: `docs/auto-show-decompile-1.3.5.1.md` and `docs/android-input-paths-aosp.md`.

## Stage 0 — Mine the logs you already have (no car needed)

In the full log exported from an earlier run (0.3.0 or later):

1. Find the lines for the external touchscreens (IDs 28/29/30): `INPUT_DEVICE_*` and `EXTERNAL_INPUT_CAPABILITIES`. Note `device="…"`, `vendor=`, `product=`.
   - `vendor=65535 product=65535` and a blank or ` Touchscreen` name → the car's USB (AOA) HID touchscreen.
   - A real device name, or `getevent -il` later showing bus `0005` → Bluetooth or something else.
2. Pressure does not help on this phone: the Samsung panel reports exactly `1.00`. Use `size`, `major` and `precision` (0.3.5 and later): panel ≈0.02–0.03 / ≈2–3 / 3.79,1.75; injected 1.00 / 0 / 1.00,1.00.
3. Note `PHONE_USB_STATE` (`adb=`, `accessory=`), `PHONE_DEBUG_STATE`, and any `ADB_STATUS` / `CAR_HID_PROBE_REQUEST`.

## Stage 1 — Phone baselines (home, on Wi-Fi, no car)

Set up Wireless debugging and an on-device client (Termux with `android-tools`, or LADB), pair, and check `adb shell echo ok`.

1. **Finger baseline:** tap **Phone input check (no USB)**, hit targets 1 → 4 → 9 → 12 → 6, drag, two fingers. Expect `origin=BUILT_IN profile=PANEL_LIKE`. Save the full log as `1-finger`.
2. **Injected control:** open the phone grid. Read `PHONE_VIEW: W×H`. In Termux run `sleep 10; adb shell input tap X Y` (cell 1: X = 0.152·W, Y = 0.333·H) and switch to the grid before it fires. Expect the grid to advance, pressure 1.00 down / 0.00 up, size 1.00. Note the `origin` it gets. Save as `2-injected`.
3. **Optional HID control:** plug in a USB or Bluetooth mouse and click the grid. Expect `origin=EXTERNAL`. Save as `3-hid`.
4. `adb shell getevent -il > devices-before.txt` (list of devices with no car attached).

If 1 or 2 does not register, fix the probe before trusting any car result.

## Stage 2 — Set the variables before plugging into the car

- Phone unlocked, screen on, charged.
- **Developer options on and USB debugging on first, then unplug and replug the cable** (Garmin's own setup order; the USB configuration is chosen when the accessory session starts).
- Pair and connect the phone with the car by Bluetooth (Auto+ Show requires this); note whether it is connected.
- Developer options → Pointer location and Show taps on. Confirm with a phone tap.
- Termux ready, still on Wi-Fi. Do not tick **Always allow** for the car's key unless Stage 5 says so.

## Stage 3 — Run A: passive (Advanced box unticked)

1. In Termux start captures:
   - `adb shell getevent -lt > getevent-A.txt &`
   - `adb logcat -v time > logcat-A.txt &`
2. Plug in. Choose **Rental Display Probe → Just once**, allow USB access, select Auto+ Show on the car, and start the car grid. Wait for the counter to advance.
3. Watch the phone: did an **"Allow USB debugging?"** prompt appear? If it did, choose **Allow** (one-time) and note when.
4. Before touching, take snapshots in Termux:
   - `adb shell getevent -il > devices-A.txt`
   - `adb shell dumpsys input > input-A.txt`
   - `getprop | grep -iE "usb|adb" > adb-A.txt; dumpsys usb >> adb-A.txt` (`dumpsys adb` is not available on this Samsung build)
   - `cat /data/misc/adb/adb_keys >> adb-A.txt 2>&1; logcat -d | grep -iE "adbd|AdbDebugging|UsbDebugging" | tail -60 >> adb-A.txt`
   - `adb shell getprop sys.usb.config > usb-A.txt; adb shell getprop sys.usb.state >> usb-A.txt`
5. Tap **Inspect input · 20 seconds** on the phone grid. For 20 seconds **touch only the car**: 1 → 4 → 9 → 12 → 6, a drag, two fingers. Do not touch the phone. Watch whether Pointer location reacts.
6. Tap **Logs** → **Record: I touched the car screen only** → **Save full log** as `A-car-passive`. Stop the captures (`kill %1 %2`).

## Stage 4 — Run B: advanced ADB request

1. Unplug the cable. Tick **Advanced: ask car to set up ADB touch** (USB debugging already on). Replug so this is a fresh session; do not rely on Retry.
2. Accept the ADB prompt if it appears (note it). Repeat Stage 3 steps 1–6 with file suffix `B` and log name `B-car-adb`.

## Stage 5 — Only if A and B show no car taps anywhere

Change one variable at a time and repeat the touch routine, saving a separate log each time:

- **C1:** Bluetooth disconnected vs connected.
- **C2:** accept the ADB prompt with **Always allow** for the car's key (a one-time grant may not be honoured). Revoke it afterwards. Note that always-allow keys also expire after 7 days of disuse.
- **C3:** the original Auto+ Show app on the same phone and car, with the same getevent/dumpsys captures, to see whether its touch works at all.

## Stage 6 — Clean up

Developer options → Revoke USB debugging authorizations; turn off Wireless debugging, Pointer location and Show taps.

## What to send

The saved logs (`1-finger`, `2-injected`, optional `3-hid`, `A-car-passive`, `B-car-adb`, any C), the `*-A.txt` / `*-B.txt` capture files, and notes per run: did Pointer location react, did the grid advance, did the debugging prompt appear.

Review files before sharing: they can contain device names and other details.

## Reading the results

| What you see during car-only touches | Likely meaning |
| --- | --- |
| `getevent` shows events from a device with bus `0003` / vendor `ffff`; probe logs `WINDOW_INPUT origin=EXTERNAL` | USB HID delivered to the app. Check coordinate mapping with `deltaNorm`. |
| `getevent` shows those events but the probe logs nothing | Routing or focus: read `dumpsys input` for the device's display binding and `WINDOW_FOCUS`. |
| `getevent` silent; probe logs `TOUCH origin=BUILT_IN/UNKNOWN_DEVICE profile=SYNTHETIC_LIKE`, pressure 1.00/0.00 | ADB injection is working. |
| Pointer location reacts but the grid does not | The event reaches the display but not our window. |
| Nothing anywhere; the adbd/AdbDebugging logcat lines show no authorized USB key and no prompt appeared | ADB authorization is the blocker. |
| Nothing anywhere; key authorized; `sys.usb.config` includes `adb` | Head unit side, or a Samsung/Android 16 restriction. |
| Nothing anywhere; `sys.usb.config` lacks `adb` | ADB was not exposed on that USB session; redo Stage 2 ordering. |

A fully negative run does not prove the car sends nothing. Stock Android may deny some of these commands even to ADB shell; denial is inconclusive.
