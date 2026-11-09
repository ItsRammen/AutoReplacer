# AutoReplacer on Open Headunit

Decision (2026-11-08): AutoReplacer becomes an Auto+ Show–focused derivative of Open Headunit. For now the upstream code lives in `openheadunit/` (unmodified import of `andreknieriem/open-headunit@ae74788`, AGPL v3, see `openheadunit/UPSTREAM.md`). The probe in `app/` remains the diagnostic tool. Later the work is replayed onto a GitHub fork of Open Headunit and published as AutoReplacer under the AGPL v3.

## What Open Headunit already provides

- AAP transport, version negotiation, TLS, service discovery, audio, input (`aap/`).
- Self-mode through Android Auto's developer head unit server on port 5277 (`connection/self/`).
- Video: Android Auto H.264 frames reach `videoDecoder.decode(...)` in `aap/AapVideo.kt`; 1280×720 is a supported resolution (`Settings.Resolution._1280x720`), which matches the car's `VIDEO_READY`.
- Touch: `AapProjectionActivity` maps view coordinates (`TouchCoordinateMapper`) and sends `commManager.send(TouchEvent(...))`.

## What AutoReplacer adds (ported from the probe)

1. **Garmin USB Link session** — accessory open, `USBL` header, session request, VIDEO_READY handling, keepalive, safety state with the type-17 reply, termination (`UsbSession`, `Wire`, `SerialWriter`, `AccessoryIo`, `DiagnosticPolicy`).
2. **Video sink** — forward Android Auto's H.264 (codec config, then frames) to the car as service 1 type 2, at the negotiated 1280×720, instead of or alongside local decoding. Needs checks that the car accepts Android Auto's stream framing as-is.
3. **Car touch** — the port-8051 bridge (`CarTouchBridge`, `TsProtocol`): car pixels (1280×720) → Android Auto video coordinates → `TouchEvent`, with no phone window involved.
4. **Lifecycle** — open on the car's accessory intent, start self-mode, and keep running with the phone UI in the background.

## Order of work

1. Build upstream unchanged (debug, `github` flavour) to confirm the toolchain.
2. Add an `autoreplacer` package with the Garmin session and the 8051 bridge, behind a setting, without touching the decode path.
3. Tap `AapVideo` frames into the Garmin video sink; test on the car with a fixed 1280×720 profile.
4. Route car touch into `TouchEvent`; test targets on the car.
5. Polish: auto-start on plug-in, notification, settings screen limited to what Auto+ Show cars need.

## Status (2026-11-08): first car-ready build

Implemented in `openheadunit/` (package `autoshow`):

- `AutoShowLauncherActivity`: invisible; receives the Garmin accessory-attached intent and starts the service.
- `AutoShowService`: foreground service. Starts `CarTouchServer` first (so port 8051 is held before the car's helper starts), opens `GarminLink`, and on VIDEO_READY creates `CarEncoder`, sets Android Auto's resolution to match the car (720p for 1280×720), gives the encoder's input Surface to Open Headunit's decoder (`videoDecoder.setSurface`), then starts self mode without Open Headunit's projection screen (`ACTION_START_SELF_MODE` + `EXTRA_NO_UI`).
- `GarminLink`: probe's USB session, with the type-17 safety reply.
- `CarEncoder`: Surface-input H.264 baseline encoder; repeats the last frame after 250 ms idle so the car keeps getting video while Android Auto sends nothing.
- `CarTouchServer`: port 8051; car pixels → Android Auto video pixels → `TouchEvent` via `CommManager.send`.

Mode clean-up: Auto+ Show mode is the default (and, for a new install, only) connection mode, the other modes' code is kept and hidden by that default, and the unused root/Bluetooth-scan/advertise/storage permissions are removed.

Known risks, all untested on a phone and car:

1. Self mode may need Android Auto set up with developer settings enabled and Open Headunit's onboarding completed once.
2. If Open Headunit's own projection screen ever opens, it takes the decoder's Surface back and the car video will freeze.
3. A stale car helper from an earlier session holds port 8051 until the phone restarts; touch will then be unavailable (the notification says so).
4. Encoder settings (baseline, 1 s keyframes, ~2.8 Mbit/s at 720p) are copied from the probe, which the car accepted; Surface input and repeated frames are new.
5. The Android Auto stream is decoded and re-encoded, which adds latency and CPU use.

## Bluetooth (0.5.4)

Auto+ Show bonded with the car's Bluetooth address (carried in VIDEO_READY after 28 bytes of integers, a 6-byte and a 10-byte field: a 17-character `XX:XX:XX:XX:XX:XX`) and then connected the headset and A2DP profiles through hidden APIs. Those hidden calls need BLUETOOTH_PRIVILEGED, so a normal app cannot repeat them. AutoReplacer instead:

1. reads the address (`Wire.carBluetoothAddress`);
2. if Bluetooth is on and the Nearby devices permission is granted, starts pairing when the phone is not bonded to the car (`createBond`), or checks the bond;
3. watches for the car to connect media (A2DP) and call (headset) audio, rechecking for about 20 s, and shows the result on the notification's second line.

Not done: sending Auto+ Show's "Bluetooth setup" stage in the session request (could make the car show its pairing screen and delay video), connecting profiles ourselves, and anything that enables Bluetooth. Whether the car also needs the pairing before it will send touch is unknown; the first car run will show it.
