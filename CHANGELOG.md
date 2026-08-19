# Changelog

[English](#changelog) | [中文](#中文)

## 0.4.23

- Added a Home dashboard as the default destination with LSPosed service status and the installed TikTok version.
- Moved the Root-based TikTok restart action to Home and added Root-only cache clearing that preserves accounts, settings, drafts, and app data.
- Added manual interface language switching between Follow System, English, and Chinese, plus an action to reset all Toki settings.
- Added direct GitHub, Telegram, and issue-report links and expanded navigation to Home, General, Feed, and Downloads.
- Added an independent page-purification control for game-related entrances.

## 0.4.22

- Added an option to keep the video progress bar visible on standard videos shorter than 30 seconds; the existing hide-progress-bar option takes priority.
- Added optional author-location display with the matching country flag and region code beside the author name.
- Split author-avatar and author-information purification into independent controls.
- Updated TikTok 46.4.3 purification paths for the LIVE entry, search entry, top and bottom navigation, music title, and video feedback surveys.
- Added grouped purification controls for commercial and promotion labels, creative tools and templates, movie and anime entrances, sharing and creator incentives, and activity safety warnings.
- Moved feed payload, label, anchor, survey, and warning gates into a dedicated hook module and removed stale decompiler aliases and obsolete warning paths.

## 0.4.21

- Added an optional page-purification control to hide the system status bar on TikTok's main video pages.
- Applied the status-bar control only to TikTok 46.4.3's `MainActivity` and kept it active across layout updates.
- Used Android's `WindowInsetsController` on Android 11+ with a legacy system-UI fallback for Android 8–10.

## 0.4.20

- Set official TikTok 46.4.3 as the sole implementation, testing, and maintenance target.
- Other TikTok versions are outside the support scope and receive no version-specific compatibility work.
- Removed 46.3.x download-location and comment-translation compatibility code.
- Split hook installation into isolated feature classes while keeping one module APK and entry point.
- Extended TikTok 46.4.3 For You filtering to feed dispatch and cache-backed list insertion paths.
- Preserved the exact text entered in view-count and like-count range fields while keeping numeric filtering unchanged.
- Added an option to disable offline cold-cache use when a network is available.

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

### 0.4.23

- 新增首页并设为默认页面，可查看 LSPosed 服务状态与已安装的 TikTok 版本。
- 将基于 Root 的 TikTok 重启操作移至首页，并新增仅清除缓存的 Root 操作；账号、设置、草稿和应用数据均会保留。
- 新增界面语言手动切换，可选择跟随系统、English 或中文，并可一键重置全部 Toki 配置。
- 新增 GitHub、Telegram 与问题反馈直达入口，导航扩展为首页、常规、信息流和下载。
- 新增独立的游戏相关入口页面净化选项。

### 0.4.22

- 新增“总是显示视频进度条”，移除普通视频短于 30 秒时隐藏进度条的限制；与隐藏进度条同时开启时，以隐藏为准。
- 新增作者位置显示，可在作者昵称旁显示对应国家/地区旗帜和地区代码。
- 将作者头像与作者信息拆分为两个独立的页面净化选项。
- 更新 TikTok 46.4.3 的直播入口、搜索入口、顶部与底部导航栏、音乐标题和视频评价问卷净化路径。
- 新增商业合作与推广、创作工具与模板、影视与动漫、分享与创作者激励、活动伤害警告等分类型净化选项。
- 将 Feed 数据、标识、锚点、问卷和警告 Hook 整理到独立模块，并清除反编译伪包名与失效的旧警告路径。

### 0.4.21

- 新增页面净化选项，可隐藏 TikTok 主视频页面的手机系统状态栏。
- 状态栏功能仅作用于 TikTok 46.4.3 的 `MainActivity`，并在页面布局更新后保持生效。
- Android 11 及以上使用 `WindowInsetsController`，Android 8–10 使用兼容性的系统 UI 标记。

### 0.4.20

- 将官方 TikTok 46.4.3 设为唯一的实现、测试与维护目标。
- 其他 TikTok 版本不在支持范围内，不再提供针对版本的专门适配。
- 移除 46.3.x 保存位置和评论翻译兼容代码。
- 将 Hook 按功能拆分为相互隔离的代码类，同时保持单一模块 APK 和入口。
- 将 TikTok 46.4.3 推荐页过滤扩展到信息流消息分发和缓存列表插入路径。
- 保留播放量与点赞数范围输入的原始文本，过滤计算仍使用解析后的数值。
- 增加联网时禁用离线冷缓存的选项。

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
