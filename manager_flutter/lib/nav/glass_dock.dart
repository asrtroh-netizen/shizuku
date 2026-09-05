import 'package:flutter/material.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/onetools/liquid_glass.dart';

/// 悬浮液态玻璃底栏：胶囊岛 + 轻模糊 + 轻微透镜，内容从两侧透出。
///
/// 内部仍用 [NavigationBar]，保留 M3 无障碍与既有 widget 测试的 `find.byType`。
///
/// [NavigationBar] 自带 SafeArea。岛已经用外层 padding 抬离手势条，必须把底部
/// padding 吃掉，否则岛会被垫高一截、胶囊被拉成长条。
class GlassDock extends StatelessWidget {
  const GlassDock({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<NavigationDestination> destinations;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final bottomInset = MediaQuery.viewPaddingOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        Glass.pageMargin,
        0,
        Glass.pageMargin,
        Glass.dockBottomGap + bottomInset,
      ),
      child: LiquidGlass(
        radius: Glass.radiusDock,
        child: MediaQuery.removePadding(
          context: context,
          removeBottom: true,
          child: Theme(
            data: theme.copyWith(
              navigationBarTheme: theme.navigationBarTheme.copyWith(
                backgroundColor: Colors.transparent,
                elevation: 0,
                shadowColor: Colors.transparent,
                surfaceTintColor: Colors.transparent,
                indicatorColor: scheme.primary.withValues(alpha: 0.18),
                overlayColor: WidgetStateProperty.resolveWith((states) {
                  if (states.contains(WidgetState.pressed)) {
                    return scheme.primary.withValues(alpha: 0.10);
                  }
                  return Colors.transparent;
                }),
                iconTheme: WidgetStateProperty.resolveWith((states) {
                  final selected = states.contains(WidgetState.selected);
                  return IconThemeData(
                    size: 22,
                    color: selected ? scheme.primary : scheme.onSurfaceVariant,
                  );
                }),
                labelTextStyle: WidgetStateProperty.resolveWith((states) {
                  final selected = states.contains(WidgetState.selected);
                  return theme.textTheme.labelSmall?.copyWith(
                    fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                    color: selected ? scheme.primary : scheme.onSurfaceVariant,
                  );
                }),
              ),
            ),
            child: NavigationBar(
              backgroundColor: Colors.transparent,
              elevation: 0,
              shadowColor: Colors.transparent,
              surfaceTintColor: Colors.transparent,
              height: Glass.dockHeight,
              selectedIndex: selectedIndex,
              onDestinationSelected: onDestinationSelected,
              destinations: destinations,
            ),
          ),
        ),
      ),
    );
  }
}
