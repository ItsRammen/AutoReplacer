# Supplied full-log analysis — 2026-11-07

Phone: Samsung SM-S9480, Android API 36. Versions span 0.1.0 through 0.3.0.

## Background termination

All three 0.3.0 service sessions end with our own IOException: No car response for 15 seconds. No other ERROR record appears in these 0.3.0 sessions. Their closing counts are 85, 86 and 84 video buffers, with successful output continuing after UI_BACKGROUND. The final session starts at 14:23:40.191, receives its last message at 14:23:44.892, and our watchdog ends it at 14:24:00.193. This establishes a premature app-controlled teardown, rather than evidence of an OS background kill, in the supplied sessions. Other unrecorded failures remain possible. No PHONE_SCREEN_OFF record occurs in this excerpt, so it does not verify locked-phone operation.

## Touch

External touchscreen devices 28/29/30 register with source 0x1002 during 0.3.0 negotiation. The 22 recorded touch-event lines in 0.3.0 name internal device 6 sec_touchscreen; none is from an external device. The final grid records zero events. The final safety report is restricted=0, speed=0 at 14:23:44.892; the earlier 0.3.0 runs report restricted=1, speed=0.

All supplied sessions use passive observation; no ADB_SETUP_REQUEST_SENT occurs. USB debugging/function state is enabled, but neither it nor car ADB_STATUS=1 establishes authorization or functioning injection. External device enumeration is evidence of registration, not evidence of HID report delivery or correct Android routing. These logs cannot distinguish head-unit report failure, Android/Samsung HID routing/compatibility, or legacy ADB helper compatibility.

## 0.3.1 changes

Receive silence alone no longer fails advancing video. Negotiation, first-video and video-progress timeouts remain, as do blocked-write watchdogs, USB failures and stationary-state handling. RX_QUIET records absence of incoming reports without claiming ongoing car acknowledgement. External axis ranges/enabled state and activity-window external-motion/focus diagnostics were added without new permissions, hidden APIs or global input capture.

## External context

Historical Taiwan owner reports describe Auto+ Show touch failures after Android updates, including firmware-dependent repair. These are corroborating history, not confirmation of an Android-16-specific cause on this rental:

- https://www.mobile01.com/topicdetail.php?f=264&t=6129116
- https://www.mobile01.com/topicdetail.php?f=264&t=6480055
- Android input routing architecture: https://source.android.com/docs/core/display/multi_display/input-routing

## Next field check

Park, reconnect after updating, confirm the video counter, tap only the car while the phone grid is visible, then background for at least a minute. Export the full log afterward. Screen-off operation is a separate check. The updated log can establish whether external events reach the activity but miss the grid; absence at both levels still needs system/head-unit evidence to localize further.
