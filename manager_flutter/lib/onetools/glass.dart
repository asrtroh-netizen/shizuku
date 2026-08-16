import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 全局「液态玻璃」皮肤 —— 与 eSIM 端（NekokoLPA2 的 MarkBuy 玻璃语言）**同一套质感**。
///
/// OneTools 与 eSIM 是同一家的两个新产品，所以质感数值必须一致：圆角 18、
/// 面板模糊 18 / 栏模糊 24、发丝描边 0.8 宽（浅色 onSurface 混白 / 深色白 .12）、
/// 通透面 .55 浅 / 深色更薄、卡片面 .72 浅 / .62 深。
/// 浅色投影走双层（接触影 + 环境影），避免半透明卡贴死浅底渐变；深色仍是单层柔黑影。
///
/// **颜色各自保留**：这些数值只规定"透多少、圆多少、边多细"，底色一律从当前
/// [ColorScheme] 派生。因此 Tools 保住 One 血统色、eSIM 保住它自己的色，
/// Material You、动态取色、纯黑主题、用户自选主题色全部照旧生效。
///
/// 性能取舍沿用参照项目的结论：卡片数量多，**不给每张卡片套 BackdropFilter**，
/// 靠半透明与描边营造玻璃观感；真模糊留给少数高价值面。
abstract final class Glass {
  // ── 质感数值：与 eSIM 端逐项对齐，改这里等于改两个产品的"像不像一家人" ──

  /// 卡片/面板圆角。
  static const double radiusCard = 18;

  /// 小面（菜单、输入框、磁贴）圆角。
  static const double radiusTile = 14;

  /// 芯片圆角。参照项目用 10（不是 Material 默认的胶囊形）。
  static const double radiusChip = 10;

  /// 门面卡/底部弹层这类"大面"的圆角。
  ///
  /// 此前首页门面卡（[OneStatusHero]）自己写死 28、底部弹层写 `radiusCard + 4`(22)、
  /// 普通卡走 18，三种大小并存，摆在一起就是"圆角曲率不统一"。统一收敛到 22：
  /// 比普通卡略大一档以突出门面地位，但落在同一条圆角标度上，不再是游离的 28。
  static const double radiusHero = radiusCard + 4;

  /// 悬浮底栏内容区高度（不含左右/底部留白与系统手势条）。
  static const double dockHeight = 64;

  /// 悬浮底栏圆角。等于 [dockHeight] 的一半，两端成胶囊。
  /// 只用于底栏 chrome；芯片仍走 [radiusChip]。
  static const double radiusDock = dockHeight / 2;

  /// 岛与屏幕底（含系统手势条之上）的空隙。
  static const double dockBottomGap = 8;

  // ── 间距标度（8pt 栅格）：全局统一，改这里等于调所有页面的疏密 ──
  //
  // 此前项目里至少三套间距并行（Glass 的 12/18、AppPaddings 的 16、门面卡自带的
  // 24 与各页手写的 fromLTRB），导致"容器框之间过于拥挤"且一二级页宽度对不齐。
  // 统一到这一把标度后，页面左右留白、卡片间距、卡内留白都从同一处取值。
  static const double space2 = 2;
  static const double space4 = 4;
  static const double space6 = 6;
  static const double space8 = 8;
  static const double space12 = 12;
  static const double space16 = 16;
  static const double space20 = 20;
  static const double space24 = 24;

  /// 页面左右外边距：一级页与二级页统一走它，保证内容宽度严格对齐。
  ///
  /// 从原来的 12 提到 16——首页卡此前在列表 12 之外还各自再补 12（实际 24），
  /// 二级页只有 12，两边宽度对不上。现在两边都只留这一层 16，既对齐又更透气。
  static const double pageMargin = 16;

  /// 竖直堆叠的容器框之间的呼吸间距。卡片自身不再各写外边距，一律靠它拉开。
  static const double cardGap = 14;

  /// 卡内统一内边距。门面卡这类大面可在此基础上加一档（用 [space20]）。
  static const double padCard = 16;

  /// 面板模糊强度。
  static const double blurPanel = 18;

  /// 顶栏/底栏这类 chrome 的模糊强度。
  static const double blurBar = 24;

