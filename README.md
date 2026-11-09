# AutoReplacer

**English** · [繁體中文（台灣）](README.zh-TW.md)

Android Auto on a car screen that only speaks **Auto+ Show** (Garmin USB Link).

AutoReplacer runs Android Auto on your phone, encodes the picture, and sends it to the car over USB. The car's touches come back to the phone, so you can use Android Auto on the car's own screen. Audio and calls go over Bluetooth.

It is a **hard fork of [Open Headunit](https://github.com/andreknieriem/open-headunit)** (itself a continuation of Michael A. Reid's [Headunit](https://github.com/mikereidis/headunit)), reworked for one job: being the phone side of an Auto+ Show head unit. It is licensed under the **GNU AGPL v3**.

> **Status: working prototype, tested on one car and one phone.** Taiwan Toyota Corolla Cross with an Auto+ Show unit, Samsung SM-S9480 on Android 16, Android Auto 17.9. Expect rough edges. Please read [Limitations](#limitations) before relying on it.
>
> **Not affiliated** with Garmin, Google, Toyota or Auto+ Show. No Garmin code or assets are included; the USB protocol was worked out from observation (see [How this was found out](docs/how-it-was-found.md)).
>
> **Safety.** Android Auto is built to be used while driving with its own limits, but a touchscreen is still a distraction. Use voice control while the car is moving, and set up destinations before you drive. AutoReplacer does not try to override anything the car reports about driving restrictions.

## What it does

- **Shows Android Auto on the car.** Android Auto runs on the phone as if the phone were its own head unit. AutoReplacer picks up its video, re-encodes it for the car, and sends it over USB.
- **Passes the car's touches to Android Auto.** Taps and drags work. Pinch does not: the car sends one finger.
- **Calibrates touch.** The car reports touch in a coordinate space that does not match its picture. A guided calibration (nine crosses on the car screen) measures the mapping and corrects it.
- **Pairs the car's Bluetooth** for audio and calls, and shows whether it is connected.
- **Recovers from USB drops.** It keeps its state, shows a waiting picture on the car, and reconnects Android Auto when the cable comes back.
- **Says what is happening.** One notification and a status card report the car link, Android Auto, touch and Bluetooth in plain words.
- **Keeps its own log**, which you can save and attach to a problem report.

It does **not** use root, Shizuku or accessibility services, and it does not record your screen.

## Requirements

- A car with an **Auto+ Show / Garmin USB Link** head unit (the unit shows "Auto+ Show" and is normally driven by Garmin's Auto+ Show phone app).
- An Android phone with **Android Auto** installed. Tested with Android Auto 17.9 on Android 16. The method relies on Android Auto's "head unit server" (Android Auto 17.4 or newer).
- **Developer options** and **USB debugging** turned on. The car's unit uses an ADB connection to set up its touch helper.
- A USB data cable (not charge-only) and the car's USB port that works with Auto+ Show.
- Bluetooth, paired with the car, for audio and calls.
- Notifications allowed, so the status notification can show.

## Setting up

1. **Install** the APK from the [Releases](../../releases) page. The package is `com.andrerinas.headunitrevived.autoreplacer`, so it can sit next to Open Headunit. Debug builds are signed with a debug key, so installing over a build signed with another key needs an uninstall first.
2. **Open the app once** and go through the short setup. It checks Android Auto, Developer options, USB debugging and notifications, and asks for Bluetooth ("Nearby devices") permission.
3. **Turn on Android Auto's developer mode.** In Android Auto's settings, tap the version number about ten times. Then open *Developer settings*.
4. **Start the head unit server.** In Android Auto's *Developer settings*, open the menu and choose **Start head unit server**. Do this *before* plugging in. Android Auto may stop it after a session, so you may need to start it again. The app shows an amber warning if nothing is listening, with an **Open Android Auto** button.
5. **Plug in.** Connect the phone to the car's Auto+ Show port. The first time, Android asks which app should handle the USB accessory: choose AutoReplacer and tick "Use by default".
6. **Allow the car's ADB prompt** on the phone if it appears. Ticking "Always allow" lets that car run ADB commands on the phone, so only do it on a car you own or trust (see [Privacy and security](#privacy-and-security)).
7. A **waiting picture** (a spinner and the current step) appears on the car, then Android Auto.
8. **Calibrate touch once.** With Android Auto showing, open the ⋮ menu on the home card, or *Settings → Car touch*, and choose *Calibrate touch on car*. Tap the middle of each of the nine crosses.

### The home screen

The home card shows the car session's state (waiting for the car, connecting, Android Auto starting, showing on the car, car disconnected), plus one line each for **Touch** and **Bluetooth**. **Reconnect** appears when the cable is in and no session is running. **⋮** holds *Save log* and *Calibrate touch on car*. The notification shows the same status, with *Save log* and *Mark* buttons.

## Languages

The app is available in **English** and **Traditional Chinese (Taiwan)** (繁體中文). It follows the phone's language, or you can pick one in *Settings*. This README is also available in [繁體中文](README.zh-TW.md). The pages under [`docs/`](docs) are in English.

## When something goes wrong

| You see | What to do |
| --- | --- |
| Amber **Start Android Auto's server first** | Start the head unit server in Android Auto's developer settings, then plug in. |
| **Android Auto is not answering** | Stop and start the head unit server again. It can stay silent for a minute or more after a session ends. |
| The car screen is black or stuck on the waiting picture | Unplug and replug the cable. Check that the server is running. |
| **The USB connection to the car was lost** | The cable or port dropped, or the car restarted its USB. The app waits 90 s and reconnects; if it does not, replug. |
| Touch does nothing, or is off near the edges | Calibrate touch (*⋮ → Calibrate touch on car*). If it never arrives, save a log. |

To save a log, open **⋮ → Save log** (or tap **Save log** in the notification). It goes to **Downloads**. [Troubleshooting and logs](docs/troubleshooting.md) explains how to read it.

## Privacy and security

- The car needs to run **ADB commands** on your phone to set up touch. Tick "Always allow" only on a car you own or trust. Android expires "Always allow" keys after seven days of disuse.
- The app opens a TCP port on **127.0.0.1** only, so it is not reachable from the network.
- A saved log holds your phone model and a **masked** Bluetooth address (last two bytes). Look it over before posting it.

## Limitations

- **Tested on one car and one phone.** Other Auto+ Show cars, phones and Android versions are untested. Touch calibration is per phone, not per car.
- **Single-finger touch.** The car's touch link carries one point, so pinch and two-finger gestures cannot work. Android Auto's on-screen zoom buttons cover it.
- **Android Auto's head unit server must be started by hand**, and Android Auto may stop it after a session. AutoReplacer cannot start it for you.
- **A USB reset ends Android Auto's session**, so a drop always means reconnecting Android Auto, not just the cable.
- **Touch sometimes does not arrive**, and the cause is not known.
- **Intermittent USB drops** happen on the tested setup. A cable, port or car-side cause is likely; the app can only recover, not prevent them.
- **Bluetooth calls.** The phone cannot connect the headset (call) profile itself; if it is not connected, choose the car in the phone's Bluetooth settings.
- **Early software.** Debug-signed builds, no store release, no automatic updates.

## Learn more

| Page | What it covers |
| --- | --- |
| [Troubleshooting and logs](docs/troubleshooting.md) | Reading the event log and what common messages mean |
| [How AutoReplacer works](docs/how-it-works.md) | Android Auto on the phone, video, the USB Link, touch, Bluetooth, reconnecting |
| [How this was found out](docs/how-it-was-found.md) | The research, the wrong turns, and the evidence |
| [Building from source](docs/building.md) | Build, tests, translations, repository layout |
| [Making a release](docs/releasing.md) | Tagging and the GitHub build |
| [All documentation](docs/README.md) | Index, including the raw research notes |
| [Changelog](openheadunit/CHANGELOG-AUTOREPLACER.md) | What changed in each version |

## Reporting a problem

Open an issue with:

1. What you saw on the **car** and on the **phone**, and roughly when.
2. The **saved log** (*⋮ → Save log*, from Downloads). Look it over first.
3. The phone model, Android version, Android Auto version, and the car and unit.

For USB drops and missing touch, note what you did at the time (unplugged, turned the cable, started the car, began driving). Tapping **Mark** in the notification at the moment something happens puts a timestamp in the log. Those timestamps line the log up with real events.

## Licence and credits

- **AutoReplacer** is a **hard fork** of Open Headunit. It is not kept in sync with upstream, and it does not aim to be merged back. What changed is listed in [`openheadunit/UPSTREAM.md`](openheadunit/UPSTREAM.md); the import point is recorded there.
- It is released under the **GNU Affero General Public License v3** ([`LICENSE`](LICENSE)). Anyone who distributes a build, or lets people use a modified version over a network, must offer the complete corresponding source under the same licence.
- **Open Headunit**: André Rinas and contributors, <https://github.com/andreknieriem/open-headunit>.
- **Headunit**: Michael A. Reid, <https://github.com/mikereidis/headunit>, the original Android Auto head unit code and protocol work (notice: [`openheadunit/COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`](openheadunit/COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt)).
- Third-party libraries keep their own licences.
- "Garmin", "Auto+ Show", "Android Auto", "Toyota" and other names belong to their owners and are used only to say what the software works with. The USB protocol described here was worked out from observing a car and from public analysis of freely downloadable apps, for interoperability.
