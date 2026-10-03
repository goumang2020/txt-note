# txtNote · Kotlin Multiplatform

将原 Windows Phone / C# 项目迁移为 Kotlin Multiplatform + Compose Multiplatform。Android、iOS 和桌面端共用笔记模型、存储、文件交换和 Compose 界面；桌面支持 macOS、Windows、Linux。

原版的全部 79 个文件在 **`legacy/windows-phone/`**，`backup-manifest.json` 记录逐文件 SHA-256，Git 历史保持原样。

## 已实现

- 新建、编辑、重命名、删除笔记；日常 / 工作 / 兴趣分类。
- 名称搜索、全文搜索；按修改时间、创建时间、名称排序；笔记置顶。
- 多选、批量删除、合并导出；导入多份 TXT / TSV。
- UTF-8（含 BOM）和 UTF-16（带 BOM）文本导入；导出 UTF-8。
- 系统剪贴板复制 / 粘贴；Android / iOS 系统分享；桌面通过文件导出分享。
- 图片附件、自定义背景、中 / 英 / 日语言切换、深浅色主题、字号设置。
- 六种主题色（森林绿、海洋蓝、鸢尾紫、暖橘色、玫瑰粉、石墨灰），支持自定义 `#RRGGBB` 颜色；立即生效，重启和备份恢复后保留。
- Android 自动保存：输入停顿、返回列表和切到后台时保存，不弹出保存确认；空白新建页返回不产生文件。桌面 / iOS 保留未保存修改确认。
- 文件名可留空：按正文第一句（或第一行）生成，过滤无效字符并限制长度，自动处理同名；可随时手动重命名。标题生成逻辑集中在 `suggestedNoteName`，方便后续替换为大模型摘要。
- 本地草稿恢复。
- JSON 完整备份与追加恢复，包含分类、时间、图片、设置及草稿。同名导入自动重命名。
- 临时文件写入后原子替换，保留上一代数据；主文件损坏时从上一代恢复，并保留损坏文件。

## 运行桌面端

需要 JDK 17 或更高版本（推荐 21）。Gradle Wrapper 已包含，不需要安装 Gradle。

```bash
# macOS / Linux：自动找到本机 JDK；没有时下载到用户缓存。
./scripts/bootstrap.sh
./scripts/run-desktop.sh
```

Windows 设置 `JAVA_HOME` 为 JDK 17+，然后：

```powershell
.\gradlew.bat -PdesktopOnly=true :composeApp:run
```

仅运行桌面端时使用 `-PdesktopOnly=true`，无需 Android SDK 或 Xcode。

```bash
./gradlew -PdesktopOnly=true :composeApp:desktopTest
./gradlew -PdesktopOnly=true :composeApp:createDistributable
./gradlew -PdesktopOnly=true :composeApp:packageDistributionForCurrentOS
```

打包输出在 `composeApp/build/compose/binaries/main/`。macOS 生成 `.app` / DMG，Windows 生成 MSI，Linux 生成 DEB；各安装包需在对应操作系统上构建，架构与构建机器一致。

## Android

Android 7.0+（API 24），编译 / 目标 SDK 36。用 Android Studio 打开根目录，安装 Android SDK 36，并在 `local.properties` 配置 `sdk.dir` 或设置 `ANDROID_HOME`。

```bash
./scripts/build-android.sh
# 或
./gradlew :androidApp:assembleDebug
```

开发 APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。

构建正式 Release APK：

```bash
./scripts/build-android-release.sh
./scripts/verify-release-apk.sh androidApp/build/outputs/apk/release/androidApp-release.apk
```

输出为 `androidApp/build/outputs/apk/release/androidApp-release.apk`。Release 禁用调试，开启 R8 优化和资源压缩，使用固定私钥签名。脚本从本机 `~/.config/txtnote/signing/signing.json` 和 `release.p12` 读取凭据，也可以提供 `ANDROID_SIGNING_KEYSTORE`、`ANDROID_SIGNING_STORE_PASSWORD`、`ANDROID_SIGNING_KEY_ALIAS`、`ANDROID_SIGNING_KEY_PASSWORD` 环境变量。私钥和密码均不进入 Git。

**请安全备份 `~/.config/txtnote/signing/`**，后续版本必须使用同一签名。仓库中的 `androidApp/release-cert.pem` 只包含公开证书，验证脚本检查签名一致且 APK 不可调试。

系统文件选择器支持设备上可用的云盘提供程序，无需授予全盘存储权限。

## GitHub Releases 与 CI/CD

