# TV Pose Test (RAZ Corp)

Tiny Android TV app to answer 3 questions on your Sony Bravia (or any Android TV):
1. Can the TV see your USB webcam? (Camera2 path, then direct UVC path)
2. How many camera frames per second arrive?
3. How fast is MediaPipe pose tracking (fps + latency, GPU or CPU)?

## Build WITHOUT Android Studio (easiest)
1. Create a new GitHub repo, upload everything in this folder (keep the `.github` folder).
2. Open the repo > Actions tab > "Build APKs" > wait ~5-8 min.
3. Download artifact `apks` -> `pose-test-camera2.apk` and `pose-test-uvc.apk`.
   (If the UVC build fails, the Camera2 one is still produced.)

## Build with Android Studio
Open folder, download `pose_landmarker_lite.task` from
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
into `app/src/main/assets/`, then Build > Build APK.
For UVC: add `-PwithUvc=true` (Gradle properties / command line).

## Install on the Sony TV (PowerShell)
1. TV: Settings > Device Preferences > About > tap "Build" 7 times. Then Developer options > USB debugging (and Network/ADB debugging) ON.
2. `adb connect <TV-IP>:5555`   then   `adb install -r pose-test-uvc.apk`
3. Plug webcam into TV USB port (before launching), open "Pose Test" from Apps.
4. Press OK on the remote to switch Camera2 <-> UVC.

## Reading the result
- Pose fps 20+ : great, full game feasible.  12-20 : playable, simple games.  Under 10 : TV too weak, use a better box.
- "NO cameras exposed" on Camera2 + UVC says "opened" : native UVC path works (what we want).
- Both fail : note the on-screen message and send it to me.
