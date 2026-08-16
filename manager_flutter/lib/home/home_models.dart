class HomeCopy {
  const HomeCopy({
    required this.appName,
    required this.heroEyebrow,
    required this.heroTitleInactive,
    required this.heroPillInactive,
    required this.heroPillReady,
    required this.heroSubtitle,
    required this.heroDetail,
    required this.stageInactive,
    required this.stageReady,
    required this.quickTitle,
    required this.wirelessTitle,
    required this.wirelessGuide,
    required this.pairing,
    required this.start,
    required this.restart,
    required this.bootTitle,
    required this.bootConfigure,
    required this.bootRoot,
    required this.bootWireless,
    required this.watchdog,
    required this.appsTitle,
    required this.appsOpen,
    required this.terminalTitle,
    required this.terminalSub,
    required this.terminalBody,
    required this.terminalOff,
    required this.rootTitle,
    required this.rootConfirm,
    required this.rootUnavailable,
    required this.adbTitle,
    required this.adbSub,
    required this.adbViewCommand,
    required this.adbCopy,
    required this.adbSend,
    required this.adbLimited,
    required this.checkUpdate,
    required this.checkingUpdate,
    required this.lang,
    required this.language,
    required this.themeLight,
    required this.themeDark,
    required this.ok,
    required this.cancel,
  });

  final String appName;
  final String heroEyebrow;
  final String heroTitleInactive;
  final String heroPillInactive;
  final String heroPillReady;
  final String heroSubtitle;
  final String heroDetail;
  final String stageInactive;
  final String stageReady;
  final String quickTitle;
  final String wirelessTitle;
  final String wirelessGuide;
  final String pairing;
  final String start;
  final String restart;
  final String bootTitle;
  final String bootConfigure;
  final String bootRoot;
  final String bootWireless;
  final String watchdog;
  final String appsTitle;
  final String appsOpen;
  final String terminalTitle;
  final String terminalSub;
  final String terminalBody;
  final String terminalOff;
  final String rootTitle;
  final String rootConfirm;
  final String rootUnavailable;
  final String adbTitle;
  final String adbSub;
  final String adbViewCommand;
  final String adbCopy;
  final String adbSend;
  final String adbLimited;
  final String checkUpdate;
  final String checkingUpdate;
  final String lang;
  final String language;
  final String themeLight;
  final String themeDark;
  final String ok;
  final String cancel;

  static const HomeCopy fallback = HomeCopy(
    appName: 'Shizuku',
    heroEyebrow: 'Service',
    heroTitleInactive: 'Not activated',
    heroPillInactive: 'Inactive',
    heroPillReady: 'Active',
    heroSubtitle: 'Shizuku is not running',
    heroDetail: 'Use wireless debugging below to start. Keep Wi‑Fi on.',
    stageInactive: 'Inactive',
    stageReady: 'Ready',
    quickTitle: 'Quick actions',
    wirelessTitle: 'Wireless debugging',
    wirelessGuide: 'Step-by-step guide',
    pairing: 'Pairing',
    start: 'Start',
    restart: 'Restart',
    bootTitle: 'Boot start',
    bootConfigure: 'Configure',
    bootRoot: 'Start on boot (Root)',
    bootWireless: 'Auto-start on Wi-Fi (wireless ADB)',
    watchdog: 'ADB/TCP watchdog',
    appsTitle: 'Application management',
    appsOpen: 'Tap to manage authorized apps',
    terminalTitle: 'Terminal',
    terminalSub: 'Shell via Shizuku',
    terminalBody: 'Run commands through Shizuku in terminal apps you like',
    terminalOff: 'Shizuku is not running',
    rootTitle: 'Root start',
    rootConfirm: 'Start or restart Shizuku with root?',
    rootUnavailable: 'This device is not rooted. Use wireless debugging or PC ADB instead.',
    adbTitle: 'PC ADB',
    adbSub: 'PC ADB',
    adbViewCommand: 'View command',
    adbCopy: 'Copy',
    adbSend: 'Send',
    adbLimited: 'You need to take an extra step',
    checkUpdate: 'Check for updates',
    checkingUpdate: 'Checking…',
    lang: 'Lang',
    language: 'Language',
    themeLight: 'Light',
    themeDark: 'Dark',
    ok: 'OK',
    cancel: 'Cancel',
  );

  factory HomeCopy.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) return fallback;
    String pick(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    const f = fallback;
    return HomeCopy(
      appName: pick('appName', f.appName),
      heroEyebrow: pick('heroEyebrow', f.heroEyebrow),
      heroTitleInactive: pick('heroTitleInactive', f.heroTitleInactive),
      heroPillInactive: pick('heroPillInactive', f.heroPillInactive),
      heroPillReady: pick('heroPillReady', f.heroPillReady),
      heroSubtitle: pick('heroSubtitle', f.heroSubtitle),
      heroDetail: pick('heroDetail', f.heroDetail),
      stageInactive: pick('stageInactive', f.stageInactive),
      stageReady: pick('stageReady', f.stageReady),
      quickTitle: pick('quickTitle', f.quickTitle),
      wirelessTitle: pick('wirelessTitle', f.wirelessTitle),
      wirelessGuide: pick('wirelessGuide', f.wirelessGuide),
      pairing: pick('pairing', f.pairing),
      start: pick('start', f.start),
      restart: pick('restart', f.restart),
      bootTitle: pick('bootTitle', f.bootTitle),
      bootConfigure: pick('bootConfigure', f.bootConfigure),
      bootRoot: pick('bootRoot', f.bootRoot),
      bootWireless: pick('bootWireless', f.bootWireless),
      watchdog: pick('watchdog', f.watchdog),
      appsTitle: pick('appsTitle', f.appsTitle),
      appsOpen: pick('appsOpen', f.appsOpen),
      terminalTitle: pick('terminalTitle', f.terminalTitle),
      terminalSub: pick('terminalSub', f.terminalSub),
      terminalBody: pick('terminalBody', f.terminalBody),
      terminalOff: pick('terminalOff', f.terminalOff),
      rootTitle: pick('rootTitle', f.rootTitle),
      rootConfirm: pick('rootConfirm', f.rootConfirm),
      rootUnavailable: pick('rootUnavailable', f.rootUnavailable),
      adbTitle: pick('adbTitle', f.adbTitle),
      adbSub: pick('adbSub', f.adbSub),
      adbViewCommand: pick('adbViewCommand', f.adbViewCommand),
      adbCopy: pick('adbCopy', f.adbCopy),
      adbSend: pick('adbSend', f.adbSend),
      adbLimited: pick('adbLimited', f.adbLimited),
      checkUpdate: pick('checkUpdate', f.checkUpdate),
      checkingUpdate: pick('checkingUpdate', f.checkingUpdate),
      lang: pick('lang', f.lang),
      language: pick('language', f.language),
      themeLight: pick('themeLight', f.themeLight),
      themeDark: pick('themeDark', f.themeDark),
      ok: pick('ok', f.ok),
      cancel: pick('cancel', f.cancel),
    );
  }
}

