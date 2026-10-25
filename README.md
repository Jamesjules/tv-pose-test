# Pose Play v0.2 (RAZ Corp)

One APK, two roles (auto-detected):
- TV: runs the demo game (pop bubbles with your hands). Input = phone skeleton if a phone is connected, otherwise the TV's own camera (USB webcam / built-in). Switches automatically both ways.
- Phone: opens its camera, tracks you with MediaPipe, sends only the skeleton (a few hundred bytes/frame) to the TV.

Links (phone -> TV):
- Wi-Fi: same router or phone hotspot. Tap "Wi-Fi auto" (auto-find) or "TV IP" (IP is shown on the TV screen).
- Bluetooth: pair phone and TV in system Bluetooth settings once, then tap "Bluetooth" and pick the TV.
- Cable: not supported (TVs are USB hosts and phones can't talk to them directly without special adapters). Use USB tethering/hotspot if you want a wired-ish link.

Install over the old one: adb install -r pose-play-uvc.apk  (TV)  and normally on the phone.
Phone stays in landscape. Put it on a stand 2-3 m from you.
