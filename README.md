# Auto Skip Ads for Android (work in progress)

An Android app that taps the **Skip** button for you when a YouTube or YouTube Music ad shows one, so ads do not keep playing when your hands are busy.

> **Status:** early development. The current version (v0.1) only **watches** the YouTube apps and writes what it sees to the developer log, to find the exact Skip button. Tapping Skip comes next.

## How it works

- The app is an Android **Accessibility Service**. It only watches two apps: `com.google.android.youtube` and `com.google.android.apps.youtube.music`.
- When a Skip button appears on screen, the service taps it. The timing does not matter: it waits for the button, whether it shows after 5, 10 or 15 seconds.
- No muting, no fast-forwarding, no internet access.

## Download the APK

Every push builds a new APK with GitHub Actions.

1. Open the **Actions** tab of this repository and click the latest run with a green check.
2. Scroll down to **Artifacts** and download `auto-skip-ads-debug-apk`.
3. Unzip it. The file inside is `app-debug.apk`.

## Install on your phone

1. Copy `app-debug.apk` to your phone and open it. Allow "Install unknown apps" for the app you opened it from. If Google Play Protect warns you, choose **Install anyway**.
2. Open **Auto Skip Ads** and tap **Open Accessibility settings**.
3. Find **Auto Skip Ads** in the list, turn it on, and confirm.
4. On Android 13 and newer, if the switch is greyed out, go to **Settings → Apps → Auto Skip Ads**, tap the three dots at the top right, and choose **Allow restricted settings**. Then try step 3 again.

## Privacy

- The service can read what is on the screen, so it is limited to the two YouTube apps in `accessibility_service_config.xml`.
- v0.1 writes on-screen text from those apps to the local developer log (Logcat) for debugging only. Nothing is stored or sent anywhere. This debug logging will be removed before the Skip feature is finished.
- The app has no internet permission.

## Limitations

- The screen must be on. An Accessibility Service cannot see a locked or turned-off screen.
- Ads without a Skip button cannot be skipped.
- The Skip button's text or position can change when YouTube updates its apps, so the app may need updates.
- Google Play restricts apps that use the Accessibility API for purposes other than helping people with disabilities, so this app is shared as an APK here instead of on Google Play.

## Build it yourself

- **In the cloud:** push to `main`, or run the workflow by hand (**Actions → Build Android APK → Run workflow**).
- **Locally:** open the project in Android Studio, or run `./gradlew assembleDebug`.

## Roadmap

- [x] Accessibility Service that logs what the YouTube apps show
- [ ] Find and tap the Skip button
- [ ] On/off switch and counter of skipped ads
- [ ] Release APK on GitHub Releases

## Disclaimer

Educational project, not affiliated with YouTube or Google. Skipping ads may go against YouTube's Terms of Service. Use at your own risk.

## Author

Made by **Piyush**.
