# Reelock — Break the loop.
Blocks Instagram Reels / YouTube Shorts, or lets you watch for a fixed daily time. Feed and posts of people you follow stay normal.

Instagram: Stories are always allowed. Reels tab / player blocked or time-boxed; optional home-feed clean-up (skip Reels between posts, skip posts from accounts you do not follow).

Features: daily limits (None/5/10/15/20 min per app), Reel Pass, Focus Lock, Sleep Mode, gradual pace, time-reclaimed tracking, focus score and levels, weekly review, hourly scroll pattern, streaks, milestones, four accent themes, share progress, 1-minute warnings, diagnostics. Fonts: Manrope + Instrument Serif (OFL, see docs/LICENSES.md).

## Water popup (new)
Triple-tap the back of a Samsung phone -> a small "how much water?" window opens over whatever app you are in. Pick 100 / 250 / 500 ml with one tap or set an exact amount on the ruler; the glass fills toward your daily target and the window closes itself. Only the last 7 days are stored. It is a see-through activity (`WaterActivity`), so no overlay permission and no background service.
Setup: Good Lock > RegiStar > Back-tap action > Triple tap > Open app > **Reelock** (single app icon; a launch that is not an icon tap opens the popup, switch in Controls > water). Water is always blue. A "?" button in the popup (and "help" on the Today tile, or Controls > water) walks through the setup and opens Good Lock. Also available as the long-press shortcut on the Reelock icon and as a tile on the Today tab.

## Build
Push to GitHub -> Actions -> "Build" -> artifacts: `Reelock-apk` (install on phone) and `Reelock-playstore-aab` (Play Console).
Then: Settings > Accessibility > Reelock > ON. See PLAY_STORE.md for publishing.

## Play Protect says "App blocked"?
1. Play Store > profile > Play Protect > Settings > turn off "Scan apps", install, turn back on.
2. Samsung: Auto Blocker OFF while installing.
3. `adb install app-release.apk`.

## Self-test (no phone needed)
The blocking logic (`Detector.kt`: screen scan + decision engine) is pure Kotlin and is tested against fake Instagram / YouTube screens in `selftest/SelfTest.kt`:
`kotlinc -cp android.jar -include-runtime -d t.jar app/src/main/java/app/reelock/{Store,Detector}.kt selftest/SelfTest.kt && java -cp t.jar:android.jar app.reelock.SelfTestKt`
In the app: Controls > Self-check shows when Reels, Stories and feed-skip were last seen.

## Checks that run without a phone or Gradle
- `selftest/SelfTest.kt` - 103 scenario tests of the blocking engine (see above).
- `python3 tools/argcheck.py app/src/main/java/app/reelock` - checks every call of the project's own UI functions against their definitions.
