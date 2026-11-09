# Building from source

Build, test and release AutoReplacer. The repository layout is at the end.

Requirements: **JDK 17**, the **Android SDK** (platform 36, build-tools), network access for Gradle, and `ANDROID_HOME` set. The Gradle wrapper (8.14.4) is included.

```sh
cd openheadunit
./gradlew :app:assembleGithubDebug
# the APK is in app/build/outputs/apk/github/debug/
```

- The `github` flavour is the one AutoReplacer uses; it adds the `.autoreplacer` application ID suffix (`com.andrerinas.headunitrevived.autoreplacer`).
- Release signing is read from your own keystore properties; do not commit keys.
- Run the plain-Java checks with `sh openheadunit/autoshow-tests/run.sh`.
- The app's wording is in `app/src/main/res/values/strings.xml`; the car-session text is the `as_*` entries. The Traditional Chinese (Taiwan) translation is `values-zh-rTW/strings.xml`. When you add an `as_*` string, add it to both.
- Version names are AutoReplacer's own (`0.7.x`). Add a line to [`openheadunit/CHANGELOG-AUTOREPLACER.md`](../openheadunit/CHANGELOG-AUTOREPLACER.md) with each release.

The earlier probe app is built with `build.py` at the repository root (see [`app/README.md`](../app/README.md)).

**Releases.** Pushing a tag such as `v0.7.18` (it must match `versionName`) makes GitHub build the APK, run the checks and attach the APK to a pre-release. See [`releasing.md`](releasing.md) for the steps and for setting up a stable signing key.





```
openheadunit/                    The AutoReplacer app (Open Headunit, reworked)
  app/src/main/java/.../autoshow/   Everything specific to the car:
      AutoShowService.kt            session orchestration, reconnect, calibration flow
      GarminLink.java, Wire.java    USB Link framing and the link state machine
      CarEncoder.java, CarSplash.java, CarCalibrationScreen.java   video to the car
      CarTouchServer.java, AutoShowTouchHub.kt, TsProtocol.java    touch on port 8051
      TouchArea.java, AutoShowTouchArea.kt                         touch mapping and calibration
      CarBluetooth.kt, BluetoothPlan.java                          Bluetooth
      AutoShowState.kt, AutoShowStatusCard.kt, AutoShowNotification.kt   status
      AutoShowUi.kt                 what is hidden from Open Headunit's screens
      AutoShowEventLog.kt, AutoShowLogSaver.kt                     logging
      AutoShowText.kt               translated wording for the plain-Java classes' reason codes
  autoshow-tests/               Plain-Java checks (no Android SDK)
  UPSTREAM.md                   Changes on top of the Open Headunit import
  CHANGELOG-AUTOREPLACER.md     User-facing changes
  README-OPEN-HEADUNIT.md       Open Headunit's own README, for reference
app/                            The earlier diagnostic probe (kept for reference)
README.md, README.zh-TW.md      Overview and setup, in English and Traditional Chinese (Taiwan)
docs/                           How it works, troubleshooting, building, releasing
docs/research/                  Field notes, decompile notes and the probe's history
tests/, build.py                The probe's tests and build script
LICENSE                         GNU AGPL v3
```
