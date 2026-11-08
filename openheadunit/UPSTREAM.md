# Upstream

This folder is an unmodified import of **Open Headunit**:

- Repository: https://github.com/andreknieriem/open-headunit
- Commit: `ae74788d8312dd7a735775ddc21370f1741a7af1` (2026-11-03)
- Licence: GNU Affero General Public License v3 (`LICENSE`), with Michael A. Reid's original headunit notice (`COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`).

AutoReplacer builds its Auto+ Show (Garmin USB Link) head unit on this code. Changes made here are AutoReplacer's own commits on top of this import, so they can later be replayed onto a GitHub fork of Open Headunit, for example with `git format-patch --relative=openheadunit <import-commit>..`.

Any distributed build derived from this folder must be released under the AGPL v3 with its complete corresponding source.

## AutoReplacer changes on top of the import

The import commit is unmodified upstream. Later commits change (so they can be replayed onto a fork):

- `app/build.gradle.kts`: `applicationIdSuffix = ".autoreplacer"` on the `github` flavour.
- `app/src/main/AndroidManifest.xml`: the `NAVIGATION_UPDATE` custom permission name now starts with `${applicationId}` (a fixed name made Android refuse to install next to the original app, INSTALL_FAILED_DUPLICATE_PERMISSION); removed the root, Bluetooth scan/advertise and external-storage permissions; added `autoshow.AutoShowLauncherActivity` (USB accessory attach) and `autoshow.AutoShowService`.
- `app/src/main/res/values/strings.xml`: "Self Mode" labels are now "Auto+ Show mode" and the app is named AutoReplacer (English only; translations are untouched).
- `utils/Settings.kt`: an unset connection choice defaults to Self (Auto+ Show) mode only.
- New package `app/src/main/java/com/andrerinas/openheadunit/autoshow/` and `res/xml/autoshow_accessory_filter.xml`.
- `autoshow-tests/`: plain-Java checks for the copied Garmin link classes (`run.sh`).
- `main/OnboardingActivity.kt` and `main/SettingsFragment.kt`: one-line hooks into `autoshow/AutoShowUi.kt`, which hides the connection-type, video-test, display, DPI, appearance, sound, automation, car-launcher, location and vehicle wizard steps, the Wireless / Automation / Input / Navigation / Theming / More features settings sections, a few individual display and USB settings, and the "change connection types" note. Set `AutoShowUi.ENABLED = false` to restore the original screens. Welcome text strings are reworded.
- `utils/AppPermissions.kt`: the "Nearby devices" row asks only for `BLUETOOTH_CONNECT` (scan/advertise are no longer in the manifest, so requiring them left the row permanently ungranted), and the overlay, write-settings and storage rows are hidden.
- `autoshow/AutoShowReadiness.kt`: plain-words setup checks (Android Auto installed, Developer options, USB debugging, notifications) shown in the wizard's final summary; `AutoShowService` also puts a USB-debugging reminder in its notification.
- `autoshow/CarBluetooth.kt`, `BluetoothPlan.java`, `Wire.carBluetoothAddress`: reads the car's Bluetooth address from VIDEO_READY (bytes 44-60), starts pairing if the phone is not bonded to it, and reports whether the car has connected media/call audio. Public APIs only; logs show just the last two address bytes.
