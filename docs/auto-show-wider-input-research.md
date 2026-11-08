# Wider research: Android car mirroring and missing touch input

Researched 2026-11-07. Findings are based on primary vendor documentation, upstream project issues/fixes and first-person reports on project support sites. Similar symptoms are not proof of the same cause. No new app build or phone/car configuration change was made for this research.

## What our existing evidence says

On Samsung SM-S9480 / Android API 36, the supplied log shows the car registering an external touchscreen (source 0x1002), but no external-origin event reaching the grid. The final run reports restricted=0, speed=0. All supplied sessions use passive input observation. The optional observed Garmin ADB setup request has not been tested in those logs. Three verified Garmin APKs, including old 1.1.5.1, use the same inspected HID-test override/ADB setup architecture.

The background shutdowns recorded in 0.3.0 were our own receive-silence watchdog firing; that is fixed in 0.3.1 and included in 0.3.2. Reports about battery management in other apps do not replace that known explanation for these logged stops. Our generated grid is not an Android virtual display, so fixes for virtual-desktop routing cannot be transplanted blindly.

## Most relevant external findings

### 1. RustDesk: Samsung / One UI 8.5 / Android 16, sharing works but input cannot be enabled

Upstream issue #15723, opened 30 July 2026, describes a Samsung SM-A346M/N with a sideloaded RustDesk 1.4.9 APK. Screen sharing works, but its accessibility input service is unavailable under restricted-settings policy. Another user reports the same One UI version. The issue was converted to discussion #15725; closure is not evidence that a fix shipped.

Relevance: a direct modern Samsung/Android 16 example of separate video and input permission paths. Limitation: our probe does not use an accessibility service, so this restriction does not directly explain its missing external HID events. It is a deployment concern if we later choose an accessibility input backend. Installing RustDesk or its input component does not enable Garmin control.

Source: https://github.com/rustdesk/rustdesk/issues/15723

### 2. scrcpy: Android 16 touch failures with a hover-related workaround

Upstream issue #6895, opened 8 June 2026, reports touch clicks failing on a OnePlus running Android 16 while Back/Home shortcuts work. A second user on a Pixel 6a / Android 16 reports success with --no-mouse-hover. The first reporter says a new virtual display accepts input while the existing display does not. These are user reports, not a verified Samsung/Auto+ Show fix.

Relevance: a concrete example where connection/video success does not imply correct event type or routing. We cannot apply scrcpy's hover switch to the Garmin head unit from our video client, and ignoring hover after it reaches our activity would not repair events already rejected upstream.

Source: https://github.com/Genymobile/scrcpy/issues/6895

### 3. scrcpy: an actual merged fix for injecting onto the wrong display

PR #5614, merged 7 December 2024, restores input targeting the original main display (ID 0) after virtual-display targeting made some UI elements unclickable. It distinguishes physical/main display coordinates from mirroring-display coordinates.

Relevance: inspect display association and coordinates rather than assume an external-device entry proves correct delivery. The existing 0.3.2 diagnostics record app-window focus/display ID, external-device axis ranges/enabled state, and external events at activity dispatch. They cannot read arbitrary system-wide input or reassign HID display associations through ordinary app APIs. Our current grid uses an encoder and bitmap, not a newly registered Android display.

Source: https://github.com/Genymobile/scrcpy/pull/5614

### 4. Samsung: Auto Blocker and USB blocking while locked are separate checks

Samsung documents Auto Blocker's ability to block USB commands. Its One UI support material also documents Settings > Security and privacy > More security settings > Block USB connections while locked. Menu labels can vary by software version.

Relevance: check actual settings state, especially for later screen-off tests. Our log already records adbSetting=1 and accessory/ADB USB functions active, and the user installed our APK. That makes a blanket Auto Blocker explanation less compelling, although it does not prove every command is permitted. Locked-USB policy cannot by itself explain missing touch while the phone is unlocked and the grid visible.

Check settings before changing them. If an enabled restriction is tested as a variable, change only that setting temporarily, then restore it afterward. Do not disable unrelated security features or assume a Xiaomi-specific debugging-security option exists on Samsung.

Sources:
- https://www.samsung.com/us/support/answer/ANS10003636/
- https://www.samsung.com/us/support/troubleshoot/TSG10005233/
- https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/samsung-auto-blocker/

### 5. TeamViewer and AirDroid: a separate input component can stop while video continues

A TeamViewer community report describes a Samsung Galaxy Wide8 on Android 16 losing control shortly after another app becomes foreground, while live screen sharing continues. A commenter reports fixing it by removing battery restrictions specifically from the Universal Add-on, with another reply saying it helped. This is community evidence, not a general OS guarantee.

Source: https://community.teamviewer.com/English/discussion/142747/android-16-quicksupport-remote-control-stops-after-a-few-seconds

AirDroid's own accessibility-control documentation says that accessibility access can be deactivated on screen lock on some Samsung/Vivo devices. This supports checking the lifetime of an input component separately from the streaming service. Our probe currently has no such component; changing its battery mode cannot produce car events that have never arrived.

Source: https://help.airdroid.com/hc/en-us/articles/4405983257243-How-to-Control-Android-devices-through-AirDroid-Control-Add-on-Accessibility