  static const double strokeWidth = 0.8;

  /// 顶栏与模态面的透明度下限。
  ///
  /// 主题层没法给 AppBar 套 BackdropFilter，而 pinned 的 SliverAppBar 下面
  /// 会有内容滚过；栏面若跟着通透面走，标题就会被内容穿透。这是为可读性
  /// 明示偏离参照项目的一处：栏和模态面更实，其余面按参照数值。
  static const double barAlpha = 0.86;
  static const double modalAlpha = 0.92;

  static bool isDark(ThemeData theme) => theme.brightness == Brightness.dark;

  /// 通透玻璃填充：芯片、输入框、导航栏这类要透出背后色彩的小面。
  static Color fill(ColorScheme scheme, bool dark) => dark
      ? scheme.surfaceContainerHighest.withValues(alpha: 0.22)
      : scheme.surface.withValues(alpha: 0.55);

  /// 卡片/面板填充：可读性优先，对齐参照项目的 .72 / .62。
  ///
  /// 底色沿用应用原本的卡片色 `surfaceContainerLow`（它带着主题色的色调），
  /// 只是加了透明度。换成 `surfaceContainerLowest` 会在浅色模式变成纯白，
  /// 那等于把带色调的卡片改成白卡——就不叫"配色不变"了。
  static Color fillStrong(ColorScheme scheme, bool dark) =>
      scheme.surfaceContainerLow.withValues(alpha: dark ? 0.62 : 0.72);

  /// 顶栏/底栏填充：比卡片更实，保证标题不被滚动内容穿透。
  ///
  /// 这是给**主题层**的栏用的——那里没法套 [BackdropFilter]，只能靠不透明度硬顶。
  /// 有真模糊的栏请用 [blurredBarFill]。
  static Color barFill(ColorScheme scheme, bool dark) => dark
      ? scheme.surfaceContainerLow.withValues(alpha: barAlpha)
      : scheme.surface.withValues(alpha: barAlpha);

  /// 有真模糊的栏（[GlassBar]）用的填充：可以通透得多，但仍留出可读性余量。
  ///
  /// 参照项目的同类面是白 .55 / .06；我在暗色留厚一点，因为这里的 pinned 栏
  /// 下面会有长列表快速滚过，而本机无设备可实测。
  static Color blurredBarFill(ColorScheme scheme, bool dark) => dark
      ? scheme.surfaceContainerHighest.withValues(alpha: 0.34)
      : scheme.surface.withValues(alpha: 0.60);

  /// 模态面（底部弹层/抽屉/菜单）：内容盖在花哨界面上，最需要实。
  static Color modalFill(ColorScheme scheme, bool dark) =>
      scheme.surfaceContainerLow.withValues(alpha: modalAlpha);

  /// 对话框透明度。背后有 [GlassDialogBackdrop] 的整屏模糊撑着可读性，
  /// 所以可以比"没有模糊时"更透一点，让人真的看见玻璃。
  static const double dialogAlpha = 0.88;

  static Color dialogFill(ColorScheme scheme, bool dark) =>
      scheme.surfaceContainerLow.withValues(alpha: dialogAlpha);

  /// 发丝描边：暗色用白高光折光；浅色把一点 [ColorScheme.onSurface] 混进白边，
  /// 否则纯白 0.40 描边会在浅底上消失，卡片糊进背景。
  static Color stroke(ColorScheme scheme, bool dark) => dark
      ? Colors.white.withValues(alpha: 0.12)
      : Color.alphaBlend(
          Colors.white.withValues(alpha: 0.55),
          scheme.onSurface.withValues(alpha: 0.10),
        );

