# 浮悬浏览器

![浮悬浏览器图标](branding/floating_browser_icon_minimal_white.png)

> **白底极简的原生 Kotlin Android 浏览器。** 以网页内容为中心，使用底部低干扰控制区、多标签浏览和独立本地搜索首页；不使用 Expo、React Native 或 Jetpack Compose。

[![Platform](https://img.shields.io/badge/platform-Android-14213D?style=flat-square)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-2563EB?style=flat-square)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-06B6D4?style=flat-square)](LICENSE)

## 设计原则

浮悬浏览器将网页置于视觉中心。导航、标签和工具不隐藏，但以轻量的底部控件长期保留。默认“现在的”风格使用透出网页内容的磨砂椭圆 Tab；可在设置中改用更标准、表面更实的 Google MD3 风格。系统状态栏和导航栏始终可用。

| 维度 | 设计选择 |
|---|---|
| 产品定位 | 轻量、多标签、原生 Android WebView 浏览器 |
| 搜索体验 | 新标签显示本地独立搜索页；提交关键词后才跳转 Bing |
| 控制布局 | 底部等边距椭圆 Tab 容器、地址栏、椭圆形后退/前进/刷新组、新建标签与 Tab 入口；网页从容器下方透出。 |
| Tab 模式 | 设置中固定选择“始终极简”或“始终完整” |
| 工具栏 | 从 Tab 区上方弹出的独立双列菜单，支持空白处与返回键关闭 |
| 当前风格 | 半透明磨砂椭圆 Tab，Android 12+ 以网页局部快照实现模糊，旧系统使用透明降级层。 |
| Google MD3 风格 | 使用标准 Material 3 色面、圆角和间距；可配置调色板样式、颜色规格与动态颜色。 |

## 功能

| 模块 | 当前能力 |
|---|---|
| 多标签 | 创建、切换、关闭 WebView 标签；极简模式提供标签计数选择器，完整模式显示可切换的标签条。 |
| 地址与搜索 | 支持 HTTP(S) 地址、常见域名和 Bing 搜索；首页不会自动加载搜索结果。 |
| Tab 工具 | 添加/移除书签、下载管理、分享、复制网址、清缓存、回首页、关闭标签、刷新和设置。 |
| 主题 | 浅色、深色、跟随系统与独立界面风格设置。MD3 选中后会显示调色板样式、颜色规格和动态颜色开关；动态颜色仅在 Android 12+ 读取系统壁纸色，低版本自动回退至所选静态调色板。[3] |
| 返回手势 | 可选“无 / AOSP / Miuix / 缩放 / 经典”。设置、工具栏、下载确认、下载管理、应用信息、网页历史和多标签关闭均走统一返回分发；AOSP 在根页面交还系统返回桌面动画。[4] [5] |
| 网页下载 | 内置下载器将网页任务登记到 Android `DownloadManager`。下载管理页支持自动刷新、任务打开、底部多选与批量删除。 |
| 下载长按操作 | 已完成任务长按可打开、分享和重命名；进行中任务可取消并删除。Android 10+ 会尝试通过内容 URI 更新实际显示名称，无法修改时保留应用内列表名称并明确提示。 |
| 下载实时通知 | 内置下载开始后，按需启动 `dataSync` 前台服务；即使返回桌面，也会以真实文件名、下载字节、百分比和每秒字节差分速度持续更新。 |
| WebView 安全 | 禁止混合内容、文件访问和内容访问；非 HTTP(S) 协议交由系统处理。 |

> Android 16+ 是否将进度通知提升为系统实时动态通知，取决于系统策略、用户设置与设备实现。无论是否被提升，普通系统通知栏中可见、持续更新的下载进度通知才是基本验收条件。[1] [2]

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
./gradlew clean :app:assembleDebug :app:assembleRelease :app:lintDebug --no-daemon --max-workers=1
```

调试 APK 默认输出至：

```text
app/build/outputs/apk/debug/app-debug.apk
```

调试与发布构建均使用同一份 **MMCKB** 证书。仓库不包含私钥：本地使用被忽略的 `signing.properties`，GitHub Actions 使用 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS` 与 `KEY_PASSWORD` 机密。请勿提交密钥库、属性文件或口令。

## 下载与通知验收

1. 在网页触发下载，并在下载确认页选择 **应用内下载**。
2. 打开 `⋮ → 下载`，确认进行中任务在首个采样周期后显示 `B/s`、`KB/s`、`MB/s` 或 `GB/s`；顶部不再显示实时通知诊断文字，右上角只保留关闭按钮。
3. 使用左下角 **多选**，勾选多个任务后删除，再点 **取消多选** 退出选择状态。
4. 长按已完成任务，验证“打开 / 分享 / 重命名 / 删除”；长按进行中任务，验证“取消并删除”。
5. 返回桌面，确认通知显示文件名、下载字节、百分比和速度。若设备未显示通知，请先确认系统通知总开关及应用通知权限。[1] [2]

## 返回手势验收

1. 在 `⋮ → 设置 → 返回手势样式` 中分别选择五个选项。
2. 对工具栏、设置、下载确认、下载管理和应用信息页执行返回手势或返回键，确认每次都优先关闭当前最上层页面。
3. 对网页执行返回，确认先回 WebView 历史、再回本地首页、再关闭额外标签。
4. 在 AOSP 模式的根首页执行返回，确认 Android 13+ 系统能够接管回到桌面的预测性预览；较低系统使用兼容返回行为。[4]

## 项目结构

```text
app/src/main/java/com/mmckb/browser/
├── MainActivity.kt                     # 浏览器、标签、地址栏、工具栏、主题、统一返回与下载管理
├── DownloadProgressService.kt          # 按需 dataSync 前台下载进度服务与真实速度采样
├── DownloadNotificationDiagnostics.kt  # 通知 channel、权限与诊断记录
└── DownloadStore.kt                    # DownloadManager 任务 ID 与应用内重命名显示名

app/src/main/res/                       # 主题、字符串、启动图标与适配资源
branding/floating_browser_icon_minimal_white.png  # 白底极简图标主资源
```

## 许可证与参考边界

本仓库以 [MIT License](LICENSE) 发布。用户指定的 [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) 使用 GPL-3.0，因此本项目**没有复制、移植或链接**其源代码与资源；只将用户明确提出的产品方向独立实现为传统 Android View 代码。

## 参考资料

[1] [Android Developers：实时动态通知](https://developer.android.google.cn/design/ui/mobile/guides/home-screen/live-updates?hl=zh-CN)

[2] [Android Developers：通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)

[3] [Android Developers：在 Views 中启用动态颜色](https://developer.android.com/develop/ui/views/theming/dynamic-colors)

[4] [Android Developers：支持预测性返回手势](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)

[5] [Android Developers：为 Views 提供预测性返回动画](https://developer.android.com/guide/navigation/custom-back/support-animations-views)

[6] [Android Developers：WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
