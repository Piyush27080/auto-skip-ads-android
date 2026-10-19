# Changelog

All notable changes to this project are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/).

## [1.0.0] - 2026-10-19
### Added
- New dark app screen in the style of the Chrome extension: status, ON/OFF switch, **Ads skipped**, **Time saved**, **Reset stats**
- **Mute ads** option: mutes the media volume while an ad is on screen
- **Fast-forward ads (experimental)** option for ads with no Skip button
- App icon (adaptive icon, with a themed version for Android 13+)
- Help card for phones that stop background apps (Xiaomi, Redmi, Oppo, Vivo)
- GitHub Actions workflow that attaches the APK to a GitHub Release when a `v*` tag is created
- Fixed debug signing key, so new versions install over old ones

### Changed
- An ad is counted as skipped when the Skip button disappears after the tap, not on every tap
- The detailed screen log is now off by default (setting **Save debug log**)

## [0.3.0]
### Added
- Taps the Skip button when it appears (click, with a tap-gesture fallback)
- Counter of Skip taps

## [0.2.0]
### Added
- Status screen (switch on/off, service connected)
- Log saved inside the app, with a **Share log** button

## [0.1.0]
### Added
- First version: an Accessibility Service that only watches the YouTube apps and writes a log