  /// 柔投影：让玻璃面浮在渐变底之上，而不是贴在上面。
  ///
  /// 深色保持单层柔黑影（卡片 blur 18 / 偏移 (0,8)，强投影 blur 22 / (0,10)）。
  /// 浅色改为双层：贴边接触影 + 稍远环境影——单层 6–8% 在白底渐变上几乎看不见。
  static List<BoxShadow> shadow(bool dark, {bool strong = false}) {
    if (dark) {
      return <BoxShadow>[
        BoxShadow(
          color: Colors.black.withValues(alpha: strong ? 0.32 : 0.28),
          blurRadius: strong ? 22 : 18,
          offset: Offset(0, strong ? 10 : 8),
        ),
      ];
    }
    return <BoxShadow>[
      BoxShadow(
        color: Colors.black.withValues(alpha: strong ? 0.07 : 0.05),
        blurRadius: strong ? 6 : 4,
        offset: const Offset(0, 2),
      ),
      BoxShadow(
        color: Colors.black.withValues(alpha: strong ? 0.16 : 0.11),
        blurRadius: strong ? 24 : 18,
        offset: Offset(0, strong ? 10 : 8),
      ),
    ];
  }

  /// 应用底色渐变。
  ///
  /// 玻璃必须有东西可透，否则半透明只会显得发灰；这层渐变就是"可透的东西"。
  /// 取色方式：把主题主色以极低透明度混进 surface —— 既有层次，又完全跟随
  /// 用户当前配色。纯黑主题（AMOLED）例外：一点都不染，保住真黑。
  static LinearGradient backdrop(ColorScheme scheme, bool dark) {
    final pureBlack = dark && scheme.surface == const Color(0xFF000000);
    if (pureBlack) {
      return LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: <Color>[scheme.surface, scheme.surface],
      );
    }

