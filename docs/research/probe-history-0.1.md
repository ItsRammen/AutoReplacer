# Rental Display Probe 0.1.2

A small, original Android test app for the Garmin USB Link accessory used by some Auto+ Show head units. It sends a generated color card with a moving marker. **This is a protocol experiment, not an Android Auto app.** No vehicle changes are required.

The app requests USB accessory access and Nearby devices (`BLUETOOTH_CONNECT`) access to read the phone's Bluetooth name and connection state for the car's setup exchange. The name is sent to the connected car over USB; names and addresses are omitted from logs. It has no Internet, microphone, storage or screen-capture permissions. No root, USB debugging, account or companion app is used. It does not bundle Garmin code, libraries, or Open Headunit.

## Test in the rental car

1. Park the car. Unplug USB and install `rental-display-probe-0.1.2.apk` on an Android 8.0+ phone. It can update the supplied earlier APKs without uninstalling. Android may ask you to allow installation from the app opening the APK. This is a development-signed test build.
2. Close Auto+ Show. If it automatically takes the USB connection, clear its defaults in **Settings → Apps → Auto+ Show → Open by default → Clear defaults** (wording varies by phone), then unplug. You can also temporarily uninstall it, but this is not required by the probe.
3. Pair/connect the phone to the car in Android's Bluetooth settings. Select **Auto+ Show** on the car's screen. Connect the phone to the car's media/data USB port with a data cable.
4. If Android asks which app should use the accessory, choose **Rental Display Probe → Just once** for this experiment. Allow USB access and **Nearby devices** if prompted. Otherwise open this app and tap **Connect & send test card**.
5. Keep the app open. It stops when you switch apps or lock the phone. A successful visual test shows color bars, “RENTAL DISPLAY PROBE,” a changing frame number and a moving marker on the car screen. Tap **Record: image visible on car** only if you see it there.
6. Tap **Stop test**, then **Save log** or **Share log**. Saving exports the local log; sharing sends the recent on-screen log to an app you choose. Include the car year, screen size/head-unit model if known, phone model, and whether the card appeared or colors were wrong. Raw car payloads and accessory serial numbers are omitted from logs.
7. Unplug after the test. Auto+ Show can be selected again on reconnect. No firmware or car settings are written by this app.

Bluetooth pairing may still be required by the car's firmware. The probe neither performs pairing nor requests USB debugging; if the head unit insists on them, save the result rather than changing developer settings for this test.

If video still stalls, unplug USB, tick **Connection test only (no picture)**, then reconnect and run once more. This mode does not start a codec or transmit video. `CONTROL_REPLY` establishes that the car accepted a control exchange independently of H.264; it does not establish visual output. Share both logs if you run both modes.

## Interpreting the result

| Log/status | Meaning |
| --- | --- |
| `NO_ACCESSORY` | Phone has not enumerated an Android USB accessory. Check data cable/port and car mode. |
| `UNSUPPORTED_ACCESSORY` | Accessory exists but its manufacturer/model does not match the known Garmin USB Link family. |
| `USB_OPEN` | Our package obtained USB ownership. Does not establish protocol compatibility. |
| `VIDEO_READY` | The car sent display parameters. This is meaningful handshake progress. |
| `FIRST_VIDEO_BUFFER_SENT` | A write returned successfully. Does not prove the car decoded or displayed it. |
| `USER_VISUAL_CONFIRMATION` | You reported seeing the generated card on the actual car screen. |
| Timeout / `VIDEO_NOT_READY` | The observed handshake is insufficient for this firmware or the display mode is not ready. |

Use **Just once** rather than changing USB defaults for an initial test. Do not run Auto+ Show and the probe simultaneously: both request the same accessory.

## Build and verification