### 6. Samsung remote-control API changes affected other vendors

Samsung's Knox 3.11 release notes say Android 15 restricts RemoteDesktop/RemoteInjection access to managed-device contexts with appropriate authorization. Splashtop documents changes to unattended Samsung control and an attended fallback through standard Android APIs.

Relevance: established control backends can require redesign after platform changes. This is not proof that Garmin uses Knox; no Knox input implementation was found in the inspected Garmin Java path. Enterprise enrollment is not a sensible troubleshooting requirement for this personal-phone/rental-car experiment.

Sources:
- https://docs.samsungknox.com/dev/knox-sdk/release-notes/knox-sdk-3-11/
- https://support-splashtopbusiness.splashtop.com/hc/en-us/articles/40719627878811-Change-to-Unattended-Remote-Control-Support-on-Samsung-Devices

### 7. Android itself has documented simulated-input regressions

Google's Android 16 Beta 3 notes list failure of simulated touch through ADB or accessibility as a known issue. This was a specific beta-era issue, not proof that the current stable Samsung build has it. Record exact software/build/security-patch details before attributing behavior to Android 16 as a whole.

Source: https://developer.android.com/about/versions/16/release-notes

### 8. Other car-mirroring systems

Searches included CarbitLink/EasyConnection and QDLink. There are owner complaints and vendor setup instructions, but no verified public fix for this Garmin/Samsung combination was found. Those products use paired phone/head-unit implementations and are not established replacements for Garmin's USB protocol. A reported workaround for a different host cannot be assumed to change this car's input path.

Product context: https://easyconn.net/product

## Tests ranked by usefulness and effort

### A. Phone-only first: see whether car taps reach Android at all

While parked, enable Developer options > Pointer location (optionally Show taps). Confirm the overlay responds to a normal phone touch first. Keep the phone unlocked, our grid visibly open, and tap only the car. Observe the physical phone screen; our generated car video does not capture Android's pointer overlay.

- Overlay responds to car taps, grid does not: evidence that input reaches the phone's display input path; investigate coordinates, focus, routing, and the app-window event logs.
- Grid logs WINDOW_EXTERNAL_INPUT: evidence that the external event reaches the activity before view dispatch. Compare with TOUCH records.
- Neither overlay nor app responds: inconclusive about raw USB report delivery; still compatible with no reports, invalid HID data, wrong display routing, or failed ADB injection. It is not proof that Android receives no input anywhere.

Disable the overlays after testing. This step observes system behavior without granting new debugging access to the rental car.

Android documentation: https://developer.android.com/studio/debug/dev-options

### B. Compare the existing passive and advanced ADB setup modes

With the car parked, Bluetooth pairing connected as required by Garmin's normal setup, first test passive mode in 0.3.2. Then explicitly select the existing Advanced: ask car to set up ADB touch option and use Retry connection. Phone debugging is already enabled in the supplied log. Check for ADB_SETUP_REQUEST_SENT and for an actual Android authorization prompt; its absence does not prove authorization. Avoid persistent rental-car debugging authorization.

Do not mix this comparison with a screen-lock experiment. If touch starts only after the ADB request, that is meaningful setup evidence. If it remains absent, the car's ADB_STATUS=1 still cannot establish a working control path.

### C. When a trusted computer is available: compare input backends independently

Current scrcpy supports API injection (SDK), device UHID and USB AOA input modes. A computer control test can help distinguish them:

- scrcpy --no-audio --mouse=sdk --no-mouse-hover
- scrcpy --no-audio --mouse=uhid
- Optional separate USB-host/AOA control check: scrcpy --otg (requires a compatible host/driver setup; Windows USB-driver constraints can apply).

If SDK control works but the car does not, there is no blanket failure of modern API injection on the phone, although the car may use a different/older helper. If SDK fails but UHID works, concentrate on injection permissions/API compatibility. If computer AOA input works, that establishes the tested mouse HID path, not compatibility with the car's touchscreen descriptor. A failed test can also be setup/authorization/driver-related.

These are desktop tests; installing scrcpy's server or another remote-control app on the phone does not turn the head unit into a scrcpy client. A computer test can be done separately from the car; wireless debugging with a trusted computer is another option for diagnostics while the car occupies USB.

Primary input-mode documentation:
- https://github.com/Genymobile/scrcpy/blob/master/doc/mouse.md
- https://github.com/Genymobile/scrcpy/blob/master/doc/otg.md

### D. System evidence if still unresolved

During a connected car tap test, a trusted debugging session can collect dumpsys input, display/window state, and input-related logcat. Where permitted, raw input-event tracing can show whether HID reports reach Android; access varies by device. Do not interpret a permission-denied result as absence of input.

This would narrow whether the problem lies in head-unit event production, the HID descriptor/display association, or a legacy ADB injector. The current ordinary app cannot collect all this global evidence by adding a runtime permission.

## What this means for our app

Keep the independent video transport and return-input path separate. An accessibility backend can perform gestures for an authorized app, but does not supply missing car coordinates or repair a head-unit injector by itself. A different backend would need a reliable source of car input, correct routing, explicit permissions and testing with phone/background/lock states. The most useful immediate step is the phone-only overlay test plus the existing controlled ADB-setup comparison, using the already-built 0.3.2 diagnostics.