    // One 家族的黑白色板没有色相可染，拿近黑的 primary 去混只会把整面背景
    // 抹灰。中性配色改走亮度差：顶亮底沉，像光从上方打下来——层次仍在，
    // 玻璃依旧有东西可透，但不会脏。
    if (_isNeutralAccent(scheme)) {
      return LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: dark
            ? <Color>[scheme.surfaceContainerHigh, scheme.surface]
            : <Color>[scheme.surfaceContainerLowest, scheme.surfaceContainer],
      );
    }

    final top = Color.alphaBlend(
      scheme.primary.withValues(alpha: dark ? 0.10 : 0.07),
      scheme.surface,
    );
    final bottom = Color.alphaBlend(
      scheme.tertiary.withValues(alpha: dark ? 0.05 : 0.03),
      scheme.surface,
    );
    return LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: <Color>[top, bottom],
    );
  }

  /// 强调色是否几乎没有色相（One 黑白、灰阶主题这类）。
  static bool _isNeutralAccent(ColorScheme scheme) =>
      HSLColor.fromColor(scheme.primary).saturation < 0.15;

  /// 玻璃面装饰（配合 [GlassPanel] 或直接给 Container 用）。
  static BoxDecoration decoration(
    ColorScheme scheme,
    bool dark, {
    double radius = radiusCard,
    bool strong = true,
  }) => BoxDecoration(
    color: strong ? fillStrong(scheme, dark) : fill(scheme, dark),
    borderRadius: BorderRadius.circular(radius),
    border: Border.all(color: stroke(scheme, dark), width: strokeWidth),
    boxShadow: shadow(dark),
  );

  /// 给已有主题套上玻璃皮：只改「面」的通透度、描边与圆角，不动 [ColorScheme]。
  ///
  /// 挂在 `buildObtainiumTheme` 的出口，因此全应用（含上游 Obtainium 自己的
  /// 几十个页面）一次性生效，不必逐页改。
  static ThemeData apply(ThemeData base) {
    final scheme = base.colorScheme;
    final dark = base.brightness == Brightness.dark;
    final glassStroke = stroke(scheme, dark);
    final panel = fillStrong(scheme, dark);
    final sheer = fill(scheme, dark);
    final modal = modalFill(scheme, dark);
    final bar = barFill(scheme, dark);
    final hairline = BorderSide(color: glassStroke, width: strokeWidth);

    RoundedSuperellipseBorder shape(double r) => RoundedSuperellipseBorder(
      borderRadius: BorderRadius.circular(r),
      side: hairline,
    );

    return base.copyWith(
      // 透出底部渐变——玻璃的前提。只放开 Scaffold 背景：canvasColor 是一堆
      // 隐式 Material 面的默认色，一并透明会让它们直接漏底、文字浮在渐变上。
      scaffoldBackgroundColor: Colors.transparent,
      cardTheme: base.cardTheme.copyWith(
        color: panel,
        // 浅色给 1 档投影，避免主题 Card 贴死浅底；暗色仍靠发丝边、elevation 0，
        // 免得大面积黑影糊在深色渐变上。
        elevation: dark ? 0 : 1,
        shadowColor: Colors.black.withValues(alpha: 0.14),
        surfaceTintColor: Colors.transparent,
        // 全局「框」以首页那张基准卡（PermissionSummaryCard，走 GlassPanel）为准：
        // 它用的是普通圆角 circular(18)，而 shape() 是超椭圆 squircle。此前普通 Card
        // 走 squircle、基准卡走 circular，两种框摆一起圆角不一样。这里让所有 Card
        // 也走 circular(18)+同一条发丝描边，与基准卡对齐；填充色 panel 本就同源。
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusCard),
          side: hairline,
        ),
      ),
      dialogTheme: base.dialogTheme.copyWith(
        backgroundColor: dialogFill(scheme, dark),
        surfaceTintColor: Colors.transparent,
        shape: shape(radiusCard + 4),
      ),
      bottomSheetTheme: base.bottomSheetTheme.copyWith(
        backgroundColor: modal,
        surfaceTintColor: Colors.transparent,
        modalBackgroundColor: modal,
      ),
      // 顶栏用较实的玻璃填充：真磨砂必须逐页包 BackdropFilter，主题层给不了；
      // 若跟着通透面走，pinned 顶栏下滚过的内容会把标题穿透。
      appBarTheme: base.appBarTheme.copyWith(
        backgroundColor: bar,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      navigationBarTheme: base.navigationBarTheme.copyWith(
        backgroundColor: bar,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
      ),
      bottomAppBarTheme: base.bottomAppBarTheme.copyWith(
        color: bar,
        elevation: 0,
      ),
      drawerTheme: base.drawerTheme.copyWith(
        backgroundColor: modal,
        surfaceTintColor: Colors.transparent,
      ),
      popupMenuTheme: base.popupMenuTheme.copyWith(
        color: modal,
        surfaceTintColor: Colors.transparent,
        shape: shape(radiusTile),
      ),
      menuTheme: MenuThemeData(
        style: MenuStyle(
          backgroundColor: WidgetStatePropertyAll(modal),
          surfaceTintColor: const WidgetStatePropertyAll(Colors.transparent),
          shape: WidgetStatePropertyAll(shape(radiusTile)),
        ),
      ),
      expansionTileTheme: base.expansionTileTheme.copyWith(
        backgroundColor: Colors.transparent,
        collapsedBackgroundColor: Colors.transparent,
      ),
      searchBarTheme: base.searchBarTheme.copyWith(
        backgroundColor: WidgetStatePropertyAll(panel),
        surfaceTintColor: const WidgetStatePropertyAll(Colors.transparent),
        side: WidgetStatePropertyAll(hairline),
      ),
      chipTheme: base.chipTheme.copyWith(
        backgroundColor: sheer,
        side: hairline,
        shape: shape(radiusChip),
      ),
      inputDecorationTheme: base.inputDecorationTheme.copyWith(
        fillColor: sheer,
      ),
      // 分割线不能用玻璃描边那个白：它是给面板边缘做折光的，
      // 画在浅色内容里几乎看不见。这里仍走主题的 outlineVariant。
      dividerTheme: base.dividerTheme.copyWith(
        color: scheme.outlineVariant.withValues(alpha: dark ? 0.45 : 0.65),
        thickness: 0.5,
      ),
    );
  }
}

/// 应用级渐变底：挂在 `MaterialApp.builder`，让全应用的半透明面都有东西可透。
class GlassBackdrop extends StatelessWidget {
  const GlassBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: Glass.backdrop(theme.colorScheme, dark),
      ),
      child: child,
    );
  }
}

/// 顶栏/底栏的真磨砂层。
///
/// 只能作为 `AppBar.flexibleSpace` 使用，且那条栏的 `backgroundColor` 必须是
/// 透明的——[BackdropFilter] 模糊的是它背后已经画好的内容，如果栏自己还铺着
/// 一层不透明底色，模糊出来的就只是那块纯色，白忙一场。
class GlassBar extends StatelessWidget {
  const GlassBar({super.key, this.blur = Glass.blurBar});

