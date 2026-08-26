# v55 Android 平台实现核验记录

核验日期：2026-08-25（GMT+8）。

## 预测性返回

Android 官方建议使用 AndroidX `OnBackPressedDispatcher` / `OnBackPressedCallback` 实现自定义返回行为。该回调在 `android:enableOnBackInvokedCallback` 的值为 `false` 时仍会执行；但预测性系统动画要求 Activity 或应用选择启用 `enableOnBackInvokedCallback`。若回调长期处于启用状态并消费根 Activity 的返回，系统无法展示返回桌面的预测性动画，因此应当仅在浏览器可以后退、存在需要关闭的覆盖层或有可关闭 UI 状态时启用回调；根页面没有可处理状态时必须禁用回调，让系统完成 back-to-home 动画。

来源：<https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture>

## 系统状态栏与导航栏

目标 SDK 35 及以上在 Android 15+ 强制 edge-to-edge。交互式底部控件不得被系统导航栏遮挡，应使用 `WindowInsetsCompat.Type.systemBars()` 的底部 inset 增加控件底边距；左右 inset 应同时加入外边距，保证椭圆 Tab 容器的四方向视觉间距一致。系统栏不得隐藏。

来源：<https://developer.android.com/develop/ui/views/layout/edge-to-edge>

## 本轮实现原则

1. Tab 容器维持 `Gravity.BOTTOM`，并使用左右、顶部和底部一致的基础外边距；系统导航栏额外 inset 只增加底边距，避免影响系统任务栏/导航栏的可用性。
2. 预测性返回完全可由 Kotlin/AndroidX 实现，不需要引入其他编程语言。
3. 下载进度通知使用 Android `DownloadManager` 返回的任务标题与真实字节状态；前台服务只在用户触发下载且通知权限可用时启动，并在回到桌面后继续更新。
