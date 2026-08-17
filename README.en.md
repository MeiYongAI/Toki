# Toki

[English](README.en.md) | [中文](README.md)

Toki is a libxposed API 102 enhancement module for the official TikTok 46.3.2 and 46.3.3.
It only targets `com.zhiliaoapp.musically`.

## Features

- Region spoofing: choose from presets with country or region codes and carrier information,
  with an option to restrict the feed to the target region.
- Download enhancements: remove download restrictions, prefer watermark-free URLs, and set
  separate save locations for videos, images, and GIFs.
- Feed filtering: hide ads, livestreams, photo posts, and long videos; filter by view or like
  count.
- Duet and Stitch: allow restricted content to be used for Duet or Stitch. When looping is
  disabled, playback pauses at the end and can be replayed with one tap.
- Default playback speed: automatically apply 1.25x to 2.0x to each new video without
  overriding a manual speed choice for the current video.
- Comment translation: show Translate and Revert controls in comments and preserve translation
  state across videos.
- Anti burn-in: use a two-finger long press to toggle clear-screen mode while retaining zoom and
  swipe-to-switch gestures.
- Material 3 settings UI with dark mode and dynamic color support.

## Requirements

- Android 8.0 or later
- An LSPosed implementation that supports libxposed API 102
- Official TikTok 46.3.2 or 46.3.3 with package name `com.zhiliaoapp.musically`. The module APK is
  architecture-independent.

## Installation

1. Download the APK from [Releases](https://github.com/MeiYongAI/Toki/releases/latest).
2. Install it, enable Toki in LSPosed, and select the TikTok scope.
3. Force-stop and reopen TikTok. Restart TikTok after changing settings for changes to take
   effect.

Toki does not request Root access except for the Restart TikTok action in its settings screen.

## Privacy

Toki declares no network permissions and contains no analytics, telemetry, remote updates, or
promotional entry points. Settings are stored only on the device.

## Build

JDK 21, Android SDK Platform 37.0, and Build Tools 37.0.0 are required.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Without signing environment variables, `assembleRelease` produces an unsigned APK. For a signed
release, set the following variables and run
`.\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease`:

```text
TOKI_KEYSTORE_FILE
TOKI_STORE_PASSWORD
TOKI_KEY_ALIAS
TOKI_KEY_PASSWORD
```

## Origin and License

The project originally used the MIT-licensed project structure of
[TiktokPatchXposed](https://github.com/krolchonok/TiktokPatchXposed). Toki's implementation has
since been rewritten. This repository contains neither a TikTok APK nor decompiled source code.

This project is released under the [MIT License](LICENSE). Licenses for dependencies and build
tools are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

This project is intended only for learning, research, and use on personal devices. It is not
affiliated with or endorsed by TikTok, ByteDance, or LSPosed. TikTok updates can break
compatibility; make sure your use complies with local law and the applicable terms of service.
