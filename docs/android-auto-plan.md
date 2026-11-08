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

## Stage 3 — service discovery and video

Answer the service-discovery request with one video sink (H.264, 1280×720 or 800×480 to match the car's 1280×720 `VIDEO_READY`) plus input and minimal audio. Android Auto's H.264 can probably be forwarded to the car without re-encoding, since the car already accepts AVC from the probe; flow-control acknowledgements must be sent per frame.

## Stage 4 — touch

Car touch arrives as `a=ts x y b` in car pixels (1280×720). Scale to the negotiated Android Auto video size and send AAP input reports (touch action, pointer id, coordinates, timestamp). No injection or phone window is needed.

## Stage 5 — audio, focus, night mode, lifecycle

Media/guidance/system audio sinks or their omission, audio focus, navigation focus, and clean restart/stop.
