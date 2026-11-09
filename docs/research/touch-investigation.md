# Auto+ Show touch investigation — 2026-11-07

## Observed on the user's setup

- Android API 36 phone; Taiwan Toyota Corolla Cross rental.
- User confirmed that our independent 0.1.1 client displayed generated AVC video on the car.
- Our 0.2.0 grid receives phone-local input, but the user reports no response to car-screen touches.
- User reports the same touch failure in Garmin's latest release and one preceding release.
- Earlier video logs show VIDEO_READY with 1280×720, encoding 1, protocol fields 1/2 and ADB-related status 1; separate ADB_STATUS=1 messages also arrived.
- The subsequently supplied full log includes 0.2.0–0.3.0. It confirms external touchscreen registration, but no external touch events reaching the grid. See log-analysis-2026-11-07.md for the parked run and the independent receive-watchdog failure.

## APK evidence

Statically inspected Auto+ Show 1.3.5.1 and 1.3.4.1. Prior APK obtained from Aptoide package ID 71152436. Android apksigner verified the prior APK and reported the Garmin signing-certificate SHA-256 `6e48a76cf2d8f938d409c048a60455dabab2471eff39acc8887976e8359ff340`, matching the fingerprint for the newer inspected APK and published APKMirror metadata.

Both inspected main activities:

- Mark the HID test completed and HID support false in their initial-permission checks, then take an ADB setup path.
- Implement their HID-test callback with the diagnostic string `skipping onGarminUsbLinkClientHidTest due to AdbOverride` and no HID-test processing.
- Check the phone's USB-debugging state and a head-unit-provided ADB status.
- Can request ADB setup through the core service. In the 1.3.5.1 transport this writes service 1, type 8, payload big-endian integer 1.

This is evidence that the recent Garmin client relies on an ADB path in this setup logic. It does **not** prove that every head unit lacks AOA HID support, that an ADB status of 1 authenticates the connection, or that an older release would fix Android 16 touch.

Update: 1.1.5.1 was subsequently obtained from the original APKMirror download flow and its file hash and Garmin signature verified. It has now been decompiled and compared: its initial checks also force HID-test-done/HID-unsupported, skip the HID callback with AdbOverride, and use the same service-1/type-8 ADB setup request. See docs/auto-show-version-comparison.md for the three-version comparison, evidence and remaining compatibility questions.

## External reports

- Taiwanese installer instructions require Bluetooth pairing and USB debugging for Auto+ Show: https://www.incar-media.com.tw/autoshow/
- A Taiwan owner discussion reports historical loss of car-screen control after an Android 10 update and subsequent firmware repair, and describes USB for image/input and Bluetooth for audio: https://www.mobile01.com/topicdetail.php?f=264&t=6129116
- Other owner discussion describes video surviving while touch stopped after Android updates: https://www.mobile01.com/topicdetail.php?f=264&p=13&t=5726561
- Android's device-mirroring documentation notes that certain Xiaomi/Poco phones require an additional USB-debugging security setting for input: https://developer.android.com/studio/run/device

These reports corroborate the kind of failure, but none establishes an Android-16-specific cause on this exact rental head unit. Phone model and current debugging/authorization state remain relevant missing observations.

## Diagnostic changes in our 0.2.1 build

- Passive input observation remains the default and retains the working video sequence.
- Logs phone manufacturer/model, readable global debugging setting, available sticky USB-function state, and input-device snapshots after VIDEO_READY and the first ADB-status message.
- Labels head-unit ADB status as a report, not proof of authorization or working input.
- Adds an explicitly selected advanced mode that sends the observed ADB setup request once, only when the phone already reports debugging enabled. It does not enable debugging, grant authorization, install a helper, alter firmware, or attempt an undocumented HID switch.
- Explains in the UI and README that authorizing a rental car for debugging grants broader phone access; persistent authorization should be avoided for this experiment.

The advanced request is a controlled test of an omitted setup step, not a promised repair. If the original app also fails with valid authorization and required phone-vendor settings, the car's input helper/firmware compatibility is a strong remaining hypothesis. A normal phone app cannot make the head unit register an AOA HID input device without a car-supported mechanism.
