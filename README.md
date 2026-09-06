Spatial Android
===============

Spatial Android is a native Android spatial-computing shell that turns a phone into a futuristic, hand-tracked launcher environment.

The core interaction pipeline is:

CameraX camera frames -> MediaPipe Hand Landmarker -> landmark processing -> gesture state machine -> virtual cursor -> Compose spatial UI hit testing -> pinch click / pinch-hold drag.

IMPORTANT HONESTY NOTE
----------------------

Android does not allow a normal app to freely embed arbitrary third-party apps as live floating Compose windows. This project therefore uses a layered approach:

1. Native internal spatial apps run inside this project as floating Compose windows.
2. Installed Android apps are discovered dynamically and launched using normal Android package/activity APIs.
3. Where supported, Android windowing/task behavior may be used by the system, but this app does not rely on undocumented hacks.
4. Optional advanced integrations, such as notification access or accessibility, are only described as optional and are not silently required.

Features
--------

- Hand tracking using MediaPipe Hand Landmarker on-device.
- Virtual cursor controlled by index fingertip.
- Pinch click using thumb + index finger.
- Pinch-hold drag for windows and draggable UI regions.
- Hover feedback for spatial controls.
- Floating window manager with move, resize, minimize, maximize, focus, snap/layout presets.
- Dock with Home, Apps, Search, Recents, Notifications, Settings.
- Universal menu near the cursor.
- App library that dynamically discovers installed launchable apps.
- Universal search for installed apps, internal apps, notes, and settings.
- Internal apps: Notes, Calculator, Gallery, Files, Settings, Media, Clock, Browser-style panel, Recents, Notifications, Quick Settings, Help.
- Floating spatial keyboard with QWERTY, symbols, compact, and numeric modes.
- Optional speech input through Android speech recognizer activity when available.
- Camera passthrough background mode and procedural virtual background mode.
- Debug overlay and hand skeleton visualizer.
- Calibration settings and manual calibration capture.
- Low/Medium/High graphics quality modes.
- Touch fallback if camera permission is denied or tracking fails.

Supported Android Versions
--------------------------

- minSdk: 26
- targetSdk: 34

Building Locally
----------------

1. Install Android Studio or a local Gradle/Android SDK environment.
2. Open the project in Android Studio, or run:

   gradle wrapper --gradle-version 8.7
   ./gradlew assembleDebug

3. GitHub Actions downloads the MediaPipe hand landmarker model into app/src/main/assets/hand_landmarker.task before Gradle compilation. For a local build, place the model file at that path before running the build.

GitHub Actions
--------------

The included workflow:

- checks out the repository
- installs JDK 17
- installs/configures Android SDK
- installs Gradle 8.7
- generates the Gradle wrapper
- downloads the hand-tracking model
- runs ./gradlew assembleDebug
- uploads app-debug.apk as an artifact

After a GitHub Actions run completes, open the workflow run and download:

Artifacts -> spatial-android-debug-apk

Permissions
-----------

Required:

- CAMERA: used locally for hand tracking.

Optional / situational:

- Notification listener access: only if you choose to enable real notification mirroring in Android settings.
- Speech recognition: handled through the system recognizer activity when available.

No camera frames, hand images, or gesture data are uploaded.

Camera and Hand Tracking
------------------------

The app uses CameraX ImageAnalysis and MediaPipe Tasks Vision HandLandmarker in VIDEO mode.

Processing happens on a dedicated analyzer executor, not on the main/UI thread.

The index fingertip drives the cursor. The thumb-index distance drives pinch.

Pinch Detection
---------------

Pinch detection uses:

- normalized thumb-tip to index-tip distance
- hand-size normalization
- smoothing
- start threshold
- release threshold
- temporal confirmation
- hysteresis
- drag delay
- movement slop

This avoids one-frame accidental clicks.

Calibration
-----------

Settings includes manual calibration capture:

1. Hold an open hand and capture open distance.
2. Pinch and capture pinch distance.
3. Apply to compute start/release thresholds.

External App Limitations
------------------------

Installed apps such as YouTube, Chrome, Telegram, Spotify, Instagram, WhatsApp, Maps, and games are launched as normal Android apps.

They cannot always be embedded inside this app's Compose windows. When embedding is not possible, the app launches them normally and preserves internal spatial state as much as Android permits.

Accessibility
-------------

Hand tracking is an additional input method, not the only input method.

Touch input, Android back handling, and normal activity launching remain available.

Optional accessibility service support is not silently requested. If future advanced integration needs accessibility, Android will require explicit user enablement in system settings.

MediaProjection
---------------

This project does not secretly record the screen. If future screen-preview features are added, they must use Android MediaProjection with explicit user consent and local-only handling.

Privacy
-------

- Camera frames are processed locally.
- No hand images are uploaded.
- No gesture data is sent to a server.
- The hand-tracking model is bundled into app assets during build.

Performance
-----------

Settings provides Low/Medium/High graphics quality.

These affect:

- background particle count
- camera analysis resolution
- visual effect density
- tracking workload

Known Limitations
-----------------

- Pseudo-spatial smartphone interaction, not true 6DoF headset tracking.
- Arbitrary third-party apps cannot be embedded as live Compose windows.
- Real notification mirroring requires optional user-enabled notification listener access.
- Some quick settings toggles open the relevant Android settings screen because direct toggling is restricted.
- Keyboard long-press key repeat is basic and can be extended.

Future Improvements
-------------------

- Two-hand interaction architecture.
- More advanced window snapping.
- Real notification listener integration.
- Optional MediaProjection-based external app preview cards where legally and technically appropriate.
- More gesture customization.
- More internal spatial apps.
