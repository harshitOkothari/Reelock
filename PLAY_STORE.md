# Play Store checklist for Reelock

## 0. Reality check
Google reviews Accessibility-API apps strictly. Digital-wellbeing / app-limiting is an accepted core use, but approval is not guaranteed. What this repo already does for you:
- Prominent in-app disclosure + explicit "Agree" before the Accessibility settings are ever opened (Onboarding step "Before we turn it on", and a gate for older installs).
- The service does nothing until the user has agreed.
- Service is scoped to only `com.instagram.android` and `com.google.android.youtube` and declares `isAccessibilityTool="false"`.
- No INTERNET permission, no analytics, no ads, no data leaves the device.
- Targets Android 15 (API 35), edge-to-edge, signed release AAB built by CI.
- Privacy policy page (docs/privacy.html) and in-app link.
- No Instagram/YouTube logos or branding; "not affiliated" text included.

## 1. Before you upload
1. Enable GitHub Pages: repo Settings > Pages > Deploy from branch > `main` / `/docs`.
   Privacy URL becomes https://harshitokothari.github.io/Reelock/privacy.html  (edit YOUR_EMAIL in docs/privacy.html first!)
2. Make your own upload key (do NOT use the dev key in the repo):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   Then add GitHub secrets (repo Settings > Secrets and variables > Actions):
   - UPLOAD_KEYSTORE_B64  = output of `base64 -w0 upload.jks`
   - KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
   Keep upload.jks backed up privately. Enrol in Play App Signing when creating the app.
3. Run the workflow, download artifact `Reelock-playstore-aab` (app-release.aab).
4. Delete `app/reelock.jks` from the repo once you stop sideloading (it is only a dev key).

## 2. Play Console
- Developer account: one-time US$25. New personal accounts must run a closed test with 12+ testers for 14 days before production.
- Category: Health & Fitness or Productivity. Target audience: 13+ (not for kids).
- Data safety form: collects no data, shares no data.
- Permissions Declaration > Accessibility API: core functionality = "Helps users limit short-form video (Instagram Reels / YouTube Shorts) for digital wellbeing". Paste the disclosure text from the app. Upload a short screen recording showing: disclosure screen -> Agree -> Android Accessibility settings -> blocking in action.
- Store listing: describe it honestly. Do not use Instagram/YouTube logos or imply affiliation. Use the screenshots from a real phone.
- Assets in this repo: docs/logo.png (512x512 icon) and docs/feature_graphic.png (1024x500).

## 3. Keep in mind
- Instagram/YouTube can change their screens; detection may need a patch release. Bump `versionCode` each upload.
- Never add network calls, analytics SDKs or ads without updating the privacy policy and Data safety form.

## 4. Heads-up on the feed-skipping option
The Instagram "skip Reels / skip posts from accounts I don't follow" options use `canPerformGestures` (scroll swipes) in addition to Back/Home. Google is stricter about Accessibility apps that perform gestures in other apps. If the Permissions Declaration is rejected, remove `android:canPerformGestures` from `res/xml/accessibility_config.xml` and the `skip()` function in `GuardService.kt`; the Reels/Shorts blocking, limits, Sleep Mode and Focus Lock keep working. The disclosure text already mentions this behaviour, so keep it only if you keep the feature.
