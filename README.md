# Toki

[English](#toki) | [中文](#中文) | [Changelog](CHANGELOG.md) | [更新日志](CHANGELOG.md#中文) | [Telegram](https://t.me/toki_lsposed)

Toki is a libxposed API 102 enhancement module implemented and tested for official TikTok 46.4.3.
It only targets `com.zhiliaoapp.musically`.

## Features

- Region and environment spoofing: choose region presets with country codes and carrier
  information, and independently spoof GPS, system language, and system time zone.
- Startup login prompt: close the dismissible prompt shown during startup without bypassing
  login or verification.
- Download enhancements: remove download restrictions, prefer watermark-free saving for all
  videos, and set separate save locations for videos, images, and GIFs.
- Feed filtering: hide ads, livestreams, photo posts, AI-generated content, trending-topic bars,
  content ratings, and long videos; filter by view or like count.
- Duet and Stitch: allow restricted content to be used for Duet or Stitch. When looping is
  disabled, playback pauses at the end and can be replayed with one tap.
- Playback controls: automatically apply 1.25x to 2.0x to each new video without overriding a
  manual speed choice, and optionally keep the progress bar visible on videos under 30 seconds.
- Feed display: optionally show the author's region code with its matching country flag.
- Comment translation: show Translate and Revert controls in comments and preserve translation
  state across videos.
- Page purification: independently hide author avatar and information, descriptions, music,
  action buttons, navigation, search, LIVE, commercial and creative entrances, feedback surveys,
  safety warnings, Tako, translation controls, and the system status bar.
- Count ranges: enter full numbers or compact suffixes such as `20K` and `1.5M` directly.
- Metric range fields preserve the exact text entered while filtering uses the parsed count.
- Material 3 settings UI with dark mode and dynamic color support.

## Requirements

- Android 8.0 or later
- An LSPosed implementation that supports libxposed API 102
- Official TikTok 46.4.3 with package name `com.zhiliaoapp.musically`. Other versions are outside
  the support scope and receive no version-specific compatibility work or guarantees.
- The module APK is architecture-independent.

## Installation

1. Download the APK from [Releases](https://github.com/MeiYongAI/Toki/releases/latest).
2. Install it, enable Toki in LSPosed, and select the TikTok scope.
3. Tap the restart button in the settings app bar. Use it again after changing settings for
   changes to take effect. Restart TikTok manually when Root access is unavailable.

Toki does not request Root access except for the Restart TikTok action in its settings screen.

Community: [Telegram group](https://t.me/toki_lsposed)

## Reporting Issues

- Use the [Bug Report form](https://github.com/MeiYongAI/Toki/issues/new?template=bug_report.yml)
  for reproducible failures. Include the Toki, TikTok, Android, device, and LSPosed versions,
  exact reproduction steps, relevant settings, and sanitized LSPosed module logs.
- Use the [Feature Request form](https://github.com/MeiYongAI/Toki/issues/new?template=feature_request.yml)
  for concrete improvements based on an actual use case.
- Use the [Telegram group](https://t.me/toki_lsposed) for general discussion and help identifying
  a problem before filing it. Do not post account data, tokens, cookies, or unredacted private logs.

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

## 中文

Toki 是面向官方 TikTok 46.4.3 实现并完成测试的 libxposed API 102 功能增强模块，仅作用于
`com.zhiliaoapp.musically`。其他版本不在支持范围内，不提供专门适配或兼容保证。

### 功能

- 地区与环境伪装：选择预设地区（含国家/地区码与运营商信息），可独立伪装 GPS、系统语言和系统时区，并可强制指定地区。
- 启动登录引导：可关闭启动时可跳过的登录提示，不绕过登录或验证。
- 下载增强：解除下载限制，所有视频优先使用无水印地址，可分别设置视频、图片和 GIF 保存目录。
- 信息流过滤：隐藏广告、直播、图文帖、AI 生成内容、热点话题条、内容评级提示和长视频，并按播放量/点赞数过滤。
- 合拍与拼接：允许受限内容合拍或拼接；关闭循环播放后视频进入暂停状态，单击即可重播。
- 播放控制：为新视频自动应用 1.25x–2.0x，不覆盖当前视频内手动选择的倍速，并可让短于 30 秒的视频始终显示进度条。
- 信息显示：可在作者昵称旁显示对应国家/地区旗帜和地区代码。
- 评论翻译：评论区显示“翻译/还原”按钮，并跨视频保持翻译状态。
- 页面净化：可分别隐藏作者头像与信息、文案、音乐、互动按钮、导航、搜索、直播、商业与创作入口、评价问卷、伤害警告、Tako、翻译控件和手机系统状态栏。
- 数量范围：支持直接输入完整数字或 `20K`、`1.5M` 等数量后缀。
- 数据筛选输入会保留用户输入的原始文本，过滤时使用解析后的数值。
- Material 3 设置界面，支持深色模式与动态配色。

### 环境要求

- Android 8.0 或更高版本
- 支持 libxposed API 102 的 LSPosed 实现
- 官方 TikTok 46.4.3，包名 `com.zhiliaoapp.musically`；其他版本不在支持范围内
- 模块 APK 不区分设备架构

### 安装

1. 从 [Releases](https://github.com/MeiYongAI/Toki/releases/latest) 下载 APK。
2. 安装后在 LSPosed 中启用 Toki，并勾选 TikTok 作用域。
3. 在设置页右上角点击重启按钮；每次修改设置后也可一键重启使其生效。没有 Root 权限时，请手动重启 TikTok。

除设置页内的“重启 TikTok”外，模块不会主动申请 Root 权限。

交流：[Telegram 群组](https://t.me/toki_lsposed)

### 问题反馈

- 可稳定复现的异常请使用 [错误报告表单](https://github.com/MeiYongAI/Toki/issues/new?template=bug_report.yml)，
  并填写 Toki、TikTok、Android、设备和 LSPosed 版本、完整复现步骤、相关设置及已脱敏的模块日志。
- 有明确使用场景的改进建议请使用 [功能建议表单](https://github.com/MeiYongAI/Toki/issues/new?template=feature_request.yml)。
- 一般交流或尚未确认的问题可先在 [Telegram 群组](https://t.me/toki_lsposed)讨论。
  请勿公开账号资料、Token、Cookie 或未经脱敏的私人日志。

### 隐私

模块不声明网络权限，无统计、遥测、远程更新或推广入口，设置仅保存在本机。

### 构建

需要 JDK 21、Android SDK Platform 37.0 与 Build Tools 37.0.0。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

未配置签名环境变量时 `assembleRelease` 生成未签名 APK；正式发布需设置以下环境变量后运行
`.\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease`：

```text
TOKI_KEYSTORE_FILE
TOKI_STORE_PASSWORD
TOKI_KEY_ALIAS
TOKI_KEY_PASSWORD
```

### 来源与许可证

项目最初基于 [TiktokPatchXposed](https://github.com/krolchonok/TiktokPatchXposed) 的 MIT
许可工程结构，现有实现已针对 Toki 重写；仓库不包含 TikTok APK 或反编译源码。

本项目按 [MIT License](LICENSE) 发布，依赖与构建工具许可证见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

### 免责声明

本项目仅供学习、研究和个人设备使用，与 TikTok、ByteDance 及 LSPosed 项目无隶属或认可
关系。TikTok 版本更新可能导致功能失效，使用前请确认符合当地法律及相关服务条款。
