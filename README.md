# 浮悬浏览器

![浮悬浏览器图标](branding/floating_browser_icon_minimal_white.png)

> **白底极简的原生 Kotlin Android 浏览器。** 以网页内容为中心，使用底部低干扰控制区、多标签浏览和独立本地搜索首页；不使用 Expo、React Native 或 Jetpack Compose。

[![Platform](https://img.shields.io/badge/platform-Android-14213D?style=flat-square)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-2563EB?style=flat-square)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-06B6D4?style=flat-square)](LICENSE)

## 设计原则

浮悬浏览器将网页置于视觉中心。导航、标签和工具不隐藏，但以更轻的底部控件长期保留；应用图标采用白底、深海军蓝浏览器轮廓、蓝色导航点与青色指针，不依赖文字、渐变、拟物阴影或第三方品牌标记。

| 维度 | 设计选择 |
|---|---|
| 产品定位 | 轻量、多标签、原生 Android WebView 浏览器 |
| 搜索体验 | 新标签显示本地独立搜索页；提交关键词后才跳转 Bing |
| 控制布局 | 底部地址栏、椭圆形后退/前进/刷新组、新建标签与 Tab 入口 |
| Tab 模式 | 设置中固定选择“始终极简”或“始终完整” |
| 工具栏 | 从 Tab 区上方弹出的独立双列菜单，支持空白处与返回键关闭 |
| 视觉语言 | 毛玻璃低对比度控制区，支持浅色、深色、跟随系统；启动图标为白底极简样式 |

## 功能

| 模块 | 当前能力 |
|---|---|
| 多标签 | 创建、切换、关闭 WebView 标签；极简模式提供标签计数选择器，完整模式显示可切换的标签条。 |
| 地址与搜索 | 支持 HTTP(S) 地址、常见域名和 Bing 搜索；首页不会自动加载搜索结果。 |
| Tab 工具 | 添加/移除书签、下载管理、分享、复制网址、清缓存、回首页、关闭标签、刷新和设置。 |
| 网页设置 | JavaScript、桌面版网站、主题、标签显示模式与浏览数据清理。 |
| 网页下载 | WebView 下载请求交由 Android `DownloadManager`；下载管理页可刷新状态并打开完成文件。 |
| 下载通知诊断 | 可发送独立测试通知；下载后先发布探针通知，再按需启动 `dataSync` 前台服务；下载管理页显示最后诊断状态。 |
| WebView 安全 | 禁止混合内容、文件访问和内容访问；非 HTTP(S) 协议交由系统处理。 |

> Android 16+ 是否将进度通知提升为系统实时动态通知，取决于系统策略、用户设置与设备实现。无论是否被提升，普通系统通知栏中可见的测试通知和下载进度探针才是基本验收条件。[1] [2]

## 应用标识与环境

| 项目 | 当前值 |
|---|---|
| 应用 ID / namespace | `com.mmckb.browser` |
| 显示名称 | 浮悬浏览器 |
| 最低 Android 版本 | Android 7.0（API 24） |
| compileSdk / targetSdk | 36 |
| Java / Kotlin JVM 目标 | 17 |
| Gradle / Android Gradle Plugin | 8.11.1 / 8.9.1 |
| Kotlin | 2.0.21 |

## 本地构建

准备 Android SDK 与 JDK 17 后，在仓库根目录运行：

```bash
./gradlew :app:assembleDebug
```

资源受限环境可使用单工作线程进行完整验证：

```bash
ANDROID_HOME=/path/to/android-sdk \
ANDROID_SDK_ROOT=/path/to/android-sdk \
JAVA_HOME=/path/to/jdk-17 \
./gradlew clean :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
```

调试 APK 默认输出至：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目的发布与调试签名通过 `release.keystore` 和环境变量配置；不要将密钥库或密码提交到仓库。请参考 `.gitignore` 与现有 CI 配置。

## 下载通知验证

1. 打开 `⋮ → 设置 → 下载实时通知`，并允许系统通知权限。
2. 点击 `测试下载实时通知`。若该通知可见，说明应用通知渠道和系统权限链路可用。
3. 开始一个不会瞬间结束的网页文件下载。应用会先发布探针通知，再按 `DownloadManager` 返回的真实字节进度更新。
4. 如未看到通知，打开 `⋮ → 下载管理` 并查看顶部的“实时通知诊断”。它会标识权限、探针、`startForeground`、任务监控或进度更新的最后状态。

## 项目结构

```text
app/src/main/java/com/mmckb/browser/
├── MainActivity.kt                     # 浏览器、标签、地址栏、工具栏、设置与内嵌下载管理
├── DownloadProgressService.kt          # 按需 dataSync 前台下载进度服务
├── DownloadNotificationDiagnostics.kt  # 测试通知、探针与应用内诊断状态
└── DownloadStore.kt                    # DownloadManager 任务 ID 本地存储

app/src/main/res/                        # 主题、字符串、启动图标与适配资源
branding/floating_browser_icon_minimal_white.png  # 白底极简图标主资源
```

## 许可证

本仓库以 [MIT License](LICENSE) 发布。Android SDK、AndroidX、Material Components、目标网页与 Bing 服务分别受其自身许可证或服务条款约束。

## 参考资料

[1] [Android Developers：实时动态通知](https://developer.android.google.cn/design/ui/mobile/guides/home-screen/live-updates?hl=zh-CN)

[2] [Android Developers：通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)

[3] [Android Developers：WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
