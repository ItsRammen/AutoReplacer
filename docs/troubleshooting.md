# Troubleshooting and logs

How to save a log, how to read it, and what the common messages mean. For the short version, see the [README](../README.md#when-something-goes-wrong).

## Saving a log

- Open the **⋮** menu on the home card and choose *Save log*, or use the **Save log** button in the notification. The file goes to **Downloads** as `AutoReplacer-log-<date>.txt`.
- The log has two parts: the system log (`logcat`; only the last few minutes of a busy session fit) and an **event log** kept by the app (up to 1.5 MB, across sessions) that is appended at the end. The event log is the part to read first.

## Language of the log

The technical event names (`GARMIN_…`, `TS8051_…`, `AUTOSHOW_…`) and the readiness summary at the top of a saved log are always English. A few lines quote the sentence the app showed, for example the reason in `AUTOSHOW_STANDBY`, and those follow the app's language.

## Reading the event log

Lines are `MM-DD HH:MM:SS.mmm EVENT …`. The important ones:

| Line | What it tells you |
| --- | --- |
| `GARMIN_USB_OPEN`, `GARMIN_SESSION_REQUEST` | A link opened and the phone sent its session request |
| `GARMIN_VIDEO_READY: 1280x720 …` | The car is ready; the size it wants |
| `GARMIN_FIRST_VIDEO_BUFFER_SENT` | Video started. It must come **after** `VIDEO_READY` |
| `AUTOSHOW_PROJECTION` | The encoder and decoder were wired up |
| `AUTOSHOW_AA_HANDSHAKE_COMPLETE` | Android Auto answered and the handshake finished |
| `AUTOSHOW_FIRST_FRAME`, `AUTOSHOW_SPLASH_DONE` | Android Auto's first picture reached the encoder and replaced the waiting picture |
| `AUTOSHOW_STATS …` | Every 5 s at first, then every 30 s: `lastDecodedFrame`, `carFrames`, `aaPictureFrames`, `touchLines` |
| `AUTOSHOW_AA_RETRY attempt N` | Android Auto did not answer (or its session ended); the app will try again |
| `AUTOSHOW_ENCODER_REBUILT` | Android Auto's session restarted; a fresh encoder was built |
| `GARMIN_ERROR`, `GARMIN_DROP_CONTEXT`, `GARMIN_LAST_TRAFFIC` | The link failed, and what was happening just before |
| `AUTOSHOW_LINK_ENDED`, `AUTOSHOW_STANDBY`, `AUTOSHOW_RESUMED` | The reconnect flow |
| `PHONE_STATE USB_STATE …`, `ACTION_POWER_*`, `USB_ACCESSORY_DETACHED` | Whether the phone lost power or the accessory detached |
| `GARMIN_SAFETY_STATE restricted=… speed=…` | The car's own flag (see [Limitations](../README.md#limitations)) |
| `TS8051_LISTENING`, `TS8051_CONNECTED`, `TS8051_INIT`, `TS8051_CLOSED lines=… bytes=…` | The touch port and the car's connection to it |
| `TS8051_IDLE`, `TS8051_FIRST_BYTES` | Connected but silent, and the first raw bytes the car sent |
| `AUTOSHOW_TOUCH car=x,y → aa=x,y action=…` | One touch, before and after mapping |
| `AUTOSHOW_CALIBRATION_*`, `AUTOSHOW_TOUCH_AREA` | The calibration run and the mapping in use |
| `CAR_BLUETOOTH_*` | Pairing, audio connection and the hidden-connect attempts |

## Common situations

| You see | It usually means |
| --- | --- |
| Amber "Start Android Auto's server first" | Nothing is listening on `127.0.0.1:5277`. Start the head unit server in Android Auto's developer settings, then plug in. |
| `the peer accepted the connection and then sent nothing at all` | Android Auto's server accepted the connection but does not answer. After a session ended it can stay silent for a minute or more. Stop and start the head unit server. A connect-and-close probe also causes this, so the app never probes by connecting. |
| `TS8051_BIND_FAILED … EADDRINUSE` | An old car helper holds port 8051. The app asks it to exit; if it persists, restart the phone once. |
| `TS8051_CONNECTED` but no `TS8051_INIT` | The car connected to the touch port and sent nothing. See [Limitations](../README.md#limitations). |
| `GraphicBufferSource: Dropping frame that's going backward in time` | The encoder was kept across an Android Auto restart. The app now rebuilds it; if you still see this with a black car screen, send the log. |
| `GARMIN_FIRST_VIDEO_BUFFER_SENT` before `GARMIN_VIDEO_READY` | Video went to a link before the car was ready; the car usually resets its USB a few seconds later. Fixed, but shows what to look for. |
| `read failed: EIO`, `write failed: ENODEV`, `read interrupted by close()` | The USB accessory connection failed. Compare with the `PHONE_STATE` lines: a `POWER_DISCONNECTED` means the cable lost power; `USB_STATE … configured=false` without it means the car reset its USB function. |
| `Input stream already open for this accessory` | A new link opened the accessory before the old one released it. The app closes replaced links at once and retries. |

## Tests

Plain-Java checks for the protocol classes need no Android SDK:

```sh
sh openheadunit/autoshow-tests/run.sh
```

They cover framing and parsing (`Wire`, `TsProtocol`), the serial writer, the session watchdog, Bluetooth planning, touch mapping and calibration fitting, the touch server (including eviction of an old helper against a copy of the helper's loop), the port check and the plain-language disconnect messages.
