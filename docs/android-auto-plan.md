# Android Auto hook-up plan

Goal: run Android Auto's projection on the same phone, send its video to the car over Garmin USB Link, and feed car touch (port 8051) back as Android Auto touch input.

## Stage 1 — connectivity check (0.4.0, done in code, not yet run)

Test settings → **Check Android Auto connection**: checks the Android Auto package, connects to the developer head-unit server on `127.0.0.1:5277`, sends an AAP version request (control channel 0, flags 0x03, message 0x0001, version 1.1) and parses the version response (message 0x0002: major, minor, status). Raw bytes are logged (`AA_VERSION_RAW`) so framing mistakes are visible. It stops before encryption.

Before running: Android Auto → Settings → tap **Version** repeatedly until developer settings unlock → menu → **Start head unit server**. The server only accepts connections while it is started.

## Stage 2 — TLS and authentication (blocked on a decision)

After the version exchange, the head unit and phone run a TLS handshake inside AAP `SSL_HANDSHAKE` messages, and Android Auto expects the head unit to present a certificate it trusts. Open-source receivers (aasdk/OpenAuto, headunit, Open Headunit) do this with a certificate and private key that originate from Google's own head-unit tooling. Copying that key into this repository is a licensing and distribution question that needs a deliberate decision. Options:

1. Load a certificate/key file the user supplies at runtime (nothing embedded in the repo).
2. Build on an existing open-source receiver as a dependency and follow its licence.
3. Stop at Stage 1 and use phone-side controls only.

### Open Headunit as the base (checked 2026-11-08, commit ae74788)

- Licence: **GNU AGPL v3** (with Michael Reid's original GPLv3/Affero headunit notice). A derivative that is distributed must be released under the AGPL with source.
- It already implements what Stages 2–5 need: AAP framing and version negotiation (it announces 1.2; its tests use the same `00 03 00 08 00 02 00 01 00 07 00 00` response this probe parses), TLS, service discovery, H.264 video, touch reports, audio policy, and a **Headunit Server self-mode** that connects to Android Auto's developer server on port 5277 on the same phone, which is this project's setup.
- It ships head-unit certificate material in `app/src/main/res/raw/cert` and `privkey` (plus public root certificates). Reusing it means taking on the same provenance question it carries.
- Suggested shape: a fork of Open Headunit (AGPL) with two additions from this repo: a Garmin USB Link video sink (send Android Auto's H.264 to the car instead of, or as well as, decoding it on the phone) and the port-8051 car-touch input with the safety-mask reply. The probe stays as the diagnostic tool.

## Stage 3 — service discovery and video

Answer the service-discovery request with one video sink (H.264, 1280×720 or 800×480 to match the car's 1280×720 `VIDEO_READY`) plus input and minimal audio. Android Auto's H.264 can probably be forwarded to the car without re-encoding, since the car already accepts AVC from the probe; flow-control acknowledgements must be sent per frame.

## Stage 4 — touch

Car touch arrives as `a=ts x y b` in car pixels (1280×720). Scale to the negotiated Android Auto video size and send AAP input reports (touch action, pointer id, coordinates, timestamp). No injection or phone window is needed.

## Stage 5 — audio, focus, night mode, lifecycle

Media/guidance/system audio sinks or their omission, audio focus, navigation focus, and clean restart/stop.
