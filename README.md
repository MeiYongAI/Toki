# Toki

<img src="https://raw.githubusercontent.com/MeiYongAI/Toki/main/docs/assets/toki.svg" align="right" width="96" height="96" alt="Toki icon">

An LSPosed module that gives you more control over your TikTok experience.

[![Build](https://github.com/MeiYongAI/Toki/actions/workflows/build.yml/badge.svg)](https://github.com/MeiYongAI/Toki/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/MeiYongAI/Toki/blob/main/LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84.svg)](#dependencies)

**English** · [简体中文](README.zh-CN.md)

[Download](https://github.com/MeiYongAI/Toki/releases) ·
[Module repository](https://github.com/Xposed-Modules-Repo/io.github.meiyongai.toki) ·
[Changelog](https://github.com/MeiYongAI/Toki/blob/main/CHANGELOG.md) ·
[Report a problem](https://github.com/MeiYongAI/Toki/issues/new/choose) ·
[Telegram](https://t.me/toki_lsposed)

Toki provides feed filters, playback controls, media tools and region settings
through a Material 3 interface with 57 language options.

> **AI-generated project.** This module is developed with AI-generated code and
> may contain errors. Review changes and test carefully before relying on it.
>
> **Hide My Applist (HMA):** Do not hide Toki from TikTok. Toki does not provide
> HMA compatibility support. See [Troubleshooting](#troubleshooting).

## Table of contents

- [Install](#install)
- [Usage](#usage)
- [Features](#features)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [Contributing](#contributing)
- [Support](#support)
- [Disclaimer](#disclaimer)
- [License](#license)

## Install

### Dependencies

| Requirement | Supported configuration |
| --- | --- |
| Android | Android 9 (API 28) or newer |
| Framework | A working LSPosed installation supporting **libxposed API 102** |
| TikTok | Official client; adapted versions: **47.1.4**, **47.0.3**, and **46.8.3** |
| Module scope | `com.zhiliaoapp.musically` or `com.ss.android.ugc.trill` |

Toki supports **official TikTok clients only**. Third-party modified clients,
including TikTok Central, are not supported or adapted.

Toki requires the framework to apply changes to TikTok. Other TikTok versions
are outside the documented compatibility scope. Download
variants can contain different code, so a listed version alone does not
guarantee that every feature will work.

### Enable the module

1. Download and install the Toki APK from
   [Releases](https://github.com/MeiYongAI/Toki/releases).
2. Enable Toki in LSPosed and select your installed TikTok in the module scope.
3. Open Toki, check the activation status on Home and choose the features you
   want to enable.
4. Force-stop TikTok and open it again. If a method-discovery dialog appears,
   let it finish and dismiss the completion message. Then force-stop TikTok
   and open it again to apply the saved results.

## Usage

Open Toki to configure features, change the interface language, view feature
status or import/export settings from Home.

For example, to filter ads, enable ad filtering in the feed settings, restart
TikTok and check the feature status in Toki after opening TikTok.

- **Restart when prompted.** Newly enabled features, region spoofing, playback
  speed and immersive layout changes can require a new TikTok session. When
  Toki indicates that a restart is needed, force-stop TikTok and open it again.
- **Check feature status.** The status page distinguishes your requested
  settings from those applied in the current session and reports adaptation
  failures. Installed filtering and clean-mode features receive configuration
  updates during the session.
- **Keep configuration backups.** Export settings before reinstalling or
  making substantial changes, and import them from Home when needed.

## Features

| Area | Options |
| --- | --- |
| Feed filters | Filter ads across video feeds, including creator profiles. On For You, filter LIVE, photos, AI-labeled videos/photos, topic/creator cards, keywords, authors, duration, views and likes; block offline video insertion. |
| Layout cleanup | Separate top navigation, bottom navigation and video-page controls, including the stop auto-scroll button. |
| Playback | Custom speeds, automatic clean mode, fullscreen playback, progress-bar options and enhanced auto scroll across video pages. |
| Media and tools | Prefer watermark-free downloads, set custom save folders, handle audio restrictions, configure translation and copy original or translated comment text. |
| Region | Spoof SIM/region, language, time zone and location within TikTok; display creator regions. |
| Management | Feature status, settings import/export, automatic method discovery and 57 interface language options. |

## Troubleshooting

### How to apply method-discovery results

After discovery succeeds, dismiss the completion message, force-stop TikTok
and open it again to apply the saved results. If discovery fails, check Toki's
feature diagnostics and [report the problem](#contributing).

### TikTok crashes before the discovery dialog appears

If you use Hide My Applist (HMA), check whether it hides Toki
(`io.github.meiyongai.toki`) from TikTok. Logs from the previous implementation
confirmed a startup crash when the discovery dialog could not find the hidden
module package. The current source changes the resource-loading path, but the
affected devices and HMA configuration have not been verified with that change.

Remove Toki from the apps hidden from TikTok, or disable HMA's hiding rules for
TikTok, then force-stop TikTok and reopen it. **Toki does not provide HMA
compatibility support. Do not hide Toki from TikTok while using the module.**

If the crash persists, include your HMA version and the LSPosed and crash logs
from the same launch with your [bug report](#contributing).

### A setting has no effect

Check that Toki is enabled, the correct TikTok package is selected in LSPosed
and the relevant feature is enabled in Toki. Follow any restart instruction on
the feature status page. If adaptation fails, include the TikTok version and
download source in your report.

## Development

[MeiYongAI/Toki](https://github.com/MeiYongAI/Toki) maintains the source code, issues and releases.
The [Xposed module repository](https://github.com/Xposed-Modules-Repo/io.github.meiyongai.toki)
distributes the same signed APK for the module catalog. Maintainers should follow
the [release synchronization instructions](https://github.com/MeiYongAI/Toki/blob/main/docs/releasing.md) when publishing.

### Environment

| Component | Version or requirement |
| --- | --- |
| IDE | Android Studio, or Android SDK command-line tools |
| Gradle runtime | JDK 21; Java source/target level is 17 |
| Android SDK | Platform 37 (`platforms;android-37.0`), Build Tools 37.0.0 |
| Build plugins | Android Gradle Plugin 9.4.0; Kotlin Compose plugin 2.2.10 |
| Gradle | 9.7.1, provided by the wrapper |
| App SDK levels | `compileSdk = 37`, `targetSdk = 35`, `minSdk = 28` |

Dependencies are resolved from Google Maven and Maven Central; Gradle plugins
also use the Gradle Plugin Portal. Set `JAVA_HOME` or Android Studio's Gradle
JDK to JDK 21, and configure the SDK through `ANDROID_HOME` or a local
`local.properties` file. Machine-specific paths stay outside version control.

### Build and test

Clone the repository and install the SDK packages used by CI:

```bash
git clone https://github.com/MeiYongAI/Toki.git
cd Toki
sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
```

Build a debug APK and run the same verification tasks as
[CI](https://github.com/MeiYongAI/Toki/blob/main/.github/workflows/build.yml):

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

In Windows PowerShell, use:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

For release-specific lint checks, run `:app:lintRelease` with the wrapper.
Release builds enable R8 and resource shrinking.

| Build | APK output |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release without signing configuration | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Release with signing configuration | `app/build/outputs/apk/release/app-release.apk` |

### Sign a release

Copy [keystore.properties.example](https://github.com/MeiYongAI/Toki/blob/main/keystore/keystore.properties.example) to
`keystore/keystore.properties`, then supply your own keystore path, alias and
credentials. Keystore paths are relative to the repository root. Without this
file, the release APK is unsigned and must be signed before installation.

Keep your keystore and `keystore/keystore.properties` private and backed up.
Updates to the same installed app require the same signing key. The project's
published release certificate fingerprint is:

```text
SHA-256 34:22:83:7D:EA:4C:40:EC:06:1F:8E:59:94:70:51:2E:87:23:4A:28:4B:C8:51:E1:A6:EA:87:3A:9E:DE:5D:32
```

### Source layout and adaptation

| Path | Purpose |
| --- | --- |
| `app/src/main/java/io/github/meiyongai/toki/` | Module entry point, hooks, configuration and Compose UI |
| `app/src/main/res/` | Android resources and translations |
| `app/src/main/resources/META-INF/xposed/` | Xposed module entry, API requirements and scope |
| `app/src/main/resources/toki-host-rules.tsv` | Host-code fingerprint rules |
| `app/src/test/java/io/github/meiyongai/toki/` | Unit and resource/layout tests |

<details>
<summary>Feature installation and method-discovery behavior</summary>

Toki selects features from the startup configuration. When all main features
are off, it keeps session diagnostics without business hooks or adaptation
scanning. SIM, language, time zone, GPS, download-path changes, status bar and duration
alerts do not require symbol scanning; watermark-free downloads require source-selection
adaptation. Saved progress-bar sub-options and
region presets do not enable their parent features.

Official host builds use a common set of code fingerprints and member contracts.
There are no adaptation branches selected by version number or download source.
Unmatched code is reported as an adaptation failure.

</details>

## Contributing

Use [GitHub Issues](https://github.com/MeiYongAI/Toki/issues/new/choose) for bug
reports and feature requests, or join the [Telegram group](https://t.me/toki_lsposed)
for questions and discussion.

For a bug report, include:

- Toki and TikTok versions, plus the TikTok download source.
- Android version, device model and LSPosed version.
- Reproduction steps, relevant settings, and expected/actual behavior.
- Whether method discovery completed, feature status and relevant LSPosed logs.
  For crashes, include crash logs from the same launch.

Remove personal information before sharing logs or screenshots. Follow the
issue templates; do not upload TikTok APKs or decompiled host code.

Pull requests are welcome. Describe the problem, resulting behavior and how
you verified the change. Run unit tests and lint before submitting, update
both README translations when changing documented behavior, and keep generated
build files, local SDK paths and signing credentials out of commits.

## Support

If Toki is useful to you, you can support development through
[Ko-fi](https://ko-fi.com/meiyongai) or
[Alipay](https://github.com/MeiYongAI/Toki/blob/main/app/src/main/res/drawable-nodpi/alipay.jpg). These options are also
available in Toki under **Support Toki**. Donations are optional.

<details>
<summary>USDT · TRC20 / Tron</summary>

`TXoTeZLpbQdn4wZF51858bC3zCwS822HbB`

Use the TRC20 (Tron) network only. Verify the address and network before sending.

</details>

## Disclaimer

Toki is an independent project, not affiliated with or endorsed by TikTok or
ByteDance. It modifies app behavior and is provided **as is**, without warranties
of compatibility, reliability or account safety. Use it at your own risk and
follow applicable laws and platform terms. Respect creators' rights; download
or reuse content only with permission.

## License

Copyright © 2026 MeiYongAI. Licensed under the [MIT License](https://github.com/MeiYongAI/Toki/blob/main/LICENSE).
Dependency credits and licenses are listed in
[Third-party notices](https://github.com/MeiYongAI/Toki/blob/main/THIRD_PARTY_NOTICES.md).
