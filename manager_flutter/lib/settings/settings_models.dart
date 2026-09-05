/// `setBool` 的线上 key：与 Kotlin `ShizukuSettings.*` / `ThemeHelper.KEY_*` 的实际字符串值逐字一致。
abstract final class SettingsKeys {
  static const String startOnBoot = 'start_on_boot';
  static const String startOnBootWireless = 'start_on_boot_wireless';
  static const String autoPairing = 'auto_pairing_enabled';
  static const String watchdogAdb = 'watchdog_enabled_adb';
  static const String blackNightTheme = 'black_night_theme';
  static const String useSystemColor = 'use_system_color';
}

/// TCP/IP 端口的合法区间，与 Compose TcpIpPort 对话框（`10..65535`）及
/// `R.string.dialog_adb_invalid_port` 原文一致；Kotlin `parseTcpipPort` 是最终裁判，这里只为提前给出行内错误。
abstract final class TcpipPortRange {
  static const int min = 10;
  static const int max = 65535;

  static bool contains(int value) => value >= min && value <= max;
}

/// 「设置」Tab 的界面文案。全部由 Kotlin `R.string` 下发；无宿主时用英文 [fallback]。
/// key 名 = R.string 名去 `settings_` 前缀的小驼峰（`translationContributorsSummary` 是
/// Compose 现算的副标题：`translation_contributors` 为空时退到 `settings_translation_contributors_fallback`）。
class SettingsCopy {
  const SettingsCopy({
    required this.title,
    required this.startup,
    required this.startOnBoot,
    required this.startOnBootSummary,
    required this.startOnBootWireless,
    required this.startOnBootWirelessSummary,
    required this.autoPairing,
    required this.autoPairingSummary,
    required this.watchdogAdb,
    required this.watchdogAdbSummary,
    required this.tcpipPort,
    required this.tcpipPortSummary,
    required this.tcpipPortDisabled,
    required this.dialogAdbInvalidPort,
    required this.language,
    required this.languageSystem,
    required this.translationContributors,
    required this.translationContributorsSummary,
    required this.translation,
    required this.translationSummary,
    required this.userInterface,
    required this.darkTheme,
    required this.followSystem,
    required this.blackNightTheme,
    required this.blackNightThemeSummary,
    required this.useSystemColor,
    required this.useSystemColorSummary,
    required this.permissionMissing,
    required this.wirelessBootPermissionTooltip,
    required this.autoPairingNotificationAccessTooltip,
    required this.manual,
    required this.cancel,
    required this.ok,
  });

  final String title;
  final String startup;
  final String startOnBoot;
  final String startOnBootSummary;
  final String startOnBootWireless;
  final String startOnBootWirelessSummary;
  final String autoPairing;
  final String autoPairingSummary;
  final String watchdogAdb;
  final String watchdogAdbSummary;
  final String tcpipPort;
  final String tcpipPortSummary;
  final String tcpipPortDisabled;
  final String dialogAdbInvalidPort;
  final String language;
  final String languageSystem;
  final String translationContributors;
  final String translationContributorsSummary;
  final String translation;
  final String translationSummary;
  final String userInterface;
  final String darkTheme;
  final String followSystem;
  final String blackNightTheme;
  final String blackNightThemeSummary;
  final String useSystemColor;
  final String useSystemColorSummary;
  final String permissionMissing;
  final String wirelessBootPermissionTooltip;
  final String autoPairingNotificationAccessTooltip;
  final String manual;
  final String cancel;
  final String ok;

  static const SettingsCopy fallback = SettingsCopy(
    title: 'Settings',
    startup: 'Startup',
    startOnBoot: 'Start on boot (Root)',
    startOnBootSummary:
        'On rooted devices, start Shizuku with Root at boot '
        '(mutually exclusive with wireless boot; off by default)',
    startOnBootWireless: 'Auto-start on Wi-Fi (wireless ADB)',
    startOnBootWirelessSummary:
        'Automatically start Shizuku when Wi-Fi is connected. '
        'Requires WRITE_SECURE_SETTINGS permission.',
    autoPairing: 'Auto-pairing',
    autoPairingSummary: 'Automatically capture pairing code from notification',
    watchdogAdb: 'ADB/TCP watchdog',
    watchdogAdbSummary:
        'Monitor Shizuku after ADB/TCP start and automatically restart it '
        'up to 5 times if the service is killed',
    tcpipPort: 'TCP/IP port',
    tcpipPortSummary:
        'After starting via wireless debugging, change the TCP/IP listening '
        'port of adbd.\nClick Edit. If left blank, it will be considered disabled.',
    tcpipPortDisabled: 'Disabled',
    dialogAdbInvalidPort: 'Port is an integer ranging from 10 to 65535.',
    language: 'Language',
    languageSystem: 'Follow System',
    translationContributors: 'Translation contributors',
    translationContributorsSummary: 'Community maintained',
    translation: 'Participate in translation',
    translationSummary: 'Help us translate Shizuku into your language',
    userInterface: 'Appearance',
    darkTheme: 'Dark theme',
    followSystem: 'Follow system',
    blackNightTheme: 'Black night theme',
    blackNightThemeSummary: 'Use the pure black theme if night mode is enabled',
    useSystemColor: 'Use system theme color',
    useSystemColorSummary: 'Follow Material You dynamic color when available.',
    permissionMissing: 'Permission required',
    wirelessBootPermissionTooltip:
        'To automatically start Shizuku on boot with wireless ADB, '
        'the WRITE_SECURE_SETTINGS permission must be granted',
    autoPairingNotificationAccessTooltip:
        'Auto-pairing requires Notification Access to capture the pairing '
        'code from system settings.',
    manual: 'Manual',
    cancel: 'Cancel',
    ok: 'OK',
  );