  final double blur;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
        child: DecoratedBox(
          // 有真模糊撑着可读性，填充可以通透得多；但暗色下 pinned 栏底下会有
          // 长列表滚过，所以用专门的 blurredBarFill 留一点余量。
          decoration: BoxDecoration(
            color: Glass.blurredBarFill(theme.colorScheme, dark),
            border: Border(
              bottom: BorderSide(
                color: Glass.stroke(theme.colorScheme, dark),
                width: 0.5,
              ),
            ),
          ),
          child: const SizedBox.expand(),
        ),
      ),
    );
  }
}

/// 无标题栏的页面骨架：只铺 body（与悬浮按钮），顶部不再有磨砂标题栏。
///
/// 标题栏这一排按产品要求整个撤掉，所以这里连 `AppBar` 都不建。子页面的返回
/// 改由系统手势承担。body 仍要用 [glassBodyPadding] 让开状态栏，否则首屏会顶
/// 进刘海/状态栏里。
class GlassScaffold extends StatelessWidget {
  const GlassScaffold({
    super.key,
    required this.body,
    this.floatingActionButton,
  });

  final Widget body;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context) {
    final dark = Glass.isDark(Theme.of(context));
    // 状态栏/导航栏图标明暗随主题**反转**：暗色主题配浅色图标、浅色主题配深色图标，
    // 否则图标与背景同色会糊成一片看不清（全局病根，统一在这里治，所有 OneTools 页生效）。
    final overlay = SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
      statusBarBrightness: dark ? Brightness.dark : Brightness.light,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarIconBrightness: dark ? Brightness.light : Brightness.dark,
      systemNavigationBarContrastEnforced: false,
    );
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlay,
      child: Scaffold(
        body: body,
        floatingActionButton: floatingActionButton,
      ),
    );
  }
}

/// [GlassScaffold] 里滚动视图该用的内边距。
///
/// 标题栏撤掉后，顶部只需让开状态栏（刘海/挖孔）本身，不再为工具栏留高。
/// 高度直接算而不靠注入：页面 `build`/`State.context` 位于内层 Scaffold **之上**，
/// 读到的 `padding.top` 就是状态栏高。
EdgeInsets glassBodyPadding(
  BuildContext context, {
  double horizontal = Glass.pageMargin,
  double top = 12,
  double bottom = 24,
}) {
  final media = MediaQuery.of(context);
  return EdgeInsets.fromLTRB(
    horizontal,
    media.padding.top + top,
    horizontal,
    bottom + media.viewPadding.bottom,
  );
}

/// 主壳列表底部留白：让最后一张卡停在悬浮 Dock 之上，而不是钻进岛里。
///
/// [AppShell] 开了 `extendBody`，内容会从岛两侧透出来；列表必须自己垫这一段。
/// 高度 = 岛内容 + 底隙 + 手势条 + 一档呼吸，不含左右 [Glass.pageMargin]。
double glassDockScrollPadding(BuildContext context) {
  return Glass.dockHeight +
      Glass.dockBottomGap +
      MediaQuery.viewPaddingOf(context).bottom +
      Glass.space16;
}

/// [RefreshIndicator] 在 [GlassScaffold] 里该用的 `edgeOffset`。
///
/// 视口从屏幕顶端起算，下拉转圈默认落在状态栏底下看不全；往下推一个状态栏高
/// 才完整露出来。标题栏撤掉后不再叠加工具栏高。
double glassRefreshOffset(BuildContext context) =>
    MediaQuery.paddingOf(context).top;

/// 真磨砂的 pinned [SliverAppBar]：滚动内容从下面过时会被真实模糊掉。
class GlassSliverAppBar extends StatelessWidget {
  const GlassSliverAppBar({
    super.key,
    required this.title,
    this.actions,
    this.leading,
    this.pinned = true,
    this.automaticallyImplyLeading = false,
  });

  final Widget title;
  final List<Widget>? actions;
  final Widget? leading;
  final bool pinned;
  final bool automaticallyImplyLeading;

