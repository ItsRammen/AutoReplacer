# The car's touch helper (`usblinkts`) and why it fails on Android 14+

Observed 2026-11-08 on Samsung SM-S9480 (Android 16) with LADB running as shell; helper decompiled with jadx 1.5.1. The car pushes the helper; it is not part of the Auto+ Show APK.

## How car touch is delivered

1. On USB connect the head unit (ADB key comment `unknown@garmin-sta1295-lawrence-lxtw19`, saved as Always allow) runs `getprop ro.build.version.sdk`, pushes two files, `chmod 0777`s them and runs `/data/local/tmp/usblinkts_service`.
2. `usblinkts_service` is a two-line script:
   `export CLASSPATH=/data/local/tmp/usblinkts.bin` / `exec app_process /data/local/tmp com.garmin.android.apps.usblink.UsbLinkTS`
3. `usblinkts.bin` is a 4,718-byte JAR (SHA-256 `1db0260f92bf7dc22d626013421c5b6c72f1f03aa96c02fa156c8befb8624078`, classes dated 2017-04-14) with five classes. It runs as uid 2000 (shell).
4. `UsbLinkServerThread` listens on **TCP 8051** (`new ServerSocket(8051)`, all interfaces), writes `USBLINKTS v1.0`, then reads text lines `key=value` separated by spaces:
   - `a=init sw=<phone w> sh=<phone h> cw=<car w> ch=<car h>`
   - `a=ts x=<car x> y=<car y> b=<action>` (or the same without `a=`), where `b` is the MotionEvent action (0 down, 1 up; moves presumably 2)
   Each line is logged with `java.util.logging` tag `usblinkts` (visible in logcat).
5. In the observed session the car connected through the ADB forward: `socket accepted! Socket[address=/127.0.0.1,port=55331,localPort=8051]`, then `handleUsbLinkTS()`.
6. `onClientTouch` scales by `sw/cw`, `sh/ch` (written as integer division in the bytecode, so the scale is truncated) and calls `TSEventInput.injectEvent`, which builds `MotionEvent.obtain(when, when, b, x, y, 1.0, 1.0, 0, 1.0, 1.0, deviceId 0, 0)`, sets source `0x1002`, and calls `InputManager.injectInputEvent(event, 0)` through reflection.

## Root cause on Android 14 and later

`TSEventInput` obtains the InputManager with reflection on the hidden `InputManager.getInstance()`:

- Android 13 (`android-13.0.0_r1`): `getInstance()` builds a singleton from `ServiceManager.getServiceOrThrow(Context.INPUT_SERVICE)`. This works in an `app_process` command-line program.
- Android 14 (`android-14.0.0_r1`) and current `main`: `getInstance()` is `Objects.requireNonNull(ActivityThread.currentApplication()).getSystemService(InputManager.class)`. A bare `app_process` program has no Application, so `currentApplication()` is null and the call throws.

The constructor catches the exception, leaving both `im` and `injectInputEventMethod` null. Every later `injectEvent` throws a NullPointerException, which is caught and printed to the helper's stdout/stderr (returned to the car over ADB, not to logcat). Result: the car is authorized, the helper runs and receives coordinates, and no touch is ever injected on Android 14+. This is independent of the Auto+ Show phone-app version, which matches the reports that newer and older Garmin APKs both fail.

Not yet observed on the phone: the `usblinkts` logcat lines for real car taps (`a=init` / `a=ts`), and the stack traces. Both are checkable (see below).

## Consequences

- A working fix only needs a different way to obtain the input service, e.g. `InputManagerGlobal.getInstance()` on API 34+ (the route scrcpy adopted for Android 14). That needs code running as shell, and the car re-pushes its own JAR every connection.
- **Simpler: our app can be the port-8051 endpoint.** If the probe app is already listening on TCP 8051 when the car starts its helper, the helper's `ServerSocket(8051)` fails, its thread exits, and the car's forwarded connection reaches our app instead. The app then receives the car's raw coordinates and the init dimensions directly, with no injection, window, overlay or shell helper. They can be mapped straight into Android Auto touch messages. Requirements: the INTERNET permission, binding before the helper starts (about 5 s after plug-in), and the car's ADB key still authorized. Unverified: that the car sends `a=init`/`a=ts` lines as expected and reconnects or tolerates the different endpoint.

## Checks to run

Home, car disconnected, LADB as shell (`id` shows uid 2000):
```sh
ps -A | grep app_process
CLASSPATH=/data/local/tmp/usblinkts.bin app_process /data/local/tmp com.garmin.android.apps.usblink.UsbLinkTS > /sdcard/Download/uts-test.txt 2>&1 &
sleep 2
printf 'a=init sw=1080 sh=2340 cw=1080 ch=2340\na=ts x=540 y=1200 b=0\na=ts x=540 y=1200 b=1\n' | nc -q 1 127.0.0.1 8051
sleep 1; pkill -f UsbLinkTS
cat /sdcard/Download/uts-test.txt
```
Expected on Android 16: a NullPointerException mentioning `requireNonNull` at start-up and on each `injectEvent`, with no tap on screen.

In the car, after touching only the car screen for 20 s:
```sh
logcat -d -v time | grep -i usblinkts > /sdcard/Download/usblinkts-car.txt
```
`a=init` and `x=… y=… b=…` lines would confirm the car is sending touch to the helper.
