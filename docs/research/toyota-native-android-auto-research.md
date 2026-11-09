# Taiwan Toyota Android Auto variants: lessons for our bridge

Investigated 2026-11-07. The rental's exact head-unit model/software version is not yet available; the user can check later. No firmware has been installed or extracted from the car. This report separates published product evidence, general Android Auto architecture and proposed app design.

## Main finding

Comparable Taiwan Toyotas can use materially different projection systems despite similar screens. Auto+ Show is not simply the Android Auto receiver under another name. Native Android Auto support can come from a different head-unit generation/configuration or an added receiver module. Some platforms may support an official software upgrade, but no verified firmware-only activation path for this particular rental has been established.

## Published evidence

| Source | What it establishes | What it does not establish |
| --- | --- | --- |
| CARMAX's Android Auto guide, dated 7 December 2022 | Garmin-version Drive+ Link has an Android Auto connection mode, USB onboarding and Bluetooth/Wi-Fi wireless operation | That an older Auto+ Show-only unit contains the same receiver |
| Toyota Taiwan Drive+ Link specification table | Wired Android Auto is listed across the shown current configurations; wireless is listed on certain 9-inch configurations, including the Altis/Corolla Cross grouping | Compatibility of firmware across generations or trims |
| Toyota regional parts catalog, CROSS kit C99D9 | The 4G flagship upgrade kit supports CarPlay/Android Auto and is used with the existing Wi-Fi screen | That cellular connectivity alone creates Android Auto capability, or the exact internal hardware of the rental |
| InCar TY-GM9C product documentation | A Toyota Corolla Cross/RAV4/Altis 9-inch upgrade product supports CarPlay/Android Auto and lists a Sunplus 8368 processor | That every factory Toyota receiver uses that processor; this is an aftermarket product |
| Garmin Malaysia release notes for Corolla Cross P8600-0D002 | Version 2.10, dated 17 November 2021, includes an Android Auto navigation hang/stuck fix | That Malaysian firmware is compatible with Taiwan hardware, or that the fix applies to Auto+ Show input |

Sources:
- https://www.carmax.com.tw/zh-tw/news-detail?id=65
- https://www.toyota.com.tw/Drive%2B/3.0/
- https://www.carcenter.com.tw/products_detail.php?id=616
- https://www.incar-media.com.tw/products/cpaa/
- https://asia-aoem.garmin.com/toyota/en-MY/release-notes/lcmy20.html

The key distinction is the receiver implementation and its integration, not the screen size or the 4G label alone. A built-in receiver can continue to receive maintenance fixes through firmware. That does not mean a different base unit can gain that receiver through the same file. Exact model/part number, generation, region and supported upgrade documentation must establish that relationship first.

## How native Android Auto differs from our current Auto+ Show path

Android Auto is a phone-powered projection system with a compatible receiver in the car. It is distinct from Android Automotive OS, which runs Android apps on the car itself. The Toyota sales/support material confirms Android Auto functionality but does not disclose the exact receiver library or source code inside the rental's hardware family.

A receiver negotiates a projection session and handles video, audio and input channels. Touch positions/actions are sent back as Android Auto input messages. They are not generally simulated clicks on whatever unrelated phone app happens to occupy the physical phone screen. Open Headunit's public implementation provides concrete input-report examples with timestamp, pointer action, pointer IDs and coordinates; it also advertises touchscreen capabilities during service discovery. Google supplies a Desktop Head Unit for testing the corresponding projection and input behavior.

General sources:
- https://developer.android.com/training/cars
- https://developer.android.com/training/cars/testing/dhu
- https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/aap/protocol/messages/TouchEvent.kt

Our current probe negotiates Garmin USB Link and sends generated video. Car input would need to reach Android through the existing HID/ADB path before we can use it. Static comparison of Garmin phone APKs indicates ADB-based setup; the supplied log shows external touchscreen registration but no external touch events reaching our grid. A native Android Auto mode on another head unit does not establish that Auto+ Show's input path on this one works.

## What we can reuse in the app design

Proposed video flow:

Phone's Android Auto projection engine -> receiver component in our phone app -> render/encode for Garmin USB Link -> existing car screen

Proposed input flow, contingent on receiving car input:

Car touch -> working Garmin/Android return-input path -> our coordinate mapping -> Android Auto input report -> phone's Android Auto projection engine

This would use structured Android Auto events once input reaches our app, rather than require global ADB injection into arbitrary phone apps for each Android Auto action. It is a useful architectural direction, not a demonstrated fix. The two paths remain separate: adding an Android Auto receiver does not create car coordinates that never arrive. Background/screen-off input collection is also unresolved; native HID routed to the phone's foreground/lock screen cannot simply be assumed to reach our background receiver.

A receiver integration also requires audio/microphone/focus handling, session lifecycle and startup. The generated 5 fps test proves none of those Android Auto functions. Both video sides using H.264 does not prove compressed buffers can be forwarded unchanged; codec configuration, resolution, packetization and frame timing need verification. A render-and-re-encode path is the more straightforward experiment before considering direct forwarding.

## Startup remains a separate limitation

A physical car receiver can negotiate the normal Android Auto connection as a separate USB/Wi-Fi peer. Our proposed receiver and Android Auto would live on the same phone. Open Headunit supports self mode, but its current project documentation warns that Android Auto 17.4+ can block legacy startup triggers; its unrooted developer head-unit-server route still needs setup. A Toyota receiver's successful automatic startup therefore cannot be assumed available to an ordinary single-phone app.

Google's DHU documentation separately describes USB AOA transport and a developer-server connection route. ADB tunneling used for development is different from the obsolete arbitrary-app input injection potentially used by Auto+ Show.

Sources:
- https://github.com/andreknieriem/open-headunit
- https://developer.android.com/training/cars/testing/dhu

## What to investigate when the model/version becomes available

1. Identify the rental's head-unit model/part number, software version and generation from its normal About screen.
2. Match that identity to official manuals/release notes and supported upgrade kits. Determine whether Android Auto already exists as a normal connection mode, requires an approved upgrade component, or is absent from that family.
3. If a public firmware package for the exact family is available, read-only package/manifests/release-note comparison could establish whether receiver components differ. No such binary comparison has been completed here; receiver filenames, OS and licensing/activation details remain unknown.
4. Keep the phone-app prototype independent of any car update. Validate car touch delivery separately, then integrate a receiver and verify Android Auto input reports using phone input first.

Open Headunit is an external component with its own license and compatibility constraints; reusing it requires an integration and license review. The exact Toyota implementation is proprietary and has not been recovered. Installing another model/region's firmware or adding a module is outside the phone-only rental-user approach.