  @override
  Widget build(BuildContext context) {
    return SliverAppBar(
      pinned: pinned,
      automaticallyImplyLeading: automaticallyImplyLeading,
      title: title,
      actions: actions,
      leading: leading,
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      flexibleSpace: const GlassBar(),
    );
  }
}

/// 真磨砂底部弹层的外壳。配合 [showGlassSheet] 使用（宿主背景必须透明）。
class GlassSheet extends StatelessWidget {
  const GlassSheet({
    super.key,
    required this.child,
    this.blur = Glass.blurBar,
    this.showDragHandle = false,
  });

  final Widget child;
  final double blur;

  /// 拖拽把手由本组件自己画在玻璃面**里面**。
  ///
  /// 用框架的 `showDragHandle` 不行：宿主背景已被清成透明，把手会落在玻璃面
  /// 之外、直接浮在页面内容上，看着像穿帮。
  final bool showDragHandle;

  static const BorderRadius _radius = BorderRadius.vertical(
    top: Radius.circular(Glass.radiusCard + 4),
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    return ClipRRect(
      borderRadius: _radius,
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: Glass.fillStrong(theme.colorScheme, dark),
            borderRadius: _radius,
            border: Border.all(
              color: Glass.stroke(theme.colorScheme, dark),
              width: Glass.strokeWidth,
            ),
          ),
          // 玻璃面铺到屏幕边缘（好看），但内容要避开手势指示条：
          // 弹层默认 useSafeArea=false，不垫这一下就会被系统侵入区压住。
          //
          // 垫完必须用 removePadding 把这份底部内边距"吃掉"，否则调用点自己包的
          // SafeArea 会再加一遍，底部凭空多出一倍留白。用 padding 而非
          // viewPadding：键盘弹起时它自动归零，不会在键盘上方留一条空隙。
          child: Padding(
            padding: EdgeInsets.only(
              bottom: MediaQuery.paddingOf(context).bottom,
            ),
            child: MediaQuery.removePadding(
              context: context,
              removeBottom: true,
              child: showDragHandle
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          child: Container(
                            width: 32,
                            height: 4,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.onSurfaceVariant
                                  .withValues(alpha: 0.4),
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ),
                        Flexible(child: child),
                      ],
                    )
                  : child,
            ),
          ),
        ),
      ),
    );
  }
}

/// 对话框的真磨砂背景：在弹窗后面铺一层整屏 [BackdropFilter]。
///
/// 为什么不做成"弹窗自身透明 + 弹窗内部包模糊"：那要改动每个 `AlertDialog` 的
/// 内部结构（其中有可滚动内容与自定义布局），约束一改就可能翻车，而本机没有
/// 设备可目视验证。整屏模糊零侵入、是 iOS/macOS 的经典做法，配合弹窗降到
/// [Glass.dialogAlpha]，透过弹窗也能看见模糊层。
class GlassDialogBackdrop extends StatelessWidget {
  const GlassDialogBackdrop({
    super.key,
    required this.child,
    this.blur = Glass.blurPanel,
  });

  final Widget child;
  final double blur;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Positioned.fill(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
            child: const SizedBox.expand(),
          ),
        ),
        // 必须 Positioned.fill：Stack 给非定位子节点的是松约束，
        // AlertDialog 内部靠 Center 居中，松约束下会缩到左上角。
        Positioned.fill(child: child),
      ],
    );
  }
}

/// 弹出带真磨砂背景的对话框；除了背景，行为与 [showDialog] 一致。
Future<T?> showGlassDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
  bool useRootNavigator = true,
}) {
  return showDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    useRootNavigator: useRootNavigator,
    builder: (dialogContext) =>
        GlassDialogBackdrop(child: builder(dialogContext)),
  );
}

/// 弹出真磨砂底部弹层：把宿主背景清成透明，再由 [GlassSheet] 自己画玻璃面。
Future<T?> showGlassSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool isScrollControlled = true,
  bool isDismissible = true,
  bool useSafeArea = false,
  bool showDragHandle = false,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: isScrollControlled,
    isDismissible: isDismissible,
    useSafeArea: useSafeArea,
    backgroundColor: Colors.transparent,
    elevation: 0,
    builder: (sheetContext) => GlassSheet(
      showDragHandle: showDragHandle,
      child: builder(sheetContext),
    ),
  );
}

