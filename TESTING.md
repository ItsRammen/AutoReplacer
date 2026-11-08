# Tomorrow’s parked test — Probe 0.3.3

1. Stop the old app’s connection and install the new APK over it. Existing logs are retained.
2. Enable Developer options → Pointer location and Show taps. Keep the phone unlocked and screen on.
3. Open the phone baseline grid and tap once to confirm both the grid and Android’s pointer overlay react.
4. Connect to the car, select Auto+ Show, and start the car grid. Wait for an advancing visible car counter. Leave the advanced ADB setup option as it was for the first test.
5. On the phone grid tap Inspect. For the next 20 seconds, touch only the car: tap several places and drag. Watch the physical phone for pointer activity. The live kernel check attempts the first 15 seconds; accessory logging runs for the full 20.
6. After 20 seconds tap Logs, then Share full log. Report whether Android’s pointer overlay reacted, whether either grid reacted, and whether the counter kept advancing.

The inspection records input devices, bounded incoming accessory-message prefixes and local getevent/dumpsys results. Stock Android may deny those commands; denial or silence is inconclusive. This APK cannot intercept separate HID/ADB traffic and does not include wireless ADB pairing. Known VIDEO_READY identifiers are omitted, but other captured device details may remain; review before sharing.

If the local checks are denied, Phone-only inspection help explains the optional separately paired on-device wireless ADB client and copies these shell commands:

```sh
dumpsys input
getevent -lp
getevent -lt
```

Stop the live command with Ctrl+C. Do not type private information during capture. Kernel access may still be denied to ADB shell; ADB software-injected input may bypass kernel events.
