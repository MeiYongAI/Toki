# Toki

<img src="https://raw.githubusercontent.com/MeiYongAI/Toki/main/docs/assets/toki.svg" align="right" width="96" height="96" alt="Toki 图标">

面向 TikTok 的 LSPosed 模块，让 TikTok 更合你的使用习惯。

[![构建](https://github.com/MeiYongAI/Toki/actions/workflows/build.yml/badge.svg)](https://github.com/MeiYongAI/Toki/actions/workflows/build.yml)
[![许可证：MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/MeiYongAI/Toki/blob/main/LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84.svg)](#使用条件)

[English](README.md) · **简体中文**

[下载](https://github.com/MeiYongAI/Toki/releases) ·
[模块仓库](https://github.com/Xposed-Modules-Repo/io.github.meiyongai.toki) ·
[更新日志](https://github.com/MeiYongAI/Toki/blob/main/CHANGELOG.md) ·
[问题反馈](https://github.com/MeiYongAI/Toki/issues/new/choose) ·
[Telegram 群组](https://t.me/toki_lsposed)

Toki 提供信息流过滤、播放控制、媒体工具和区域设置，采用 Material 3 界面，
提供 57 种界面语言选项。

> **AI 生成声明：** 本模块使用 AI 生成的代码进行开发，可能存在错误。
> 请在使用前了解变更并充分测试，不要将其视为可靠性保证。
>
> **隐藏应用列表（HMA）：** 请勿向 TikTok 隐藏 Toki。Toki 不提供 HMA 兼容性支持，
> 详情见[常见问题](#常见问题)。

## 目录

- [安装](#安装)
- [使用](#使用)
- [主要功能](#主要功能)
- [常见问题](#常见问题)
- [开发](#开发)
- [参与贡献](#参与贡献)
- [支持开发](#支持开发)
- [免责声明](#免责声明)
- [许可证](#许可证)

## 安装

#已验证的构建直接使用内置适配结果，首次启动无需查找。其他构建使用原生方法查找，完成后需重启。

## 使用条件

| 条件 | 支持范围 |
| --- | --- |
| Android | Android 9（API 28）及以上 |
| 框架 | 已正常运行且支持 **libxposed API 102** 的 LSPosed |
| TikTok | 官方客户端；已适配版本：**47.1.4**、**47.0.3**、**46.8.3** |
| 模块作用域 | `com.zhiliaoapp.musically` 或 `com.ss.android.ugc.trill` |

Toki **目前仅支持官方版 TikTok**，不支持或适配 TikTok Central 等第三方修改版。

Toki 需要框架才能对 TikTok 生效。其他 TikTok 版本不在文档所述的兼容范围内。
不同下载渠道的构建可能包含不同代码，因此版本号相同也不代表所有功能
都能正常使用。

### 启用模块

1. 从 [Releases](https://github.com/MeiYongAI/Toki/releases) 下载并安装 Toki APK。
2. 在 LSPosed 中启用 Toki，并在模块作用域中勾选已安装的 TikTok。
3. 打开 Toki，检查首页的激活状态，并选择需要启用的功能。
4. 强行停止 TikTok 后重新打开。如果出现方法查找窗口，请等待完成；
   成功后点击「知道了」关闭提示，再手动强行停止 TikTok 并重新打开，以应用适配结果。

## 使用

在 Toki 中设置功能、切换界面语言、查看功能状态，或从首页导入、导出配置。

例如，想过滤广告时，在信息流设置中启用广告过滤，重启 TikTok；
打开 TikTok 后，再回到 Toki 查看该功能的状态。

- **按提示重启：** 新启用的功能，以及地区伪装、播放倍速和沉浸布局的设置变化，
  可能需要新的 TikTok 会话。功能状态页提示需要重启时，请强行停止 TikTok 后重新打开。
- **查看功能状态：** 状态页区分已请求的设置与本次会话实际应用的设置，
  并报告适配失败。已安装的过滤、清屏等功能会在会话中接收配置更新。
- **备份配置：** 重装或大幅调整设置前先导出配置，需要时从首页导入。

## 主要功能

| 分类 | 功能 |
| --- | --- |
| 信息流过滤 | 跨视频列表过滤广告，包含作者主页。推荐页支持过滤直播、图文、标记为 AI 生成的视频与照片、话题及创作者推荐卡片，按关键词、作者、时长、播放量和点赞量筛选，并阻止离线视频插入。 |
| 页面布局净化 | 顶栏、底栏、视频页面独立隐藏控件，包括金币奖励、活动卡片、停止连播按钮，并支持控件透明度调整。 |
| 播放控制 | 自定义倍速、自动清屏、全屏播放、进度条设置与跨视频页面的自动连播增强。 |
| 媒体与工具 | 优先使用无水印下载地址、自定义保存目录、音频限制处理、翻译选项，以及评论原文或译文复制。 |
| 区域设置 | 在 TikTok 内伪装地区、SIM 卡、语言、时区与位置，显示作者地区。 |
| 便捷管理 | 功能状态查看、配置导入导出、自动查找适配方法，以及 57 种界面语言选项。 |

## 常见问题

### 方法查找完成后如何生效

查找成功后，点击「知道了」关闭提示，再手动强行停止 TikTok 并重新打开，以应用保存的结果。
如果方法查找失败，请在 Toki 查看功能诊断并[反馈问题](#参与贡献)。

### 扫描窗口出现前 TikTok 就闪退

如果使用了隐藏应用列表（HMA），请检查是否向 TikTok 隐藏了 Toki
（`io.github.meiyongai.toki`）。已有旧实现的用户日志确认，隐藏 Toki 后，
扫描窗口初始化时查询不到模块包并引发闪退。当前源码已修改相关资源加载路径，
但受影响设备及其 HMA 配置尚未完成实机验证。

请取消对 TikTok 隐藏 Toki 的规则，或停用 TikTok 的 HMA 隐藏规则，
然后强行停止 TikTok 并重新打开。**Toki 不提供 HMA 兼容性支持，
使用时请勿向 TikTok 隐藏 Toki。**

如果仍然闪退，请在[错误报告](#参与贡献)中补充 HMA 版本，
并附上同一次启动的 LSPosed 日志和崩溃日志。

### 修改设置后没有效果

检查 Toki 是否已启用、LSPosed 作用域是否勾选了正确的 TikTok 包，
以及 Toki 中对应功能是否开启。按功能状态页提示重启；
如果适配失败，反馈时请注明 TikTok 版本和下载渠道。

## 开发

[MeiYongAI/Toki](https://github.com/MeiYongAI/Toki) 统一维护源码、问题反馈和正式版本。
[Xposed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.meiyongai.toki)
分发同一份已签名 APK，供模块目录收录。维护者发布时请按
[版本同步说明](https://github.com/MeiYongAI/Toki/blob/main/docs/releasing.md)同步两个仓库。

### 开发环境

| 组件 | 版本或要求 |
| --- | --- |
| IDE | Android Studio，或 Android SDK 命令行工具 |
| Gradle 运行环境 | JDK 21；Java 源码与目标级别为 17 |
| Android SDK | Platform 37（`platforms;android-37.0`）、Build Tools 37.0.0 |
| 构建插件 | Android Gradle Plugin 9.4.0；Kotlin Compose 插件 2.2.10 |
| Gradle | 9.7.1，项目已提供 Wrapper |
| 应用 SDK 级别 | `compileSdk = 37`、`targetSdk = 35`、`minSdk = 28` |

依赖从 Google Maven 和 Maven Central 获取，Gradle 插件还使用 Gradle Plugin Portal。
在本机将 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 设置为 JDK 21，
通过 `ANDROID_HOME` 或本地 `local.properties` 指定 SDK。仓库不保存特定电脑的路径。

### 构建与测试

克隆仓库，并安装 CI 使用的 SDK 组件：

```bash
git clone https://github.com/MeiYongAI/Toki.git
cd Toki
sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
```

构建 Debug APK，并运行与 [CI](https://github.com/MeiYongAI/Toki/blob/main/.github/workflows/build.yml) 一致的检查任务：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Windows PowerShell 使用：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

如需检查 Release 变体，用 Wrapper 运行 `:app:lintRelease`。
Release 构建启用 R8 和资源压缩。

| 构建类型 | APK 输出路径 |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| 未配置签名的 Release | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| 已配置签名的 Release | `app/build/outputs/apk/release/app-release.apk` |

### Release 签名

将 [keystore.properties.example](https://github.com/MeiYongAI/Toki/blob/main/keystore/keystore.properties.example) 复制为
`keystore/keystore.properties`，填写自己的密钥路径、别名和凭据。
密钥路径相对于仓库根目录；没有该配置文件时，Release APK 不签名，签名后才能安装。

请私下备份并保护自己的密钥和 `keystore/keystore.properties`。
覆盖更新同一个已安装应用需要使用相同的签名密钥。项目公开的发布证书指纹为：

```text
SHA-256 34:22:83:7D:EA:4C:40:EC:06:1F:8E:59:94:70:51:2E:87:23:4A:28:4B:C8:51:E1:A6:EA:87:3A:9E:DE:5D:32
```

### 源码结构与适配机制

| 路径 | 用途 |
| --- | --- |
| `app/src/main/java/io/github/meiyongai/toki/` | 模块入口、Hook、配置与 Compose 界面 |
| `app/src/main/res/` | Android 资源与翻译 |
| `app/src/main/resources/META-INF/xposed/` | Xposed 模块入口、API 要求与作用域 |
| `app/src/main/resources/toki-host-rules.tsv` | 宿主代码指纹规则 |
| `app/src/test/java/io/github/meiyongai/toki/` | 单元测试及资源、布局测试 |

<details>
<summary>功能安装与方法查找行为</summary>

Toki 按启动时的配置选择要安装的功能。全部主功能关闭时，保留会话诊断，
不安装业务 Hook 或启动适配扫描。SIM、语言、时区、GPS、下载路径调整、状态栏和时长提示
不需要符号扫描；无水印下载需要适配选源方法。保留的进度条子选项或地区预设不会单独启用其主功能。

官方宿主构建统一按实际代码指纹与成员契约匹配，
不按版本号或下载渠道选择适配分支；代码不匹配时会报告适配失败。

</details>

## 参与贡献

请通过 [GitHub Issues](https://github.com/MeiYongAI/Toki/issues/new/choose)
提交错误报告和功能建议，也可以加入 [Telegram 群组](https://t.me/toki_lsposed)交流。

反馈问题时，请提供：

- Toki 与 TikTok 版本，以及 TikTok 下载渠道。
- Android 版本、设备型号和 LSPosed 版本。
- 复现步骤、相关设置、预期结果与实际结果。
- 方法查找是否完成、功能状态及相关 LSPosed 日志；闪退时提供同一次启动的崩溃日志。

分享日志或截图前请移除个人信息，按 Issue 模板填写，不上传 TikTok APK 或宿主反编译源码。

欢迎提交 Pull Request。请说明要解决的问题、修改后的行为和验证方式。
提交前运行单元测试和 Lint；涉及文档所述行为时同步更新中英文 README，
不要提交构建产物、本机 SDK 路径或签名凭据。

## 支持开发

如果 Toki 对你有帮助，可以通过 [Ko-fi](https://ko-fi.com/meiyongai)
或[支付宝](https://github.com/MeiYongAI/Toki/blob/main/app/src/main/res/drawable-nodpi/alipay.jpg)支持开发，
也可在 Toki 首页的「支持开发」中找到赞助入口。赞助完全自愿。

<details>
<summary>USDT · TRC20 / Tron</summary>

`TXoTeZLpbQdn4wZF51858bC3zCwS822HbB`

仅使用 TRC20（Tron）网络，转账前请核对地址与网络。

</details>

## 免责声明

Toki 是独立项目，与 TikTok 或字节跳动不存在隶属或背书关系。
本模块会修改应用行为，按现状提供，不保证兼容性、稳定性或账号安全。
使用风险由用户自行承担，请遵守适用法律及平台条款。
尊重创作者权益，仅在获得许可后下载或再利用内容。

## 许可证

Copyright © 2026 MeiYongAI。本项目采用 [MIT 许可证](https://github.com/MeiYongAI/Toki/blob/main/LICENSE)，
依赖许可与致谢见[第三方声明](https://github.com/MeiYongAI/Toki/blob/main/THIRD_PARTY_NOTICES.md)。