/// 单选项：值 + 展示文案。
class GlassOption<T> {
  const GlassOption(this.value, this.label);
  final T value;
  final String label;
}

/// 玻璃弹层单选：替代 Material [DropdownMenu]（它会在字段下方弹一个和玻璃面
/// 冲突的菜单，很难看）。改为从底部弹一张玻璃面列选项，点中即回调。
///
/// 用「列表包一层」区分「选了某项」与「点空关闭」：`showGlassSheet` 返回 null 表示
/// 用户没选就关了（不改动），返回 `[value]` 才是真选择——这样即便 [T] 可空
/// （如 `Locale?` 的「跟随系统」），也能把「选了 null」和「没选」分清。
Future<void> glassSelect<T>({
  required BuildContext context,
  required String title,
  required List<GlassOption<T>> options,
  required T current,
  required ValueChanged<T> onSelected,
}) async {
  final result = await showGlassSheet<List<T>>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) {
      final theme = Theme.of(sheetContext);
      return ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(sheetContext).size.height * 0.7,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 2, 20, 10),
              child: Text(
                title,
                textAlign: TextAlign.center,
                style: theme.textTheme.titleLarge,
              ),
            ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
                children: [
                  for (final o in options)
                    ListTile(
                      title: Text(o.label),
                      selected: o.value == current,
                      trailing: o.value == current
                          ? Icon(Icons.check, color: theme.colorScheme.primary)
                          : null,
                      onTap: () => Navigator.of(sheetContext).pop(<T>[o.value]),
                    ),
                ],
              ),
            ),
          ],
        ),
      );
    },
  );
  if (result != null && result.isNotEmpty) onSelected(result.first);
}

/// 触发 [glassSelect] 的字段块：左标题、下方当前值、右「展开」标；点开弹玻璃单选。
/// 用来替换设置里的 [DropdownMenu]，观感与其它玻璃弹层统一。
class GlassSelectTile extends StatelessWidget {
  const GlassSelectTile({
    super.key,
    required this.label,
    required this.value,
    required this.onTap,
    this.icon,
  });

  final String label;
  final String value;
  final VoidCallback onTap;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        leading: icon == null ? null : Icon(icon, color: cs.primary),
        title: Text(label),
        subtitle: Text(value, style: TextStyle(color: cs.onSurfaceVariant)),
        trailing: Icon(Icons.expand_more, color: cs.onSurfaceVariant),
        onTap: onTap,
      ),
    );
  }
}

/// 真磨砂玻璃面板：[BackdropFilter] 模糊背后内容 + 半透明填充 + 发丝描边。
///
/// 开销明显高于半透明，**只用于少数高价值面**（首页门面卡、顶栏、悬浮层）；
/// 大量列表卡片请直接用主题里的 Card 玻璃皮。
class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.radius = Glass.radiusCard,
    this.blur = Glass.blurPanel,
    this.strong = true,
    this.padding,
    this.onTap,
  });

  final Widget child;
  final double radius;
  final double blur;
  final bool strong;
  final EdgeInsetsGeometry? padding;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = Glass.isDark(theme);
    final content = Padding(
      padding: padding ?? EdgeInsets.zero,
      child: child,
    );
    return DecoratedBox(
      // 投影要画在裁剪之外，否则会被 ClipRRect 一起裁掉。
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        boxShadow: Glass.shadow(dark, strong: true),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: strong
                  ? Glass.fillStrong(theme.colorScheme, dark)
                  : Glass.fill(theme.colorScheme, dark),
              borderRadius: BorderRadius.circular(radius),
              border: Border.all(
                color: Glass.stroke(theme.colorScheme, dark),
                width: Glass.strokeWidth,
              ),
            ),
            child: onTap == null
                ? content
                : Material(
                    color: Colors.transparent,
                    child: InkWell(onTap: onTap, child: content),
                  ),
          ),
        ),
      ),
    );
  }
}
