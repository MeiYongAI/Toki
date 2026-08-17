# Changelog

[English](CHANGELOG.md) | [中文](CHANGELOG.zh-CN.md)

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