安装包在 [GitHub Releases](https://github.com/goumang2020/txt-note/releases) 下载：

| 文件 | 平台 |
| --- | --- |
| `txtNote-<版本>-android-release.apk` | Android 7.0+，固定签名的 Release 包 |
| `txtNote-<版本>-macos-arm64.dmg` | Apple Silicon Mac，内含 Java 运行时 |
| `txtNote-<版本>-macos-x64.dmg` | Intel Mac，内含 Java 运行时 |
| `SHA256SUMS.txt` | 安装包的 SHA-256 校验值 |

macOS 安装包暂未使用 Apple Developer 证书签名或公证。Android 使用固定 Release 签名，后续 Release 版本可覆盖升级。旧 `v2.0.1` debug 包与正式签名不同：请先导出 JSON 备份，卸载 debug 包，安装 Release 包后恢复。

普通 `master` 推送和 Pull Request 触发 `.github/workflows/build.yml`，检查 Windows / Linux / macOS 桌面测试和打包、Android APK、iOS 整包。

发布一个版本：

```bash
# 先将全部代码推送到 GitHub，再推送发布标签。
git tag -a v2.0.2 -m 'txtNote 2.0.1'
git push origin v2.0.2
```

`vMAJOR.MINOR.PATCH` 标签会触发 `.github/workflows/release.yml`。流程完成测试、构建 APK 和两种 DMG 后，自动创建 GitHub Release 并上传安装包及校验值。标签中的版本会传给 Gradle，设置 Android 和 macOS 安装包版本。

也可以在 GitHub Actions 中手动运行 **Release APK and DMG**，填写已存在的版本标签。重复运行会重新上传该版本的安装包。自动发布使用工作流自带的 `GITHUB_TOKEN`；写权限只授予发布 job。

Android 签名使用四个仓库 Actions Secrets：`ANDROID_KEYSTORE_BASE64`（PKCS12 私钥文件的 base64）、`ANDROID_SIGNING_STORE_PASSWORD`、`ANDROID_SIGNING_KEY_ALIAS`、`ANDROID_SIGNING_KEY_PASSWORD`。这些已经配置；私钥只在构建 runner 的临时目录还原，构建后删除。缺少凭据会明确失败，不会降级到 debug 签名。PR 检查同时编译 Debug 和未签名 Release，不向 PR 提供签名私钥。

## iOS

iOS 17.2+，iPhone / iPad；支持真机 ARM64 和 Apple Silicon 模拟器。需要 macOS、JDK 17+、Xcode，以及 Xcode 对应的 iOS 平台组件。

1. 打开 `iosApp/iosApp.xcodeproj`，选择 `txtNote` scheme。
2. 选择已安装的模拟器；真机运行时在 Signing & Capabilities 中选择自己的开发团队。
3. 点击 Run。Xcode 构建阶段会调用 Gradle，生成并链接共享 Kotlin 框架。

只检查共享代码、不依赖 Android SDK：

```bash
./gradlew -PiosOnly=true :composeApp:compileKotlinIosSimulatorArm64
./gradlew -PiosOnly=true :composeApp:linkDebugFrameworkIosSimulatorArm64
# 编译 SwiftUI 入口和 ARM64 模拟器整包（不需要已安装的模拟器运行时）
./scripts/build-ios.sh
```

`-PiosOnly=true` 保留 iOS 和桌面目标，关闭 Android 目标。

模拟器整包输出：`iosApp/build/Debug-iphonesimulator/txtNote.app`。

## 从旧版迁移笔记

仓库包含原版源代码，不包含手机上的笔记数据库。新版本不会直接读取 Windows Phone 的 SQL CE `.sdf` 文件。

- 单篇笔记：在旧版导出 / 下载 `.txt`，在新版点击 **导入 TXT**。
- 旧版合并文件：导入原名为 `txtNote_Merge.txt` 的文件，按 `文件名<TAB>正文` 解析。若文件已改名，请恢复此文件名，或改为 `.tsv`。
- Excel 表格：保存为 UTF-8 TSV 后导入，第一列为文件名，第二列及后续内容为正文。
- 普通 TXT 中的制表符保持为正文，不会被误拆成多篇笔记。
- 新版合并导出增加 `# txtNote TSV v2` 标识，并转义换行、制表符和反斜杠，能够完整再次导入。旧版合并格式的歧义无法完全消除。

单篇 TXT 只包含正文；需要保留分类、图片、时间和草稿时使用 **备份**。恢复采用追加方式，不覆盖现有笔记；空笔记本会采用备份中的设置，已有笔记本保持当前设置。

原版 SkyDrive Live SDK、Windows Phone 动态磁贴、微博任务接口和拍照 API 没有直接移植。对应工作流采用系统文件选择 / 分享、笔记置顶和系统图片选择；没有后台云同步。Web 目标未包含。

## 数据与结构

| 位置 | 用途 |
| --- | --- |
| `composeApp/src/commonMain` | 模型、仓库、文件交换、共享界面 |
| `composeApp/src/desktopMain` | JVM 入口、文件选择器、剪贴板 |
| `composeApp/src/androidMain` | Android 文件与分享适配 |
| `composeApp/src/iosMain` | iOS 文档选择器、分享适配与 UIKit 入口 |
| `androidApp` | Android 启动模块 |
| `iosApp` | SwiftUI 启动模块与 Xcode 工程 |
| `legacy/windows-phone` | 未改动的原版备份 |

存储路径可在应用的 **设置** 中查看：macOS 使用 `~/Library/Application Support/txtNote`，Windows 使用 `%APPDATA%/txtNote`，Linux 使用 `$XDG_DATA_HOME/txtNote`（默认 `~/.local/share/txtNote`），移动端使用应用沙盒。

主数据为 `notebook.json`，恢复副本为 `notebook.previous.json`。草稿在输入停止 250ms 后写入；界面显示“草稿已保留”表示本次写入完成。单份文本最多 20MB、图片最多 10MB、备份最多 100MB。

依赖版本固定为 Kotlin 2.2.20、Compose Multiplatform 1.9.3、Gradle 8.14.3、AGP 8.11.1，按 [Kotlin 兼容说明](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) 与 [Compose 兼容说明](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) 选择。Wrapper 校验 Gradle 下载文件的 SHA-256。
