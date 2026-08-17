# Toki

Toki 是面向官方 TikTok 46.3.3 的 libxposed API 102 功能增强模块，仅作用于
`com.zhiliaoapp.musically`。

## 功能

- 地区伪装：选择预设地区（含国家/地区码与运营商信息），可强制指定地区。
- 下载增强：解除下载限制并优先使用无水印地址，可分别设置视频、图片和 GIF 保存目录。
- 信息流过滤：隐藏广告、直播、图文帖和长视频，并按播放量/点赞数过滤。
- 合拍与拼接：允许受限内容合拍或拼接；关闭循环播放后视频进入暂停状态，单击即可重播。
- 默认播放速度：为新视频自动应用 1.25x–2.0x，不覆盖当前视频内手动选择的倍速。
- 评论翻译：评论区显示“翻译/还原”按钮，并跨视频保持翻译状态。
- 防烧屏：双指长按切换清屏模式，保留缩放与滑动切换手势。
- Material 3 设置界面，支持深色模式与动态配色。

## 环境要求

- Android 8.0 或更高版本
- 支持 libxposed API 102 的 LSPosed 实现
- 官方 TikTok 46.3.3，包名 `com.zhiliaoapp.musically`；模块 APK 不区分设备架构

## 安装

1. 从 [Releases](https://github.com/MeiYongAI/Toki/releases/latest) 下载 APK。
2. 安装后在 LSPosed 中启用 Toki，并勾选 TikTok 作用域。
3. 强制停止并重新打开 TikTok；之后每次修改设置也需重启生效。

除设置页内的“重启 TikTok”外，模块不会主动申请 Root 权限。

## 隐私

模块不声明网络权限，无统计、遥测、远程更新或推广入口，设置仅保存在本机。

## 构建

需要 JDK 21、Android SDK Platform 37.0 与 Build Tools 37.0.0。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

未配置签名环境变量时 `assembleRelease` 生成未签名 APK；正式发布需设置以下环境变量后
运行 `.\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease`：

```text
TOKI_KEYSTORE_FILE
TOKI_STORE_PASSWORD
TOKI_KEY_ALIAS
TOKI_KEY_PASSWORD
```

## 来源与许可证

项目最初基于 [TiktokPatchXposed](https://github.com/krolchonok/TiktokPatchXposed) 的 MIT
许可工程结构，现有实现已针对 Toki 重写；仓库不包含 TikTok APK 或反编译源码。

本项目按 [MIT License](LICENSE) 发布，依赖与构建工具许可证见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 免责声明

本项目仅供学习、研究和个人设备使用，与 TikTok、ByteDance 及 LSPosed 项目无隶属或认可
关系。TikTok 版本更新可能导致功能失效，使用前请确认符合当地法律及相关服务条款。
