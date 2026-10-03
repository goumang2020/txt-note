# 本地验证记录

日期：2026-10-03。构建主机：Apple Silicon macOS，JDK 21，Xcode 26.5。

| 检查 | 结果 |
| --- | --- |
| 原版文件 SHA-256 校验 | 79 / 79 一致 |
| 笔记仓库测试 | 12 项通过（含主题持久化、备份恢复与旧数据兼容） |
| TXT / TSV 交换测试 | 5 项通过 |
| 文件存储与损坏恢复测试 | 2 项通过 |
| Compose 界面测试 | 3 项通过（含预设及自定义主题色切换） |
| 主题对比度测试 | 1 项通过（六种预设和自定义极端颜色） |
| macOS ARM64 DMG | 本机打包成功 |
| Android debug APK | 构建成功 |
| macOS `.app`（ARM64） | 构建成功，已启动并确认界面显示 |
| iOS 模拟器整包（ARM64） | Kotlin 框架、SwiftUI 入口及整包构建成功 |

界面测试覆盖中文和多行正文输入、通过编辑界面保存、全文搜索，以及未保存修改的离开确认。

实际使用的构建命令：

```bash
source scripts/java-env.sh
./gradlew :androidApp:assembleDebug :composeApp:desktopTest :composeApp:createDistributable
./scripts/build-ios.sh
./gradlew -PdesktopOnly=true :composeApp:packageDmg
```

测试报告：`composeApp/build/reports/tests/desktopTest/index.html`。

共 23 项测试通过。原版 79 个文件的备份校验保持一致。

Android / iOS 尚未连接真机运行。本机没有可用的 iOS 模拟器运行时，iOS 验证为编译和链接整包，不包括模拟器启动。Windows / Linux 的本地运行未验证。

远端构建记录：[GitHub Actions](https://github.com/goumang2020/txt-note/actions)。标签触发的发布产物在 [GitHub Releases](https://github.com/goumang2020/txt-note/releases) 中提供 APK、ARM64 / Intel DMG 和 SHA-256 校验值。
