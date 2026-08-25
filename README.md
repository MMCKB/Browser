# 浮悬浏览器

一个使用 **Kotlin + Android View + WebView** 构建的原生 Android 浏览器示例。项目不使用 Expo、React Native 或 Jetpack Compose；新标签页显示本地搜索首页，用户提交搜索词后才跳转到 Bing 结果页。

当前仓库对应 **v2.15.0-notification-diagnostics**。它保留已验证的网页下载与下载管理，并让下载通知链路在没有 Logcat 的真机环境中也能够直接检查。

## 功能概览

| 模块 | 当前实现 |
|---|---|
| 本地搜索首页 | 每个新标签默认显示独立本地首页；搜索提交后跳转至 [Bing](https://www.bing.com/) 搜索结果。|
| 多标签浏览 | 支持创建、切换、关闭 WebView 标签；可在设置中选用“始终极简”或“始终完整”两种固定布局。|
| 底部控制 | 地址栏、椭圆形后退/前进/刷新组、新建标签和 Tab 入口固定在底部，降低对网页内容的干扰。|
| 双列工具栏 | 点击 Tab 区 `⋮` 后，从底部 Tab 区上方弹出独立双列菜单；点击空白、返回键或菜单操作会回落关闭。|
| 网页下载 | WebView 下载请求交由 Android `DownloadManager` 处理，并带入网页 User-Agent 和 Cookie。|
| 下载管理 | 在 `⋮ → 下载管理` 中查询任务状态、真实已下载字节/总字节、完成文件，并手动刷新。|
| 下载通知诊断 | 设置可发布独立测试通知；网页下载后先发布探针通知，再启动 `dataSync` 前台服务。下载管理会显示通知发布和服务启动的最后诊断结果。|
| 外观与主题 | 固定低对比度毛玻璃控制区；支持浅色、深色和跟随系统，未包含液态玻璃或 Kyant0 依赖。|
| 安全默认值 | WebView 禁止混合内容、`file://` 访问和内容访问；HTTP(S) 由应用打开，其他协议交给系统。|

> Android 16+ 的“实时动态通知”由系统根据应用请求、通知特征、用户设置和设备实现决定是否提升。无论是否被系统提升，v2.15 的首要验收标准都是普通系统通知栏中可以看到测试通知和下载进度探针。

## 下载通知验证

1. 在应用中进入 `⋮ → 设置 → 下载实时通知`，允许系统通知权限。
2. 点击 `测试下载实时通知`。如果它可见，说明应用通知渠道和系统权限链路可用。
3. 开始一个不会瞬间完成的文件下载。应用会先显示“下载实时通知已启动”，随后按 Android `DownloadManager` 返回的真实字节进度更新。
4. 若下载仍没有通知，进入 `⋮ → 下载管理`，查看顶部的 **实时通知诊断**。该文本会记录权限、探针发布、`startForeground`、任务监控或进度更新的最后状态。

## 构建环境

| 项目 | 版本 / 要求 |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.9.1 |
| Gradle Wrapper | 8.11.1 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 36 |
| minSdk | 24（Android 7.0） |

使用 Android Studio 打开项目根目录，或在安装 Android SDK 与 JDK 17 后执行：

```bash
./gradlew :app:assembleDebug
```

在资源受限环境中，可使用以下命令同时执行构建与静态检查：

```bash
ANDROID_HOME=/path/to/android-sdk \
ANDROID_SDK_ROOT=/path/to/android-sdk \
JAVA_HOME=/path/to/jdk-17 \
./gradlew clean :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
```

调试安装包输出路径为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```text
app/src/main/java/com/manus/floatingbrowser/
├── MainActivity.kt                     # 浏览器、标签、地址栏、工具栏、设置与内嵌下载管理
├── DownloadProgressService.kt          # 按需运行的 dataSync 前台下载进度服务
├── DownloadNotificationDiagnostics.kt  # 测试通知、探针和应用内诊断状态
└── DownloadStore.kt                    # DownloadManager 任务 ID 本地存储

app/src/main/AndroidManifest.xml        # 网络、通知、前台服务与主入口声明
app/src/main/res/                        # 主题、颜色、备份/数据提取规则
docs/live_notifications_official_findings.md  # 通知与前台服务设计核验记录
```

## 说明与边界

网页兼容性、Cookie、登录状态、文件服务器响应以及下载任务状态由设备 WebView、Android `DownloadManager` 和目标网站共同决定。本项目当前仍处于测试阶段；发布到应用商店前应使用正式签名，完成隐私政策、权限说明、下载行为、通知渠道、WebView 安全和多设备兼容性测试。

书签当前支持添加/移除状态，但完整的书签查看和删除列表尚未迁回 v2.15 基线，后续应作为独立增量实现并真机验证。

## 许可证

本项目以 [MIT License](LICENSE) 发布。第三方组件、Android SDK 与网页内容仍分别受其自身许可证和服务条款约束。

## 参考资料

- [Android Developers：WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [Android Developers：前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android Developers：实时动态通知](https://developer.android.google.cn/design/ui/mobile/guides/home-screen/live-updates?hl=zh-CN)
- [Android Developers：通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)