  factory SettingsCopy.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) return fallback;
    String pick(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    const f = fallback;
    return SettingsCopy(
      title: pick('title', f.title),
      startup: pick('startup', f.startup),
      startOnBoot: pick('startOnBoot', f.startOnBoot),
      startOnBootSummary: pick('startOnBootSummary', f.startOnBootSummary),
      startOnBootWireless: pick('startOnBootWireless', f.startOnBootWireless),
      startOnBootWirelessSummary:
          pick('startOnBootWirelessSummary', f.startOnBootWirelessSummary),
      autoPairing: pick('autoPairing', f.autoPairing),
      autoPairingSummary: pick('autoPairingSummary', f.autoPairingSummary),
      watchdogAdb: pick('watchdogAdb', f.watchdogAdb),
      watchdogAdbSummary: pick('watchdogAdbSummary', f.watchdogAdbSummary),
      tcpipPort: pick('tcpipPort', f.tcpipPort),
      tcpipPortSummary: pick('tcpipPortSummary', f.tcpipPortSummary),
      tcpipPortDisabled: pick('tcpipPortDisabled', f.tcpipPortDisabled),
      dialogAdbInvalidPort:
          pick('dialogAdbInvalidPort', f.dialogAdbInvalidPort),
      language: pick('language', f.language),
      languageSystem: pick('languageSystem', f.languageSystem),
      translationContributors:
          pick('translationContributors', f.translationContributors),
      translationContributorsSummary: pick(
        'translationContributorsSummary',
        f.translationContributorsSummary,
      ),
      translation: pick('translation', f.translation),
      translationSummary: pick('translationSummary', f.translationSummary),
      userInterface: pick('userInterface', f.userInterface),
      darkTheme: pick('darkTheme', f.darkTheme),
      followSystem: pick('followSystem', f.followSystem),
      blackNightTheme: pick('blackNightTheme', f.blackNightTheme),
      blackNightThemeSummary:
          pick('blackNightThemeSummary', f.blackNightThemeSummary),
      useSystemColor: pick('useSystemColor', f.useSystemColor),
      useSystemColorSummary:
          pick('useSystemColorSummary', f.useSystemColorSummary),
      permissionMissing: pick('permissionMissing', f.permissionMissing),
      wirelessBootPermissionTooltip: pick(
        'wirelessBootPermissionTooltip',
        f.wirelessBootPermissionTooltip,
      ),
      autoPairingNotificationAccessTooltip: pick(
        'autoPairingNotificationAccessTooltip',
        f.autoPairingNotificationAccessTooltip,
      ),
      manual: pick('manual', f.manual),
      cancel: pick('cancel', f.cancel),
      ok: pick('ok', f.ok),
    );
  }
}

/// 夜间模式候选（`R.array.night_mode_value` ↔ `R.array.night_mode`）。
class NightModeOption {
  const NightModeOption({required this.value, required this.label});

  final int value;
  final String label;

  /// 非法 / 缺失字段用默认值：`value` → -1（跟随系统），`label` → `''`。
  factory NightModeOption.fromJson(Map<String, dynamic> json) {
    final v = json['value'];
    final l = json['label'];
    return NightModeOption(
      value: v is num ? v.toInt() : -1,
      label: l is String ? l : '',
    );
  }
}

/// 语言候选；`label` 已在 Kotlin 侧按 Compose `localeLabel` 表拼好。
class SettingsLocale {
  const SettingsLocale({
    required this.tag,
    required this.label,
    required this.selected,
  });

  final String tag;
  final String label;
  final bool selected;

  factory SettingsLocale.fromJson(Map<String, dynamic> json) {
    final t = json['tag'];
    final l = json['label'];
    return SettingsLocale(
      tag: t is String ? t : 'SYSTEM',
      label: l is String ? l : 'SYSTEM',
      selected: json['selected'] == true,
    );
  }
}

