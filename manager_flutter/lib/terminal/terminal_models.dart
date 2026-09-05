/// 「终端」Tab（rish 教程页）的界面文案。全部由 Kotlin `R.string` 下发（格式参数已在
/// Kotlin 侧填好、HTML 已去标签）；无宿主时用英文 [fallback]（`res/values/strings.xml`
/// 默认值按 `rish` / `rish_shizuku.dex` 填参后的原文）。
class TerminalCopy {
  const TerminalCopy({
    required this.title,
    required this.back,
    required this.open,
    required this.rishDescription,
    required this.tutorial1,
    required this.tutorial1Description,
    required this.exportFiles,
    required this.tutorial2,
    required this.tutorial2Description,
    required this.tutorial3,
    required this.tutorial3Description,
  });

  final String title;
  final String back;
  final String open;
  final String rishDescription;
  final String tutorial1;
  final String tutorial1Description;
  final String exportFiles;
  final String tutorial2;
  final String tutorial2Description;
  final String tutorial3;
  final String tutorial3Description;

  static const TerminalCopy fallback = TerminalCopy(
    title: 'Use Shizuku in terminal apps',
    back: 'Back',
    open: 'Open',
    rishDescription:
        'With rish, in any terminal app, you can connect to and interact with '
        'the shell runs by Shizuku. About the detailed usage rish, tap to view '
        'the document.',
    tutorial1:
        'First, Export files to any where you want. You will find two files, '
        'rish and rish_shizuku.dex.',
    tutorial1Description:
        'If there are files with the same name in the selected folder, they '
        'will be deleted.\n\nThe export function uses SAF (Storage Access '
        "Framework). It's reported that MIUI breaks the functions of SAF. If "
        "you are using MIUI, you may have to extract the file from Shizuku's "
        'apk or download from GitHub.',
    exportFiles: 'Export files',
    tutorial2: 'Then, use any text editor to open and edit rish.',
    tutorial2Description:
        'For example, if you want to use Shizuku in Termux, you should replace '
        'PKG with com.termux (com.termux is the package name of Termux).',
    tutorial3:
        'Finally, move the files to somewhere where your terminal app can '
        'access, you will be able to use sh rish to run commands through '
        'Shizuku.',
    tutorial3Description:
        'Some tips: grant execute permission to rish and add it to PATH, you '
        'will able to use rish directly.',
  );

  factory TerminalCopy.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) return fallback;
    String pick(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    const f = fallback;
    return TerminalCopy(
      title: pick('title', f.title),
      back: pick('back', f.back),
      open: pick('open', f.open),
      rishDescription: pick('rishDescription', f.rishDescription),
      tutorial1: pick('tutorial1', f.tutorial1),
      tutorial1Description:
          pick('tutorial1Description', f.tutorial1Description),
      exportFiles: pick('exportFiles', f.exportFiles),
      tutorial2: pick('tutorial2', f.tutorial2),
      tutorial2Description:
          pick('tutorial2Description', f.tutorial2Description),
      tutorial3: pick('tutorial3', f.tutorial3),
      tutorial3Description:
          pick('tutorial3Description', f.tutorial3Description),
    );
  }
}

/// 整页快照：真源永远是 Kotlin，页面不持久化任何状态。
class TerminalSnapshot {
  const TerminalSnapshot({
    required this.shName,
    required this.dexName,
    required this.copy,
  });

  final String shName;
  final String dexName;
  final TerminalCopy copy;

  static const TerminalSnapshot empty = TerminalSnapshot(
    shName: 'rish',
    dexName: 'rish_shizuku.dex',
    copy: TerminalCopy.fallback,
  );

  /// 空 Map → [empty]；`shName` / `dexName` 不是非空字符串 → 默认名；`copy` 不是 Map → fallback。
  factory TerminalSnapshot.fromJson(Map<String, dynamic> json) {
    if (json.isEmpty) return empty;
    String str(String key, String or) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : or;
    }

    final copyRaw = json['copy'];
    return TerminalSnapshot(
      shName: str('shName', empty.shName),
      dexName: str('dexName', empty.dexName),
      copy: TerminalCopy.fromJson(
        copyRaw is Map ? Map<String, dynamic>.from(copyRaw) : null,
      ),
    );
  }
}
