# Version 0.3.6 changes

- `TsProtocol` (parser for the car's 8051 text protocol) is covered by `tests/TsProtocolTest.java`; all 10 suites pass and the APK builds against android-35. `CarTouchBridge` (socket listener) is compiled but not exercised on a phone or the car yet.
- On Android 13 and older the bridge stays off so the car's own helper keeps working.

# Version 0.3.5 changes (unit-tested logic only; Android code not built or run here)

- `ContactProfile` and `ContactTally` are pure classes covered by `tests/ContactProfileTest.java`. The `MainActivity` logging edits were made without an Android SDK and have not been compiled against android.jar; build with `python3 build.py` before field use.
- The profile heuristic assumes injected taps report pressure/size 1.0 and no touch-major. This now matches AOSP `InputShellCommand` (see `docs/android-input-paths-aosp.md`), but is still unconfirmed on this phone; the injected-tap control test establishes the real baseline.

# Version 0.3.4 changes (unit-tested logic only; Android code not built or run here)

- Origin labelling and bounded `useAdb` repeat are pure classes covered by `tests/InputPolicyTest.java`. The Android wiring (`MainActivity`, `UsbSession`, `InputDiagnostics`) was edited without an Android SDK in this environment and has not been compiled against android.jar; build with `python3 build.py` before field use.

# Version 0.3.3 validation

Validated 2026-11-07.

- 179 assertions passed: 40 wire/negotiation, 12 bounded-writer, 75 grid/state, 19 log/export and 18 lifecycle/background-state and 9 watchdog regression checks, plus 6 bounded-command checks.
- New lifecycle checks cover duplicate starts, cancellation before opening USB, explicit stop reservation, retained target progress/dimensions while hidden, and ignored hidden-grid touch collection.
- Built with Eclipse Temurin JDK 21 and Android SDK Platform 35 / Build-Tools 35.0.0. APK v2/v3 signatures and ZIP alignment passed. The signing certificate matches previous supplied versions.
- Packaged version code 10, version name 0.3.3, minimum API 26, target API 35.
- Packaged non-exported service declares connectedDevice foreground type and stopWithTask=false. Permissions are FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, WAKE_LOCK and POST_NOTIFICATIONS. No network or screen-capture permission is requested.
- USB permission is checked before foreground-service startup. The service owns the session/encoder and a partial wake lock, with notification controls for Open grid, Share log and Stop. It does not restart automatically after OS termination.
- Static review: hiding/destroying the Activity no longer stops the stream. Service cleanup releases the wake lock and foreground notification after session completion, startup failure, destruction or an explicit-stop deadline. Screen off/on and unlock events are logged without opening UI.
- Sharing/saving snapshots and returning to logs retain the ongoing service. Hidden-grid touch collection is disabled; existing targets and advertised dimensions are retained when resumed.
- Packaged log provider remains non-exported with per-URI grants and read-only immutable snapshots. Full logs remain persistent, independent of the 180-record preview. Clipboard limit is 256 KiB; share text limit is 64 KiB with full-file attachment always supplied.
- No physical Android device/emulator or head unit was used for this revision. Foreground-service startup, notifications, screen-off streaming, OEM battery behavior, UI/share/document-picker flows and car input routing and actual same-cable recovery require phone/head-unit verification. JVM tests and packaging checks do not establish these runtime behaviors.
- Regression based on the supplied log: an hour of incoming silence with advancing video stays active, including the final background-run timing. Missing VIDEO_READY, no initial output and stalled output still fail; motion handling is unchanged. No real-time one-hour device test was performed.
- Touch diagnostics add external-device axis/enabled data, app-window external-event observations and window focus/display ID through public APIs, with no new permissions.
- New recovery checks verify the observed 61-byte zero-filled termination frame and subsequent frame alignment, and simulate a native writer that ignores interruption: shutdown must not be treated as completed worker termination until that task actually exits.
- Static review: Retry requests at most one reopen, checks old reader/writer/encoder completion plus descriptor-cleanup completion and a 750 ms settling interval in both the activity and service startup path, waits up to eight seconds, preserves car-grid progress, and cancels queued reopen on Stop/background. The prior persistent replug flag no longer prevents a completed-cleanup retry.
- Graceful Stop now sends the observed Garmin termination payload and permits up to one second before forced close. A successful write is not treated as a head-unit acknowledgement. Transport failures still close rather than attempting control writes over a failed pipe.
- Existing USB stalls/EIO and the missing car touch events remain unresolved. Motion/unknown-state stop behavior and head-unit restrictions are retained; no override is sent.

- New bounded-command checks cover stderr/permission-error preservation, nonzero exit status, draining output beyond the retained limit without blocking, retained-output bounds, live-process deadline reporting and termination time. These execute JVM fixture processes, not Android commands.
- Inspection runs fixed argv commands without a shell or privilege escalation, off the UI thread; each process has a deadline, output cap and forced cleanup. An application-owned 20-second window rejects duplicate starts and survives Activity recreation. The live kernel attempt has a 15-second deadline; device enumeration and framework dump attempts have one-second deadlines each. Local command denial, silence and timeout are not classified as absence of car input.
- During the inspection window, up to 128 incoming complete accessory messages receive bounded hex-prefix logging, including keepalives. VIDEO_READY's known identifier tail is omitted. This does not capture separate AOA HID control transfers, ADB USB traffic or partial/malformed frames. No global monitoring, root, wireless ADB pairing or new permission was added.
- Grid toolbar buttons narrowed to fit the additional Inspect action. Help provides Developer options navigation and copyable commands for a separately paired on-device ADB client. No physical UI, Samsung command access or car-side input was verified here.

APK SHA-256:

`58264f0189786d5a1ccec82c55b3cb23efc30cbbed08ba6b8ae72b6143bf8b58`
