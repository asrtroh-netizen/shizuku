# OBSERVATIONS R2

格式：日期 | 发现者 | 位置 | 现象 | 建议

- 2026-09-06 | 协调者 | `NotificationListenerAccess.openSettings` | 合并前设置页 catch `Exception`、配对页 catch `ActivityNotFoundException`；合并后取 `Exception`。设置页行为不变；配对页极少见的非 ANFE 异常从"上抛给通道报错"改为"静默并尝试回退" | 已接受，记录
- 2026-09-06 | K1 | `HomeActions.snapshot()` | 事实采集改为急切求值：R+ 上也读一次 `getAdbTcpPort()`；`grantedCount < 0` 时也格式化一次复数字符串后丢弃。无副作用、用户不可见 | 已接受
- 2026-09-06 | K1 | `FlutterHostActivity` | `actions` 原来在 `configureFlutterEngine` 与 `onCreate` 各建一次；接线时改为 `if (!::actions.isInitialized)`，`HomeChannel` 与宿主持同一实例 | 已处理
- 2026-09-06 | K3 | `res/values/values.xml` `main_layout_manager` | 旧 View 首页 RecyclerView 遗留字符串，零引用；`shrinkResources` 会剔除 | 下轮死资源清理
- 2026-09-06 | K3 | `gradle/libs.versions.toml` | 四个 Compose 别名已无引用（tooling / tooling-preview / icons-extended / runtime-livedata） | 无害，可下轮清
- 2026-09-06 | D1 | `home_header.dart` `HomeThemeSlide` | 字面 hex `Color(0xFF3A4A6B)` / `Color(0x33000000)`（P1 就有），违反 v1.3 §3.1 | 下轮换令牌
- 2026-09-06 | D1 | `home_models.dart` | `grantedCount / adbCommand / appsSub / rootSub` 用硬 `as` 转型，坏类型会抛；其它页面模型是宽容解析 | 下轮统一
- 2026-09-06 | D3 | Ultra `one_status_hero.dart` | busy 态按钮 `busyLabel` 为 null 时硬编码中文 `'处理中'`；首页未启用 busy 态 | 若启用需从 copy 下发
- 2026-09-06 | 协调者 | `migration/round2/` | 非本会话产物（今天 02:53–03:05 生成的另一份 R2 草案，仅文档），与 `migration/r2/` 并存易混淆 | 请用户决定删除或归档
