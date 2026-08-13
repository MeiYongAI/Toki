# Toki

Toki 是面向官方 TikTok 46.3.x 的功能增强模块，使用 libxposed API 102。模块仅作用于
`com.zhiliaoapp.musically`，设置界面采用 Material 3。

> Toki 与 TikTok、ByteDance 和 LSPosed 项目均无隶属或背书关系。TikTok 内部实现会随
> 版本变化；升级 TikTok 前请先确认兼容性。

## 环境要求

- Android 8.0 或更高版本
- 支持 libxposed API 102 的 LSPosed 实现
- 官方 TikTok 46.3.x，包名为 `com.zhiliaoapp.musically`
- ARM64 与否不影响模块 APK；目标 TikTok 客户端本身仍需匹配设备架构

目前只维护上述官方目标包和 API，不对第三方修改、重打包或捆绑插件客户端提供兼容支持，
也不提供旧版 Xposed 入口或旧包设置迁移。

## 功能

- 地区与 SIM 信息伪装，支持地区、ISO 代码和运营商搜索。
- 解除下载限制，优先使用无水印地址，并分别设置视频、图片和 GIF 保存目录。
- 过滤信息流广告、直播、照片模式、长视频及不符合播放量/点赞条件的内容。
- 允许受限内容合拍与拼接；禁止循环播放时，视频结束后进入原生暂停状态，单击即可重新播放。
- 为每条新视频设置默认播放速度，可选 1.25x、1.5x、1.75x 和 2.0x；当前视频的手动倍速选择不会被覆盖。
- 在评论区显示“翻译/还原”按钮，并跨视频保持翻译状态。
- 双指长按切换防烧屏清屏模式，同时保留缩放和上下切换视频手势。
- Material 3 设置页，支持深色模式、动态配色和自适应布局。
- 通过右上角菜单使用 Root 权限重启 TikTok。

## 安装

1. 从 [Releases](https://github.com/MeiYongAI/Toki/releases/latest) 下载已签名 APK。
2. 安装 APK，在 LSPosed 中启用 Toki，并勾选 TikTok 作用域。
3. 强制停止并重新打开 TikTok。

修改设置后需要重启 TikTok 进程。只有设置页中的“重启 TikTok”操作需要额外授予
Magisk 或 KernelSU Root 权限，其余功能不主动申请 Root。

## 隐私

Toki 不声明网络权限，不包含统计、遥测、远程更新、外部 APK 下载或作者推广入口。
设置保存在本机，并通过 LSPosed 的远程首选项接口提供给目标进程。

## 构建

需要 JDK 21、Android SDK Platform 37.0 和 Build Tools 37.0.0。应用的
`targetSdk` 保持为 36：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

未配置签名环境变量时，`assembleRelease` 生成 unsigned APK。正式签名需要设置：

```text
TOKI_KEYSTORE_FILE
TOKI_STORE_PASSWORD
TOKI_KEY_ALIAS
TOKI_KEY_PASSWORD
```

然后运行：

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease
```

## 来源与许可证

项目最初基于 [TiktokPatchXposed](https://github.com/krolchonok/TiktokPatchXposed) 的
MIT 许可工程结构，现有 libxposed Hook、设置页及功能实现已针对 Toki 重写。仓库不包含
TikTok APK、反编译源码或第三方修改客户端。

本项目按 [MIT License](LICENSE) 发布。依赖与构建工具的许可证见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
