# Changelog

[English](#changelog) | [中文](#中文)

## 0.4.19

- Added compatibility for the comment-translation control and playback-completion handling on official TikTok 46.4.3.
- Fixed custom video, image, and GIF save locations on TikTok 46.3.2 and 46.4.3 by intercepting TikTok's MediaStore insertion bridge.
- Replaced the Android system directory picker with a built-in relative shared-storage path editor.
- Extended trending-topic and promotional-overlay purification for TikTok 46.4.3.
- Known issue: view-count and like-count filtering remains unreliable on TikTok 46.4.3 and may let some out-of-range videos through.

## 0.4.18

- Reworked the Material 3 settings screen and organized features into General, Feed, and Downloads sections.
- Added page purification with optional controls for author details, descriptions, music, action buttons, search, Tako, translation controls, and navigation bars.
- Added filters for AI-generated content, trending-topic bars, and content-rating prompts.
- Added independent GPS, system-language, and system-time-zone spoofing that follows the selected target region.
- Added an option to skip the startup login guide by dismissing skippable prompts only; it does not bypass login or verification.
- Improved view-count and like-count filters with support for full numbers and `K`/`M`/`B` suffixes.
- Removed the failed anti-burn-in feature; the standard default playback-speed option is now displayed as `1.0x`.
- Clarified download wording to state that all videos prefer watermark-free URLs.

## 0.4.17

- Added support for official TikTok 46.3.2 and 46.3.3.
- Fixed the comment translation button not executing translation on TikTok 46.3.2.
- Adapted the anti-burn-in status Toast entry for TikTok 46.3.2.

## 0.4.16

- Improved anti-burn-in clear-screen state retention and restoration across videos and photo posts.
- Confirmed compatibility with official TikTok 46.3.3.
- Updated the launcher icon with a solid-color background.

## 0.4.15

- Limited support to the official TikTok client and removed compatibility code for modified clients.
- Removed grayscale mode and forced unmute settings that depended on third-party client bridges.
- Improved loop disabling so playback enters TikTok's native paused state and shows the replay frame.
- Fixed the need for two taps to replay a video and the feature becoming inactive after switching videos.
- Stopped forcing progress-bar synchronization to reduce reliance on high-frequency callbacks.

## 0.4.14

- Added default playback speeds: 1.0x, 1.25x, 1.5x, 1.75x, and 2.0x.
- Applied the selected speed to each new video after its first render without overriding manual speed changes.
- Adapted to the official TikTok 46.3.3 player interface.

## 0.4.13

- Switched to libxposed API 102 and fixed the TikTok scope.
- Reworked the Material 3 settings UI, region selection, and media-directory selection.
- Added comment-translation state retention and scrolling-list synchronization.
- Added the two-finger long-press anti-burn-in clear-screen mode.
- Fixed the need for two taps to replay a video after loop disabling.
- Added the Root-based Restart TikTok action.

## 中文

### 0.4.19

- 适配官方 TikTok 46.4.3 的评论翻译控件与播放完成处理。
- 通过拦截 TikTok 的 MediaStore 写入桥，修复 TikTok 46.3.2 与 46.4.3 的视频、图片和 GIF 自定义保存位置。
- 移除 Android 系统目录选择器，改用内置的共享存储相对路径编辑框。
- 扩展 TikTok 46.4.3 的热点话题与推广浮层净化兼容。
- 已知问题：TikTok 46.4.3 的播放量与点赞数筛选仍不可靠，少数范围外视频可能漏过过滤。

### 0.4.18

- 重构 Material 3 设置页，按常规、信息流和下载分类组织功能。
- 新增页面净化，可选择隐藏作者信息、文案、音乐、互动按钮、搜索入口、Tako、翻译控件和导航栏。
- 新增屏蔽 AI 生成内容、热点话题条和内容评级提示。
- 新增 GPS、系统语言和系统时区伪装，均可独立开关并跟随目标地区。
- 新增跳过启动登录引导，仅关闭启动时可跳过的登录提示，不绕过登录或验证。
- 优化播放量与点赞量筛选，支持输入完整数字及 `K/M/B` 数量后缀。
- 移除失败的防烧屏功能；默认倍速的标准项统一显示为 `1.0x`。
- 优化下载说明，明确所有视频优先使用无水印地址。

### 0.4.17

- 支持官方 TikTok 46.3.2 与 46.3.3。
- 修复 TikTok 46.3.2 评论页翻译按钮无法执行翻译的问题。
- 适配 TikTok 46.3.2 的防烧屏状态提示 Toast 入口。

### 0.4.16

- 改进防烧屏清屏模式在视频与图集中的状态保持和恢复，增强页面切换后的稳定性。
- 明确适配官方 TikTok 46.3.3。
- 更新启动器图标，使用纯色背景。

### 0.4.15

- 明确仅支持官方 TikTok，移除第三方修改客户端专用兼容代码。
- 移除仅依赖第三方客户端桥接、在官方 TikTok 中无效的灰度模式和强制取消静音设置。
- 优化禁止循环播放：播放结束后进入 TikTok 原生暂停状态，并显示重播首帧。
- 修复播放结束后需要点击两次才能重新播放，以及切换视频后功能失效的问题。
- 不再强制同步播放进度条，减少对 TikTok 高频进度回调的依赖。

### 0.4.14

- 新增默认播放速度：1.0x、1.25x、1.5x、1.75x 和 2.0x。
- 每条新视频首次渲染后自动应用所选速度，不覆盖当前视频内手动选择的倍速。
- 适配官方 TikTok 46.3.3 播放器接口。

### 0.4.13

- 使用 libxposed API 102，并固定 TikTok 作用域。
- 重构 Material 3 设置界面、地区选择和媒体保存目录选择。
- 增加评论翻译状态保持及滚动列表同步。
- 增加双指长按防烧屏清屏模式。
- 修复禁止循环播放后需要点击两次才能重新播放的问题。
- 增加 Root 重启 TikTok 操作。