Requires Python 3, a JDK supporting `--release 8` (JDK 17+ recommended), Android SDK Platform 35 and Android SDK Build-Tools 35.0.0. Install SDK packages from Google's official Android SDK distribution, accepting its terms. No third-party app libraries or Gradle are needed.

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 build.py
# Framing/negotiation tests without the Android SDK:
python3 build.py --tests-only
# Optional, if adb is already configured on your own computer:
adb install -r build/rental-display-probe-0.1.2.apk
```

`build.py` runs protocol tests, compiles Java/XML, creates DEX, aligns and development-signs the APK, and verifies its signature and alignment. The generated debug key stays under `build/`; it is not a production signing key. Builds from different keys cannot update each other without uninstalling the previous build.

Build validation does not replace a phone/head-unit test. No physical Android device or compatible car is attached to the development environment.

## Scope and limits

- Handles the known Garmin accessory identity only. It does not force a USB mode switch on arbitrary cars.
- Sends a `USBL` preamble, a Bluetooth-name identification message (service 5/type 2), and a setup-stage-1 request using protocol 1.2 and synthetic 800×480 display information. Once a generated encoded buffer is available, sends stage 0 followed by video and an orientation report. In connection-only mode, stage 0 is followed by keepalives without video. Video mode sends keepalives after the first video buffer has been sent.
- Observed framing: big-endian 16-bit service, 16-bit message type, 32-bit payload length. Incoming service 2 includes VIDEO_READY, VIDEO_NOT_READY and keepalive replies; outgoing video uses service 1/type 2. Maximum message payload is 4 MiB minus eight bytes.
- AVC encoding 1 uses the phone's baseline H.264 encoder and generated YUV images, with codec configuration buffers included. Encoding 0 uses RGB888 test images and the observed source-stride header convention. Raw color order/stride assumptions are unverified on hardware. Other encoding values are logged and refused.
- Runs at 5 fps. Supports even display dimensions up to 1920×1080 subject to protocol payload limits and phone encoder capabilities. No audio or touch functionality is implemented.
- Unknown messages are logged without raw payloads. A reported restriction, nonzero speed or unknown safety flag stops the experiment. The Bluetooth exchange identifies the phone only: pairing, profile changes and Bluetooth restarts are left to the normal phone/car controls.
- A 15-second response timeout and write watchdog close stalled sessions. The app is intentionally limited to foreground use. After a failure, physically unplug USB before retrying; the app blocks retries on the same connection. `TX_BEGIN` identifies the exact transfer if the next write stalls.
- Some head units may require additional Bluetooth exchange, HID handling, setup-stage negotiation or package checks. These remain open compatibility questions.

## Next decision

If the card appears, the independent USB client path is established for that head unit. Next integrate an Android Auto receiver and validate rendering, input, audio, cold start and reconnect behavior. Android Auto self-mode startup restrictions remain a separate product risk; this app does not solve them.

## 0.1.1 changes and observed evidence

The first real-car report on Android API 36 showed that 0.1.0 opened the Garmin accessory, sent a session request, and received VIDEO_READY with 1280×720, dpi 80, encoding 1, protocol fields 1/2 and ADB status 1. Writes then stalled before the encoder started; repeated attempts on the same connection stalled at the opening header. The car remained on a setup-incomplete screen. This establishes partial interoperability, not displayed video.

Changes responding to that report:

- Start the encoder without a synchronous write in the receive handler. A single bounded outbound queue keeps reads independent of blocked writes.
- Send the first video buffer before the orientation report, matching the observed reference-client order. Defer pings until video has begun so they do not fill the outbound FIFO during setup.
- Prefer public Android 16 accessory-stream APIs when available. The SDK-35 build accesses these public methods reflectively. On older/OEM builds, retain the original accessory descriptor until session teardown instead of closing it immediately after duplicating it.
- Log the active write's service/type/length and completion time, encoder startup, and the seventh VIDEO_READY field as ADB status rather than generic flags. Do not log identifier strings in the response tail.
- Require a physical disconnect after failure to avoid repeatedly reopening a stalled session.

The remote cause of the first blocked write is not proven. Additional head-unit setup or Bluetooth exchange may still be required. ADB status 1 does not establish that ADB is required for displaying our own generated image, nor that HID touch is available.

## 0.1.2 changes and observed evidence

The user's 0.1.1 log establishes that Android 16 accessory streams opened, the car sent VIDEO_READY and ADB_STATUS=1, the Qualcomm encoder configured successfully, and the first 29-byte video write blocked. No video write completed. The payload is likely codec configuration; 0.1.2 now logs the actual encoder buffer flags to verify that classification. This write stall does not establish a decoder rejection.

The reference app also sends the local Bluetooth name over USB during setup, even if Bluetooth is already paired. Earlier probes omitted that exchange. Version 0.1.2 adds the observed name/CRLF message, redacted Bluetooth profile responses, an explicit setup-stage-1 to stage-0 transition, and a connection-only mode. These test the remaining setup hypothesis; they are not a confirmed fix for the car's setup-incomplete screen. The control-only mode helps distinguish a setup/transport stall from a media-content problem.
