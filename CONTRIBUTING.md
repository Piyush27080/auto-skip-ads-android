# Contributing

Thanks for your interest in improving Auto Skip Ads for Android!

## Reporting a bug
1. Open an [issue](../../issues/new/choose) using the bug report template.
2. Tell me your phone model, Android version, which app you used (YouTube or YouTube Music), and the app version (shown at the bottom of the app screen).
3. If the Skip button is not tapped: turn on **Save debug log** in the app, play an ad until the Skip button shows, then tap **Share log**. Read the log before sharing, because it contains text from your screen, and remove anything private.

## Suggesting a feature
Open an issue and describe what you would like and why it would help.

## Sending a pull request
1. Fork the repository and create a branch: `git checkout -b feature/my-change`
2. Build and test on a real phone. Push to your fork and GitHub Actions will build an APK for you.
3. Keep the code simple. Comment anything that depends on YouTube's screen layout, such as view names like `skip_ad_button`.
4. Update `CHANGELOG.md` and open a pull request describing what you changed.

## When YouTube changes its app
If the Skip button stops being tapped, the ad layer's view names probably changed. Turn on **Save debug log**, play an ad, and look in the log for the lines with `skip` or `ad_progress`. The matching code is in `SkipService.kt`, in the `inspect` function.
