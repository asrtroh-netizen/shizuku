/// 「授权应用」Tab 的界面文案。全部由 Kotlin `R.string` 下发；无宿主时用英文 [fallback]。
class AppsCopy {
  const AppsCopy({
    required this.title,
    required this.empty,
    required this.requiresRoot,
    required this.adbLimitedTitle,
    required this.adbLimitedMessage,
    required this.notRunning,
    required this.back,
    required this.ok,
  });

  final String title;
  final String empty;
  final String requiresRoot;
  final String adbLimitedTitle;
  final String adbLimitedMessage;
  final String notRunning;
  final String back;
  final String ok;

  static const AppsCopy fallback = AppsCopy(
    title: 'Application management',
    empty: 'Apps that has requested or declared Shizuku will show here.',
    requiresRoot: '* requires Shizuku runs with root',
    adbLimitedTitle: 'The permission of adb is limited',
    adbLimitedMessage:
        "It's highly possible that your device manufacturer limits the permission of adb. "
        'There may be a solution for your system in this document.',
    notRunning: 'Shizuku is not running',
    back: 'Back',
    ok: 'OK',
  );

  factory AppsCopy.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) return fallback;
    String pick(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    const f = fallback;
    return AppsCopy(
      title: pick('title', f.title),
      empty: pick('empty', f.empty),
      requiresRoot: pick('requiresRoot', f.requiresRoot),
      adbLimitedTitle: pick('adbLimitedTitle', f.adbLimitedTitle),
      adbLimitedMessage: pick('adbLimitedMessage', f.adbLimitedMessage),
      notRunning: pick('notRunning', f.notRunning),
      back: pick('back', f.back),
      ok: pick('ok', f.ok),
    );
  }
}

/// 一行授权应用。`label` 已在 Kotlin 侧按 Compose 规则拼好（他用户加 ` - <userName> (<userId>)`）。
class AppRow {
  const AppRow({
    required this.packageName,
    required this.uid,
    required this.userId,
    required this.label,
    required this.requiresRoot,
    required this.granted,
  });

  final String packageName;
  final int uid;
  final int userId;
  final String label;
  final bool requiresRoot;
  final bool granted;

  /// 非法 / 缺失字段用默认值：整数 → -1，字符串 → `''`，布尔 → false。
  factory AppRow.fromJson(Map<String, dynamic> json) {
    String str(String key) {
      final v = json[key];
      return v is String ? v : '';
    }

    int integer(String key) {
      final v = json[key];
      return v is num ? v.toInt() : -1;
    }

    return AppRow(
      packageName: str('packageName'),
      uid: integer('uid'),
      userId: integer('userId'),
      label: str('label'),
      requiresRoot: json['requiresRoot'] == true,
      granted: json['granted'] == true,
    );
  }
}

/// 列表项 / 图标缓存共用的键，与 Kotlin `packageGrantKey` 同形。
String appKey(AppRow row) => '${row.packageName}#${row.uid}';

/// 整页快照：真源永远是 Kotlin，页面不持久化任何状态。
class AppsSnapshot {
  const AppsSnapshot({
    required this.running,
    required this.adbLimited,
    required this.apps,
    required this.copy,
  });

  final bool running;
  final bool adbLimited;
  final List<AppRow> apps;
  final AppsCopy copy;

  static const AppsSnapshot empty = AppsSnapshot(
    running: false,
    adbLimited: false,
    apps: <AppRow>[],
    copy: AppsCopy.fallback,
  );

  /// 空 Map → [empty]；`apps` 不是 List → `[]`；`copy` 不是 Map → fallback。
  factory AppsSnapshot.fromJson(Map<String, dynamic> json) {
    if (json.isEmpty) return empty;
    final copyRaw = json['copy'];
    return AppsSnapshot(
      running: json['running'] == true,
      adbLimited: json['adbLimited'] == true,
      apps: _apps(json['apps']),
      copy: AppsCopy.fromJson(
        copyRaw is Map ? Map<String, dynamic>.from(copyRaw) : null,
      ),
    );
  }

  static List<AppRow> _apps(Object? raw) {
    if (raw is! List) return const <AppRow>[];
    return raw
        .whereType<Map>()
        .map((e) => AppRow.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }
}
