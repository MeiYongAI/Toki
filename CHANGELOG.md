# Changelog

## 1.0.4 — 2026-10-08

### English

- Faster first setup: verified TikTok builds use bundled adaptation results; other builds use native method discovery.
- Added Center and Fullscreen video modes. Fullscreen crops suitable portrait videos while keeping landscape videos centered.
- Expanded layout cleanup and added separate control opacity settings, including the top navigation indicator.
- Fixed the progress bar disappearing on profile videos in automatic clean mode.
- Added a Belarus SIM preset and a background audio unlock option; simplified access to the native Captions and translation menu.
- Refined settings and translations across supported languages.

Update directly from **1.0.1–1.0.3**; the signing key is unchanged. Restart TikTok after updating. If method discovery runs, restart again when it finishes. Official TikTok clients only; subtitle availability still depends on TikTok.

### 中文

- 加快首次适配：已验证的 TikTok 构建直接使用内置适配结果，其他构建使用原生方法查找。
- 新增「居中」和「全屏沉浸」视频显示选项，适合的竖屏视频可裁切铺满，横屏视频保持居中。
- 扩展页面布局净化，新增分区域控件透明度设置，覆盖顶部导航指示条。
- 修复作者主页视频在播放清屏时无法保留进度条的问题。
- 新增白俄罗斯 SIM 预设和后台音频解锁选项，简化原生「字幕和翻译」入口解锁。
- 精简设置文案，完善多语言适配。

可直接覆盖升级 **1.0.1–1.0.3**，沿用原签名。更新后重启 TikTok；如触发方法查找，完成后再次重启。目前仅支持官方版 TikTok，字幕是否可用仍由 TikTok 决定。


## 1.0.3 — 2026-10-04

### English

- Added layout cleanup with separate top navigation, bottom navigation and video-page controls, including the stop auto-scroll button.
- Enhanced native auto scroll across video pages, including profile and Following feeds.
- Reuse valid method-discovery results after module updates; only new or changed rules are scanned when TikTok is unchanged.
- Fixed missing seek-time numbers and visual search tags in automatic clean mode.
- Updated translations and kept module settings accessible through LSPosed when the launcher icon is hidden.

Update directly from **1.0.1 / 1.0.2**; the signing key is unchanged. Restart TikTok after updating and again if method discovery runs. Official TikTok clients only.

### 中文

- 新增页面布局净化，顶栏、底栏、视频页面独立设置，支持隐藏停止连播按钮。
- 增强原生自动连播，支持作者主页、关注页等视频页面。
- 优化方法缓存复用：TikTok 未更新时，模块更新仅补查新增或变化的规则。
- 修复自动净屏时拖动时间数字不显示、暂停后“搜同款”标签不出现的问题。
- 完善多语言文案，隐藏桌面图标后仍可从 LSPosed 打开模块设置。

可直接覆盖升级 **1.0.1 / 1.0.2**，沿用原签名。更新后重启 TikTok，如触发方法查找，完成后再次重启。目前仅支持官方版 TikTok。

## 1.0.2 — 2026-10-04

### English

- Added official TikTok **47.1.4** support and improved adaptation across **47.0.3** and **46.8.3**.
- Improved method discovery reliability and speed, including download, filtering, playback speed, comment copying, author region and auto-scroll adaptation.
- Fixed the video description translation unlock interfering with translation availability.
- Improved immersive layout and centered photo posts while preserving their aspect ratio.
- Fixed controls remaining invisible after pausing in automatic clean mode.

Update directly from **1.0.1**; the signing key is unchanged. Restart TikTok after updating and again after method discovery completes. Official TikTok clients only.

### 中文

- 新增官方 TikTok **47.1.4** 适配，完善 **47.0.3**、**46.8.3** 多版本适配。
- 提升方法查找稳定性与速度，完善下载、过滤、倍速、评论复制、作者地区及自动滚动适配。
- 修复视频内容翻译解锁影响翻译可用性的问题。
- 优化全屏沉浸，图文保持比例居中显示。
- 修复自动净屏暂停后部分控件仍不可见的问题。

可直接覆盖升级 **1.0.1**，沿用原签名。更新后重启 TikTok，方法查找完成后再次重启。目前仅支持官方版 TikTok。

