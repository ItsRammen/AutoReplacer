# AutoReplacer documentation

The [project README](../README.md) covers what AutoReplacer is, how to set it up and how to use it ([繁體中文](../README.zh-TW.md)). These pages cover the rest. They are in English.

## Using it

| Page | What it covers |
| --- | --- |
| [Troubleshooting and logs](troubleshooting.md) | Saving a log, reading it, and what the common messages mean |

## How it works

| Page | What it covers |
| --- | --- |
| [How AutoReplacer works](how-it-works.md) | Android Auto on the phone, video, the Garmin USB Link, touch, Bluetooth, reconnecting, security |
| [How this was found out](how-it-was-found.md) | How the unit's protocol was worked out, the wrong turns, and where the evidence is |

## Building and releasing

| Page | What it covers |
| --- | --- |
| [Building from source](building.md) | Build, tests, translations and the repository layout |
| [Making a release](releasing.md) | Tagging, the GitHub build, and a stable signing key |

## Research notes

The raw notes behind the research are in [`research/`](research):

| File | What it holds |
| --- | --- |
| [`field-notes.md`](research/field-notes.md) | Dated results from real car runs, newest last |
| [`touch-investigation.md`](research/touch-investigation.md), [`log-analysis-2026-11-07.md`](research/log-analysis-2026-11-07.md) | Early touch investigation and log reading |
| [`auto-show-decompile-1.3.5.1.md`](research/auto-show-decompile-1.3.5.1.md), [`auto-show-version-comparison.md`](research/auto-show-version-comparison.md), [`auto-show-wider-input-research.md`](research/auto-show-wider-input-research.md) | What the Auto+ Show app does and does not do |
| [`usblinkts-helper.md`](research/usblinkts-helper.md) | The car's touch helper and why it fails on Android 14+ |
| [`android-input-paths-aosp.md`](research/android-input-paths-aosp.md) | How each touch path looks to an app, from Android source |
| [`autoreplacer-on-openheadunit.md`](research/autoreplacer-on-openheadunit.md), [`android-auto-plan.md`](research/android-auto-plan.md), [`aa-loopback-overview-validation.md`](research/aa-loopback-overview-validation.md), [`toyota-native-android-auto-research.md`](research/toyota-native-android-auto-research.md) | Why Open Headunit, the plan, and background checks |
| `probe-*.md` | The earlier probe app's history, test plans and validation |

Two more files live beside the app: [`openheadunit/UPSTREAM.md`](../openheadunit/UPSTREAM.md) lists every change made on top of the Open Headunit import, and [`openheadunit/CHANGELOG-AUTOREPLACER.md`](../openheadunit/CHANGELOG-AUTOREPLACER.md) is the user-facing change history.