/// 整页快照：真源永远是 Kotlin，页面不持久化任何状态。
class SettingsSnapshot {
  const SettingsSnapshot({
    required this.supportsStartOnBoot,
    required this.bootRoot,
    required this.bootWireless,
    required this.autoPairing,
    required this.watchdog,
    required this.tcpipPort,
    required this.nightMode,
    required this.nightModeOptions,
    required this.blackNightTheme,
    required this.useSystemColor,
    required this.locales,
    required this.translationUrl,
    required this.copy,
  });

  /// Compose `buildSettingsModel`：API < 30 且非 TV 非 root 时隐藏四个启动开关，只留 TCP 端口。
  final bool supportsStartOnBoot;
  final bool bootRoot;
  final bool bootWireless;
  final bool autoPairing;
  final bool watchdog;

  /// 已设的端口字串；`''` 表示未设（Kotlin 保证不为 null）。
  final String tcpipPort;
  final int nightMode;
  final List<NightModeOption> nightModeOptions;
  final bool blackNightTheme;
  final bool useSystemColor;
  final List<SettingsLocale> locales;
  final String translationUrl;
  final SettingsCopy copy;

  /// 无宿主时的默认候选：与 `res/values/arrays.xml` 的 `night_mode` / `night_mode_value` 同序同值。
  static const List<NightModeOption> defaultNightModeOptions = <NightModeOption>[
    NightModeOption(value: 1, label: 'Off'),
    NightModeOption(value: 2, label: 'On'),
    NightModeOption(value: -1, label: 'Follow system'),
  ];

  static const List<SettingsLocale> defaultLocales = <SettingsLocale>[
    SettingsLocale(tag: 'SYSTEM', label: 'Follow System', selected: true),
  ];

  static const SettingsSnapshot empty = SettingsSnapshot(
    supportsStartOnBoot: true,
    bootRoot: false,
    bootWireless: false,
    autoPairing: false,
    watchdog: false,
    tcpipPort: '',
    nightMode: -1,
    nightModeOptions: defaultNightModeOptions,
    blackNightTheme: false,
    useSystemColor: false,
    locales: defaultLocales,
    translationUrl: '',
    copy: SettingsCopy.fallback,
  );

  /// 空 Map → [empty]；布尔非 `true` 一律 false（`supportsStartOnBoot` 例外：非 `false` 一律 true）；
  /// `tcpipPort` 数字 → 转成字串；列表不是 List / 为空 → 默认候选；`copy` 不是 Map → fallback。
  factory SettingsSnapshot.fromJson(Map<String, dynamic> json) {
    if (json.isEmpty) return empty;
    final copyRaw = json['copy'];
    final nightMode = json['nightMode'];
    final translationUrl = json['translationUrl'];
    return SettingsSnapshot(
      supportsStartOnBoot: json['supportsStartOnBoot'] != false,
      bootRoot: json['bootRoot'] == true,
      bootWireless: json['bootWireless'] == true,
      autoPairing: json['autoPairing'] == true,
      watchdog: json['watchdog'] == true,
      tcpipPort: _portString(json['tcpipPort']),
      nightMode: nightMode is num ? nightMode.toInt() : -1,
      nightModeOptions: _nightModeOptions(json['nightModeOptions']),
      blackNightTheme: json['blackNightTheme'] == true,
      useSystemColor: json['useSystemColor'] == true,
      locales: _locales(json['locales']),
      translationUrl: translationUrl is String ? translationUrl : '',
      copy: SettingsCopy.fromJson(
        copyRaw is Map ? Map<String, dynamic>.from(copyRaw) : null,
      ),
    );
  }

  /// 当前夜间模式的展示文案（同 Compose `currentNightModeLabel`：找不到退 `follow_system`）。
  String nightModeLabel() {
    for (final option in nightModeOptions) {
      if (option.value == nightMode) return option.label;
    }
    return copy.followSystem;
  }

  /// 当前语言的展示文案（同 Compose `currentLanguageLabel`：无选中项退 `settings_language_system`）。
  String languageLabel() {
    for (final locale in locales) {
      if (locale.selected) return locale.label;
    }
    return copy.languageSystem;
  }

  static String _portString(Object? raw) {
    if (raw is String) return raw;
    if (raw is num) return raw.toInt().toString();
    return '';
  }

  static List<NightModeOption> _nightModeOptions(Object? raw) {
    if (raw is! List) return defaultNightModeOptions;
    final parsed = raw
        .whereType<Map>()
        .map((e) => NightModeOption.fromJson(Map<String, dynamic>.from(e)))
        .toList();
    return parsed.isEmpty ? defaultNightModeOptions : parsed;
  }

  static List<SettingsLocale> _locales(Object? raw) {
    if (raw is! List) return defaultLocales;
    final parsed = raw
        .whereType<Map>()
        .map((e) => SettingsLocale.fromJson(Map<String, dynamic>.from(e)))
        .toList();
    return parsed.isEmpty ? defaultLocales : parsed;
  }
}