## 1.0.1 — 2026-10-03

### 中文

- 修复 TikTok 47.0.3 部分视频无水印下载生成 0 字节 `null.mp4` 的问题：在官方下载选源阶段使用同一视频的有效播放源，保留官方文件名、下载进度和保存流程。
- 状态栏隐藏支持直播观看页，并在返回前台、恢复窗口焦点和配置变化时保持生效。
- 优化方法查找窗口的留白，并修复开始查找时进度条短暂显示满格的问题。
- 按启用功能安装 Hook 和查找所需方法，统一代码指纹匹配，并串行化适配缓存读写、校验提交结果。
- 包名为 `io.github.meiyongai.toki`，版本码为 `2`。
- **本版使用新正式签名，不能直接覆盖安装原签名的 1.0.0。请先导出 Toki 配置，再卸载旧版、安装本版、导入配置，并在 LSPosed 中确认启用及作用域后重启 TikTok。仅卸载 Toki，不需要卸载 TikTok。**
- 无水印下载和直播状态栏隐藏已通过用户实机复测。直播顶部黑色留白保持宿主布局。

### English

- Fixed zero-byte `null.mp4` downloads on TikTok 47.0.3 by resolving a valid playback source during native watermark-free source selection. Native naming, progress and saving remain in use.
- Added status-bar hiding on live viewing pages, including resume, window focus and configuration changes.
- Reduced excess spacing in method discovery and fixed the initial full-progress flash.
- Install hooks and discover methods according to enabled features, use shared code fingerprints, and serialize adaptation-cache access with commit verification.
- Package: `io.github.meiyongai.toki`; version code: `2`.
- **This release uses a new signing certificate and cannot update the original signed 1.0.0 in place. Export Toki settings, uninstall Toki, install this release, import settings, confirm LSPosed activation and scope, then restart TikTok. Do not uninstall TikTok.**
- Download and live status-bar fixes were verified on-device by the user. The live page's top black spacing remains unchanged.

## 1.0.0 — 2026-09-30

### English

- New module identity: `io.github.meiyongai.toki`, version code `1`, with a new release signing certificate. This installs separately from `com.seepd.toki`; disable the 0.x module in LSPosed before enabling 1.0.0. Settings are not migrated automatically.
- Independent rewrite: the entire 1.0.0 source tree is new code and no longer contains the 0.x `com.seepd.toki` sources.
- TikTok 47.0.3 and 46.8.3 adaptation, with method discovery and persistent results keyed to the host code and rules.
- Feed filters for topic and creator recommendation cards, keywords, duration, counts, offline insertion and AI-labeled videos or photos. Ad filtering also covers creator-profile video lists; other content rules remain limited to For You.
- Playback and clean-mode controls, progress-bar handling, translation and original/translated comment copying.
- Material 3 interface with 57 language options, feature status, configuration import/export and donation-image saving.
- Local validation: 252 unit tests passed; lint reported 0 errors and 28 warnings. Release builds use R8 and resource shrinking. The latest profile-ad changes still need device verification.

### 中文

- 新模块包名为 `io.github.meiyongai.toki`，版本号 `1`，使用新的正式签名。与 `com.seepd.toki` 分开安装；请先在 LSPosed 中停用 0.x 模块，再启用 1.0.0，配置不会自动迁移。
- 独立重写：1.0.0 的全部源码均为新代码，不再包含 0.x `com.seepd.toki` 的源文件。
- 适配 TikTok 47.0.3、46.8.3，支持方法查找，并按宿主代码与规则标识持久保存结果。
- 信息流过滤涵盖话题及创作者推荐卡片、关键词、时长、数量范围、离线插入，以及标记为 AI 生成的视频和照片。广告过滤扩展到作者主页的视频列表，其他内容规则仍只作用于推荐页。
- 完善播放清屏、进度条处理、翻译与评论原文／译文复制。
- Material 3 界面提供 57 种语言选项、功能状态查看、配置导入导出及赞助图片保存。
- 本地验证：252 项单元测试通过，Lint 0 错误、28 警告；Release 启用 R8 与资源压缩。最新作者主页广告过滤调整仍待实机验证。


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
