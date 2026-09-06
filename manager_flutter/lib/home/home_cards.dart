import 'package:flutter/material.dart';
import 'package:manager_flutter/home/home_models.dart';
import 'package:manager_flutter/onetools/glass.dart';

// 首页卡片：动作卡 / 快捷磁贴网格 / ADB 受限横幅（从 home_screen.dart 原样搬出，只改名加前缀）。

class HomeActionCard extends StatelessWidget {
  const HomeActionCard({
    super.key,
    required this.icon,
    required this.title,
    required this.children,
  });

  final IconData icon;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GlassPanel(
      padding: const EdgeInsets.all(Glass.padCard),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: cs.primary),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }
}

class HomeQuickGrid extends StatelessWidget {
  const HomeQuickGrid({
    super.key,
    required this.snap,
    required this.onApps,
    required this.onTerminal,
    required this.onRoot,
    required this.onAdb,
  });

  final HomeSnapshot snap;
  final VoidCallback onApps;
  final VoidCallback onTerminal;
  final VoidCallback onRoot;
  final VoidCallback onAdb;

  @override
  Widget build(BuildContext context) {
    final copy = snap.copy;
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: HomeQuickTile(
                icon: Icons.apps_outlined,
                title: copy.appsTitle,
                subtitle: snap.appsSub,
                dimmed: !snap.running,
                onTap: onApps,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: HomeQuickTile(
                icon: Icons.terminal,
                title: copy.terminalTitle,
                subtitle: copy.terminalSub,
                dimmed: !(snap.running && snap.permission),
                onTap: onTerminal,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: HomeQuickTile(
                icon: Icons.admin_panel_settings_outlined,
                title: copy.rootTitle,
                subtitle: snap.rootSub,
                dimmed: !snap.rooted,
                onTap: onRoot,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: HomeQuickTile(
                icon: Icons.dns_outlined,
                title: copy.adbTitle,
                subtitle: copy.adbSub,
                dimmed: false,
                onTap: onAdb,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class HomeQuickTile extends StatelessWidget {
  const HomeQuickTile({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.dimmed,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool dimmed;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GlassPanel(
      onTap: onTap,
      padding: const EdgeInsets.all(18),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 124),
        child: Opacity(
          opacity: dimmed ? 0.55 : 1,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: dimmed ? cs.onSurface.withValues(alpha: 0.4) : cs.primary),
              const SizedBox(height: 10),
              Text(
                title,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                subtitle,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: cs.onSurface.withValues(alpha: dimmed ? 0.45 : 0.78),
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class HomeLimitedBanner extends StatelessWidget {
  const HomeLimitedBanner({super.key, required this.title, required this.onTap});

  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.errorContainer,
      borderRadius: BorderRadius.circular(Glass.radiusCard),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Glass.radiusCard),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Icon(Icons.warning_amber_outlined, color: cs.error),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