class HomeSnapshot {
  const HomeSnapshot({
    required this.running,
    required this.permission,
    required this.grantedCount,
    required this.rooted,
    required this.rootRestart,
    required this.showWireless,
    required this.showPair,
    required this.bootRoot,
    required this.bootWireless,
    required this.watchdog,
    required this.dark,
    required this.adbLimited,
    required this.adbCommand,
    required this.appsSub,
    required this.rootSub,
    required this.locales,
    required this.copy,
  });

  final bool running;
  final bool permission;
  final int grantedCount;
  final bool rooted;
  final bool rootRestart;
  final bool showWireless;
  final bool showPair;
  final bool bootRoot;
  final bool bootWireless;
  final bool watchdog;
  final bool dark;
  final bool adbLimited;
  final String adbCommand;
  final String appsSub;
  final String rootSub;
  final List<HomeLocale> locales;
  final HomeCopy copy;

  static HomeSnapshot empty = HomeSnapshot(
    running: false,
    permission: false,
    grantedCount: -1,
    rooted: false,
    rootRestart: false,
    showWireless: true,
    showPair: true,
    bootRoot: false,
    bootWireless: false,
    watchdog: false,
    dark: false,
    adbLimited: false,
    adbCommand: 'adb shell shizuku',
    appsSub: HomeCopy.fallback.heroSubtitle,
    rootSub: 'Root required',
    locales: const [
      HomeLocale(tag: 'SYSTEM', label: 'Follow System', selected: true),
    ],
    copy: HomeCopy.fallback,
  );

  factory HomeSnapshot.fromJson(Map<String, dynamic> json) {
    if (json.isEmpty) return empty;
    final copyRaw = json['copy'];
    return HomeSnapshot(
      running: json['running'] == true,
      permission: json['permission'] == true,
      grantedCount: (json['grantedCount'] as num?)?.toInt() ?? -1,
      rooted: json['rooted'] == true,
      rootRestart: json['rootRestart'] == true,
      showWireless: json['showWireless'] != false,
      showPair: json['showPair'] == true,
      bootRoot: json['bootRoot'] == true,
      bootWireless: json['bootWireless'] == true,
      watchdog: json['watchdog'] == true,
      dark: json['dark'] == true,
      adbLimited: json['adbLimited'] == true,
      adbCommand: json['adbCommand'] as String? ?? empty.adbCommand,
      appsSub: json['appsSub'] as String? ?? empty.appsSub,
      rootSub: json['rootSub'] as String? ?? empty.rootSub,
      locales: _locales(json['locales']),
      copy: HomeCopy.fromJson(
        copyRaw is Map ? Map<String, dynamic>.from(copyRaw) : null,
      ),
    );
  }

  HomeSnapshot copyWith({
    bool? bootRoot,
    bool? bootWireless,
    bool? watchdog,
  }) {
    return HomeSnapshot(
      running: running,
      permission: permission,
      grantedCount: grantedCount,
      rooted: rooted,
      rootRestart: rootRestart,
      showWireless: showWireless,
      showPair: showPair,
      bootRoot: bootRoot ?? this.bootRoot,
      bootWireless: bootWireless ?? this.bootWireless,
      watchdog: watchdog ?? this.watchdog,
      dark: dark,
      adbLimited: adbLimited,
      adbCommand: adbCommand,
      appsSub: appsSub,
      rootSub: rootSub,
      locales: locales,
      copy: copy,
    );
  }

  static List<HomeLocale> _locales(Object? raw) {
    if (raw is! List) return empty.locales;
    return raw
        .whereType<Map>()
        .map((e) => HomeLocale(
              tag: e['tag'] as String? ?? 'SYSTEM',
              label: e['label'] as String? ?? 'SYSTEM',
              selected: e['selected'] == true,
            ))
        .toList();
  }
}

class HomeLocale {
  const HomeLocale({
    required this.tag,
    required this.label,
    required this.selected,
  });

  final String tag;
  final String label;
  final bool selected;
}
