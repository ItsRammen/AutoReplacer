# Validation of the proposed Auto+ Show / Android Auto loopback overview

Reviewed 2026-11-07 before receiver implementation. No receiver code has been added yet. Conclusion: retain the loopback receiver idea, correct the USB input path and remove unsupported root-cause claims.

## 1. Root cause is a hypothesis, not an established result

The supplied log establishes external touchscreen registration and absence of external-origin touch events reaching our grid. It does not show raw reports being delivered and rejected, an ADB injection exception, Bluetooth HID use, an accessibility failure, or an OS process kill. A parked run reported restricted=0, speed=0. Our recorded 0.3.0 background teardowns came from the app's own receive-silence watchdog.

Static inspection of three Garmin releases found explicit HID-test bypass and ADB setup. It did not identify an accessibility input engine to transplant. AutoLynk was not inspected and should not be grouped with Auto+ Show as though its mechanism had been verified.

## 2. USB Host interception is the central incorrect assumption

In this setup the car is the USB host; the phone is in Android accessory mode. The android.hardware.usb namespace includes APIs for different roles. Its name does not mean our phone can use USB Host API calls to inspect arbitrary transfers initiated by the car.

Our UsbAccessory stream carries Garmin USB Link bulk data. AOA HID registration, descriptors and events use endpoint-zero control requests that Android passes into its input system. They are not normal application payloads waiting to be parsed from that stream. Android does not provide an ordinary accessory-app API to divert those raw HID control transfers away from the system.

If Android delivers a resulting MotionEvent to our visible activity, the app can map it and send an Android Auto input report. If the car were to provide a separate application-level touch-message stream, the app could parse it once documented. Neither condition is established by the supplied logs. Adding a raw-HID decoder to the existing USB bulk listener would not make those reports appear.

Primary references:
- https://developer.android.com/develop/connectivity/usb/accessory
- https://source.android.com/docs/core/interaction/accessories/aoa2

## 3. The security claims need qualification

### HID versus arbitrary software injection

A recognized physical Bluetooth/USB HID device is processed through Android's input system. It is different from an ordinary app calling privileged software-injection APIs to control other apps. There is no substantiated blanket Android-10+ rule that all external HID requires a foreground companion app or that all events are automatically dropped whenever a phone locks or its display turns off. Wake behavior, lock-screen policy, USB security restrictions, display association and device configuration can all matter. None is diagnosed solely by device enumeration.

### Accessibility and overlays

Modern Android still exposes AccessibilityService.dispatchGesture for enabled services with the declared capability. Restricted Settings can constrain whether a service is enabled for a particular installation; that is not a universal ban on gestures across unrelated apps. Overlay permission itself does not grant arbitrary event injection. Android 12's untrusted-touch changes concern touches obscured by untrusted overlays and have specified exceptions; they are not a general Bluetooth-HID prohibition.

Primary references:
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)
- https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events

### Background execution

Background limits and vendor battery policies are relevant engineering constraints, not proof that a socket-listener thread is suspended every time another app opens. Streaming work should be service-owned with the appropriate foreground-service type and resource lifecycle. Our existing connectedDevice service retains the USB session while the activity opens logs or another app. Screen-off streaming and input routing still require physical-phone verification. It does not gain global input ownership just because it is a foreground service.

## 4. What is valid about the loopback receiver

A receiver can connect to Android Auto's developer head-unit server on the same phone, subject to installed-version behavior and setup. Open Headunit self mode provides implementation precedent. The receiver can send AAP touch reports into that projection session without requesting generic global MotionEvent injection for each Android Auto action.

This is scoped Android Auto input handling, not a bypass that disables Android's security model or grants access to arbitrary phone windows. The missing upstream car-input source remains a separate problem.

AAP is not a plain stream of protobuf messages over TCP. The implementation must negotiate a version and TLS session, discover/open services, frame and reassemble messages, manage video flow control, acknowledge data and handle focus/session state. The developer head-unit server usually needs to be started by the user. A documented desktop ADB-forwarding development route does not itself prove unrestricted same-phone automatic startup on current Android Auto releases.

References:
- https://developer.android.com/training/cars/testing/dhu
- https://github.com/andreknieriem/open-headunit
- https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/aap/protocol/messages/TouchEvent.kt
- Local inspected receiver TLS/version/framing sources under /tmp/open-headunit-src/app/src/main/java/com/andrerinas/openheadunit/aap/

## 5. Audio must be negotiated, not merely muted

In the inspected Open Headunit self-mode path, media and speech sink announcements can be omitted while a system-sound sink is still announced. Its policy comments explicitly say the historical reason for the self-mode gating is not settled. That is implementation precedent, not proof of an unavoidable recursive audio loop.

Omitting a sink and accepting that sink then muting/discarding its audio have different effects: the latter can lose sound instead of returning it to the phone. Bluetooth music/call routing, navigation speech, focus and microphone handling require separate tests. Disabling every sink does not guarantee all audio remains correctly routed.

Reference:
- https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/aap/protocol/messages/AudioSinkAnnouncementPolicy.kt
- /tmp/open-headunit-src/app/src/main/java/com/andrerinas/openheadunit/aap/protocol/messages/ServiceDiscoveryResponse.kt

## 6. Corrected architecture

Video:

Android Auto engine on phone -> AAP over local connection -> service-owned receiver/video pipeline -> Garmin USB Link output -> car screen

Phone preview and debug controls:

Service-owned session -> optional preview activity + main debug/control activity

Hiding or replacing either activity should not tear down the service's Android Auto or Garmin connection. A visible preview is not required for sending video to the car if its decoder/encoder or validated compressed-video bridge is owned independently of the activity surface.

Input, contingent on delivery:

Car digitizer -> car HID/ADB implementation -> Android input delivery -> receiving app surface or another explicitly authorized input backend -> coordinate mapping -> AAP input report -> Android Auto engine

Alternative input only if documented on this car:

Car application-level touch-message stream -> our accessory parser -> coordinate mapping -> AAP input report

There is no confirmed application-level touch-message stream in our evidence. We can initially test the receiver using phone input and leave car input diagnostic until verified.

Coordinate mapping must account for negotiated resolution, rotation, aspect ratio, margins/letterboxing, pointer IDs and down/move/up/cancellation state. Hard-coded 1280x720 or 1920x1080 scaling is insufficient when the actual displayed geometry differs.

## Implementation implication

The authorized receiver-plus-main-debug-screen feature remains a reasonable prototype. Its scope should be described as Android Auto video with phone controls first; reliable car touch and locked-phone input remain unproven. The proposed raw-USB interception explanation cannot be used to promise a repair for the current missing touch path. Before claiming that security restrictions caused the failure, obtain event-delivery or injection-error evidence from the connected phone/head unit.
