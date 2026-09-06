# CHANGE_MANIFEST R2

| 批 | 文件 | 验证 | GATE（前 12） |
|---|---|---|---|
| Wave 1 | K1 HomeChannel.kt/HomeState.kt/HomeStateTest.kt + HomeActions.kt；K2 四共享文件 + SharedHelpersTest + 四通道文件；K3 删 3 文件 + build.gradle 删 4 依赖；D1 glass_alert/glass_notice_card + home 拆 3 文件 + home_screen_test；D3 onetools 两文件；协调者 FlutterHostActivity 委托 / app_shell | flutter test 72；gradle 72 tests | 2ed09ac9d397 |
| Wave 2 | D2 apps/settings/pairing 采用共享组件；K4 HomeActions 采用 LocaleLabels/applyBootToggle + 5 个 strings.xml nav_home/nav_apps + HomeStateTest；D1 I-5 弹窗类型修复 + 2 测试；协调者 SettingsChannel 落盘统一、GlassAlert KDoc | flutter analyze No issues；flutter test 74；gradle 73 tests | 2ed09ac9d397 |
