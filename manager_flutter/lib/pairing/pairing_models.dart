/// 「无线配对」引导页的界面文案。全部由 Kotlin `R.string` 下发；无宿主时用英文 [fallback]。
/// key 名 = R.string 名去 `adb_pairing_tutorial_content_` 前缀的小驼峰（映射表见 `PairingChannel.kt` 文件头）。
class PairingCopy {
  const PairingCopy({
    required this.title,
    required this.back,
    required this.permissionMissing,
    required this.notification,
    required this.notificationBlocked,
    required this.notificationSettings,
    required this.autoPairingNotificationAccessTooltip,
    required this.network,
    required this.networkLimitationNotForeground,
    required this.networkBlocked,
    required this.retry,
    required this.serviceStartFailed,
    required this.pairingServiceFailed,
    required this.miui,
    required this.miui2,
    required this.steps,
    required this.leftIsClickable,
    required this.developmentSettings,
    required this.enterPairingCode,
    required this.finish,
    required this.ok,
  });

  final String title;
  final String back;
  final String permissionMissing;
  final String notification;
  final String notificationBlocked;
  final String notificationSettings;
  final String autoPairingNotificationAccessTooltip;
  final String network;
  final String networkLimitationNotForeground;
  final String networkBlocked;
  final String retry;
  final String serviceStartFailed;
  final String pairingServiceFailed;
  final String miui;
  final String miui2;
  final String steps;
  final String leftIsClickable;
  final String developmentSettings;
  final String enterPairingCode;
  final String finish;
  final String ok;

  static const PairingCopy fallback = PairingCopy(
    title: 'Pair Shizuku with your device',
    back: 'Back',
    permissionMissing: 'Permission required',
    notification:
        'A notification from Shizuku will help you complete the pairing.',
    notificationBlocked:
        'The pairing process needs you to interact with a notification from '
        'Shizuku. Please allow Shizuku to post notifications.',
    notificationSettings: 'Notification options',
    autoPairingNotificationAccessTooltip:
        'Auto-pairing requires Notification Access to capture the pairing '
        'code from system settings.',
    network:
        'Shizuku needs to access local network. It is controlled by the '
        'network permission.',
    networkLimitationNotForeground:
        'Some systems (such as MIUI) disallow apps to access the network when '
        'they are not visible, even if the app uses foreground service as '
        'standard. Please disable battery optimization features for Shizuku '
        'on such systems.',
    networkBlocked:
        'The pairing process needs local network access to discover the '
        'pairing service. Please allow local network access for Shizuku.',
    retry: 'Retry',
    serviceStartFailed: 'Start Shizuku service failed.',
    pairingServiceFailed:
        'Could not start the pairing service. Please try again.',
    miui:
        'MIUI users may need to switch notification style to "Android" from '
        '"Notification" - "Notification shade" in system settings.',
    miui2:
        'Otherwise, you may not able to enter paring code from the '
        'notification.',
    steps:
        'Enter "Developer options" - "Wireless debugging". Tap "Pair device '
        'with pairing code", you will see a six-digit code.',
    leftIsClickable:
        'Please note that only the left part of the "Wireless debugging" '
        'option is clickable, tapping it will open a new page. Only toggling '
        'the switch on the right is incorrect.',
    developmentSettings: 'Developer options',
    enterPairingCode:
        'Enter the code in the notification to complete pairing.',
    finish: 'Back to Shizuku and start Shizuku.',
    ok: 'OK',
  );

