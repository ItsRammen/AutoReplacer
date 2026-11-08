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
