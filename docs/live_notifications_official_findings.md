# Android 下载实时通知：官方核验摘录

核验日期：2026-08-25（GMT+8）

## 已核验事实

1. Android 官方“实时动态通知”设计指南明确将“用户发起、有限或可跟踪的体验”列为适用场景，并要求进度条的填充量与实际进度相符。因此，网页文件下载适合作为实时动态通知用例，但不得伪造进度。

2. Android 13（API 33）及以上版本中，`POST_NOTIFICATIONS` 是非豁免通知（包括前台服务通知）在通知抽屉中可见所需的运行时权限。用户拒绝后，前台服务通知仍可能出现在任务管理器中，但不会出现在通知抽屉。

3. 新安装的应用在 Android 13+ 上默认关闭通知，须在用户上下文中请求权限。官方建议在用户主动操作对应功能时请求。

4. 应同时检查运行时权限与 `areNotificationsEnabled()`，因为用户可以在系统设置中关闭应用通知。

5. 当前 v2.13 的已知实现缺口：它使用普通 `NotificationCompat` 前台通知，而非 Android 16 的实时更新（Live Updates）模板；并且用户反馈真机不可见，说明仅编译通过不能证明通知权限、渠道、前台服务或系统呈现链路有效。后续版本必须增加可见的诊断状态和使用系统兼容的进度通知作为基线。

## 来源

- Android Developers：《实时动态通知》：https://developer.android.google.cn/design/ui/mobile/guides/home-screen/live-updates?hl=zh-CN（页面最后更新 2026-03-02）
- Android Developers：《通知运行时权限》：https://developer.android.com/develop/ui/views/notifications/notification-permission?hl=zh-cn（页面最后更新 2026-08-09）

## 设计结论

下一版应在用户启用下载实时通知时，明确显示权限/系统通知状态；在下载入队后立即创建可见的进度通知，使用 Android `DownloadManager` 的真实字节数据不断更新。对于 Android 16+，应补充平台实时更新模板的兼容分支；对于较低版本，保留前台服务进度通知作为兼容后备。工具栏恢复必须作为独立 UI 修改，与通知诊断一并交付但不改变启动入口。

## Android 16 Live Updates 追加核验

6. Android 16 提供 `Notification.ProgressStyle`，用于用户主动发起、具有明确开始和结束的进度型任务。该样式是系统进度型通知，而不是普通进度条的视觉替代。

7. 要被系统提升为 Live Update，通知需要：合格的标准/BigText/Call/Progress/Metric 样式；声明 `android.permission.POST_PROMOTED_NOTIFICATIONS`；请求 promoted ongoing；设置 ongoing 与 contentTitle；不得使用自定义 RemoteViews、群组摘要或 colorized；通知渠道不能是 `IMPORTANCE_MIN`。系统与厂商仍可拒绝提升，应用应检查促销资格和用户设置。

8. Android 16 Live Update 可以更突出地出现在通知栏顶部、锁屏和状态栏芯片；但普通前台服务通知是低版本或不具备提升资格时的兼容后备，仍必须清晰可见。因此下一版应把普通下载频道的优先级从 LOW 提高到 DEFAULT（首条可见、后续静默更新），并在 Android 16+ 使用平台/兼容库可用的 promoted ongoing 分支。

来源：
- Android Developers：《Progress-centric notifications》：https://developer.android.com/about/versions/16/features/progress-centric-notifications（最后更新 2026-03-03）
- Android Developers：《Create live update notifications》：https://developer.android.com/develop/ui/compose/notifications/live-update（最后更新 2026-08-20）

## v2.15 前台服务诊断核验

核验日期：2026-08-25（GMT+8）

9. Android 12+ 在应用处于后台时启动前台服务会受到限制，并可能抛出 `ForegroundServiceStartNotAllowedException`；用户在应用 UI 中直接操作相关功能属于允许的例外情形。

10. Android 14+ 必须在清单中声明前台服务类型和对应权限，并在 `ServiceCompat.startForeground()` 中传入类型；否则可能出现 `MissingForegroundServiceTypeException` 或 `SecurityException`。`dataSync` 的官方适用案例包括数据上传/下载，且没有额外运行时前置条件。

11. 当前用户已确认：在 v2.14 中，用户发起下载且普通通知与实时动态通知相关设置均已打开，但系统仍没有任何下载通知。因此下一版不能继续只改变渠道优先级或请求提升；必须将“服务是否请求、是否创建渠道、是否成功调用 startForeground、是否成功调用 notify、是否捕获异常”持久化为应用内诊断信息，并在下载管理页可见。

来源：
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/develop/background-work/services/fgs/service-types