  factory PairingCopy.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) return fallback;
    String pick(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    const f = fallback;
    return PairingCopy(
      title: pick('title', f.title),
      back: pick('back', f.back),
      permissionMissing: pick('permissionMissing', f.permissionMissing),
      notification: pick('notification', f.notification),
      notificationBlocked: pick('notificationBlocked', f.notificationBlocked),
      notificationSettings:
          pick('notificationSettings', f.notificationSettings),
      autoPairingNotificationAccessTooltip: pick(
        'autoPairingNotificationAccessTooltip',
        f.autoPairingNotificationAccessTooltip,
      ),
      network: pick('network', f.network),
      networkLimitationNotForeground: pick(
        'networkLimitationNotForeground',
        f.networkLimitationNotForeground,
      ),
      networkBlocked: pick('networkBlocked', f.networkBlocked),
      retry: pick('retry', f.retry),
      serviceStartFailed: pick('serviceStartFailed', f.serviceStartFailed),
      pairingServiceFailed:
          pick('pairingServiceFailed', f.pairingServiceFailed),
      miui: pick('miui', f.miui),
      miui2: pick('miui2', f.miui2),
      steps: pick('steps', f.steps),
      leftIsClickable: pick('leftIsClickable', f.leftIsClickable),
      developmentSettings: pick('developmentSettings', f.developmentSettings),
      enterPairingCode: pick('enterPairingCode', f.enterPairingCode),
      finish: pick('finish', f.finish),
      ok: pick('ok', f.ok),
    );
  }
}

/// 整页快照：真源永远是 Kotlin `PairingChannel`（它逐行复刻 `AdbPairingTutorialActivity` 的状态机），
/// 页面不持久化任何状态。
class PairingSnapshot {
  const PairingSnapshot({
    required this.supported,
    required this.notificationEnabled,
    required this.notificationListenerEnabled,
    required this.localNetworkPermissionGranted,
    required this.pairingServiceStartFailed,
    required this.autoPairingEnabled,
    required this.showMiuiHint,
    required this.copy,
  });

  /// 宿主 SDK >= Android 11（配对页只在 R+ 有意义；首页也只在 R+ 露出入口）。
  final bool supported;
  final bool notificationEnabled;
  final bool notificationListenerEnabled;
  final bool localNetworkPermissionGranted;
  final bool pairingServiceStartFailed;

  /// 设置里的「自动配对」开关；Compose 只在它开着且通知监听未开时才显示通知监听引导卡。
  final bool autoPairingEnabled;
  final bool showMiuiHint;
  final PairingCopy copy;

  /// 与 Kotlin `PairingTutorialState()` 默认值同形：本地网络权限默认已授，其余 false。
  static const PairingSnapshot empty = PairingSnapshot(
    supported: true,
    notificationEnabled: false,
    notificationListenerEnabled: false,
    localNetworkPermissionGranted: true,
    pairingServiceStartFailed: false,
    autoPairingEnabled: false,
    showMiuiHint: false,
    copy: PairingCopy.fallback,
  );

  /// 空 Map → [empty]；`supported` / `localNetworkPermissionGranted` 非 `false` 一律 true
  /// （缺失或类型错都走正常分支），其余布尔非 `true` 一律 false；`copy` 不是 Map → fallback。
  factory PairingSnapshot.fromJson(Map<String, dynamic> json) {
    if (json.isEmpty) return empty;
    final copyRaw = json['copy'];
    return PairingSnapshot(
      supported: json['supported'] != false,
      notificationEnabled: json['notificationEnabled'] == true,
      notificationListenerEnabled: json['notificationListenerEnabled'] == true,
      localNetworkPermissionGranted:
          json['localNetworkPermissionGranted'] != false,
      pairingServiceStartFailed: json['pairingServiceStartFailed'] == true,
      autoPairingEnabled: json['autoPairingEnabled'] == true,
      showMiuiHint: json['showMiuiHint'] == true,
      copy: PairingCopy.fromJson(
        copyRaw is Map ? Map<String, dynamic>.from(copyRaw) : null,
      ),
    );
  }

  /// 同 Compose `readyForPairing`：通知开、本地网络权限有、服务没启动失败 → 显示三步引导。
  bool get readyForPairing =>
      notificationEnabled &&
      localNetworkPermissionGranted &&
      !pairingServiceStartFailed;

  /// 同 Compose `autoPairingEnabled && !state.notificationListenerEnabled`。
  bool get showNotificationListenerHint =>
      autoPairingEnabled && !notificationListenerEnabled;
}
