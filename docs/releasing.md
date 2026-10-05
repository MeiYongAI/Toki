# 发布与模块仓库同步

源码与正式版本在 [MeiYongAI/Toki](https://github.com/MeiYongAI/Toki) 维护。
[Xposed 模块仓库](https://github.com/Xposed-Modules-Repo/io.github.meiyongai.toki)
使用同一份 APK，不重新构建或签名。仅在个人仓库发布，不会自动同步到模块仓库。

## 发布流程

1. 完成项目要求的测试、Lint 和签名验证，在个人仓库发布正式 Release。
2. 附件必须包含 `app-release.apk` 和对应的 `SHA256SUMS.txt`。
3. 在安装了 GitHub CLI、PowerShell 7 和 Android SDK Build Tools 的电脑运行下列命令。
   `gh auth login` 的账号需要有两个仓库的读取权限及模块仓库的发布权限。

```powershell
# 先下载已发布的文件，核对摘要、包名、版本号和模块标识；不修改远程仓库。
./scripts/sync-module-release.ps1 -Tag v1.0.3 `
  -AaptPath "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/aapt.exe"

# 核验通过后，同步为 APK 实际版本号对应的 Release（本例为 4-1.0.3）。
./scripts/sync-module-release.ps1 -Tag v1.0.3 `
  -AaptPath "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/aapt.exe" -Publish
```

将标签和 Build Tools 路径替换为实际值。脚本下载文件到 `work/release-sync/`，
不使用工作区里的未发布 APK。它校验 GitHub 附件摘要和校验文件，读取 APK 的
包名、versionCode、versionName，并检查现代 Xposed 模块入口与 API 配置。
发布时先建立草稿，上传并核验附件，再公开。若对应目标标签已存在，脚本会停止，
不会覆盖现有版本；如上次中断留下草稿，先检查该草稿再决定后续操作。

本脚本是手动发布工具，不是后台定时同步任务，也不需要将本机登录令牌保存到仓库。
发布说明应包含支持范围、重启要求及签名变化；两个仓库需保持一致。

## 模块目录要求

- 仓库名必须与 APK 包名 `io.github.meiyongai.toki` 一致。
- GitHub 仓库 Description 应包含模块名称 Toki，且不能为空。
- 至少有一个公开 Release，标签格式为 `versionCode-versionName`。
- APK 附件类型必须为 `application/vnd.android.package-archive`。
- `SOURCE_URL` 指向源码仓库，`SUMMARY` 描述当前支持范围。

官方说明：<https://github.com/Xposed-Modules-Repo/.github/blob/main/profile/README.md>。
只修改 Release 附件不会触发校验机器人；修订已有版本时需同时更新 Release 内容。
网站目录更新取决于其 webhook 和构建服务，发布成功不代表索引已经更新。
若超过申请回复中给出的等待时间仍未收录，在原申请中提供包名、Release 链接和时间，
请管理员检查事件接收、缓存及构建状态。
