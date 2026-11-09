## What's new in AutoReplacer
### 0.7.11
- Fixed reconnecting after the cable drops: picture is no longer sent to the car before it says it is ready (that made its USB reset again and again), and a replaced connection now closes before the new one opens
### 0.7.10
- The notification's Touch line is now accurate: "Waiting for the car", "Connected; waiting for the first touch", then "Working", and it updates as things change (it used to say "Not connected yet" the whole time)
### 0.7.9
- The touch status also warns when the car re-locks touch before the first touch has started
### 0.7.8
- When the car holds back touch because it reports a driving restriction, the app now says so (Touch: "Locked by the car") instead of looking connected but dead; touch starts by itself when the car reports it is free
### 0.7.7
- Fixed a black car screen after the cable dropped and came back: Android Auto's picture now returns, with the waiting picture shown meanwhile
- A cable that bounces twice quickly now uses the new connection instead of ignoring it
- Android Auto reconnects within a couple of seconds after it restarts
### 0.7.6
- About shows AutoReplacer's own changelog, and the version number is AutoReplacer's (it showed Open Headunit's 4.0.0-beta2)
### 0.7.5
- Android Auto is kept running for 90 seconds when the car's USB drops, so plugging back in no longer needs the head unit server restarted
- Plain-language messages when the car disconnects, instead of codes like EIO
- Touch calibration is protected: resetting or recalibrating asks first, and the previous calibration can be restored
### 0.7.4
- Fixed Android Auto not connecting: the server check no longer uses up Android Auto's single connection
### 0.7.3
- Settings: a Car touch section shows whether touch is calibrated, with Calibrate and Reset
### 0.7.2
- Touch calibration: tap nine crosses on the car screen so touches land where you press
- The app keeps its own event log, and Save log includes it
### 0.7.1
- Bluetooth status follows the car live, and the app tries to connect the car's audio
### 0.7.0
- Touch works after unplugging and replugging without restarting the phone
### 0.6.x
- A warning when Android Auto's head unit server is not running, with an Open Android Auto button
- One notification for the whole app, saying what is working
- Touch on the whole screen, including the bottom row, scaled to the car's real touch area
- A new icon and launch screen, and a home screen that shows the car session's status
### 0.5.x
- Android Auto shows on the car: the phone runs it, encodes the picture and sends it over Auto+ Show's USB link
- A waiting picture on the car while Android Auto starts
- Car touch is received by the app and passed to Android Auto
- Pairs the car's Bluetooth for audio
- Settings, setup steps and branding cleaned up for Auto+ Show only
## Things to know
- Touch is single-finger: taps and drags work, pinch does not (the car sends one finger)
- Start Android Auto's head unit server (Android Auto, Developer settings) before plugging in; Android Auto stops it after each session
- Calibrate touch once in Settings, Car touch, with the car connected
- AutoReplacer is built on Open Headunit; its own history is on its GitHub page
