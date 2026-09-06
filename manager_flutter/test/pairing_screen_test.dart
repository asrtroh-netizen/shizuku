import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:manager_flutter/onetools/glass.dart';
import 'package:manager_flutter/pairing/pairing_channel.dart';
import 'package:manager_flutter/pairing/pairing_models.dart';
import 'package:manager_flutter/pairing/pairing_screen.dart';
import 'package:manager_flutter/theme/app_theme.dart';
import 'package:manager_flutter/widgets/glass_notice_card.dart';

const MethodChannel _channel = MethodChannel('shizuku/pairing');

/// 每条文案都带 "!"，证明画到屏幕上的是 JSON 下发的、不是 fallback。
const Map<String, dynamic> _copy = <String, dynamic>{
  'title': 'Pairing title!',
  'back': 'Back!',
  'permissionMissing': 'Permission missing!',
  'notification': 'Notification helps!',
  'notificationBlocked': 'Notifications blocked!',
  'notificationSettings': 'Notification options!',
  'autoPairingNotificationAccessTooltip': 'Need notification access!',
  'network': 'Needs local network!',
  'networkLimitationNotForeground': 'Background limitation!',
  'networkBlocked': 'Local network blocked!',
  'retry': 'Retry!',
  'serviceStartFailed': 'Service start failed!',
  'pairingServiceFailed': 'Pairing service failed!',
  'miui': 'MIUI hint!',
  'miui2': 'MIUI hint 2!',
  'steps': 'Step one!',
  'leftIsClickable': 'Left is clickable!',
  'developmentSettings': 'Developer options!',
  'enterPairingCode': 'Step two!',
  'finish': 'Step three!',
  'ok': 'OK!',
};

/// 默认 = 全就绪（通知开 / 监听开 / 权限有 / 未失败 / 非 MIUI）。
String _snapshot({
  Object? supported = true,
  Object? notificationEnabled = true,
  Object? notificationListenerEnabled = true,
  Object? localNetworkPermissionGranted = true,
  Object? pairingServiceStartFailed = false,
  Object? autoPairingEnabled = false,
  Object? showMiuiHint = false,
  Object? copy = _copy,
}) =>
    jsonEncode(<String, dynamic>{
      'ok': true,
      'supported': supported,
      'notificationEnabled': notificationEnabled,
      'notificationListenerEnabled': notificationListenerEnabled,
      'localNetworkPermissionGranted': localNetworkPermissionGranted,
      'pairingServiceStartFailed': pairingServiceStartFailed,
      'autoPairingEnabled': autoPairingEnabled,
      'showMiuiHint': showMiuiHint,
      'copy': copy,
    });

void _mock(Future<Object?> Function(MethodCall call)? handler) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(_channel, handler);
}

/// 打桩宿主：`getState` 回 [state]；`start` 回 [afterStart]（默认 [state]）；
/// `requestLocalNetworkPermission` 回 [afterRequest]（默认 [state]）；其它动作回 `{ok:true}`。
/// 返回按序记录的全部调用。
List<MethodCall> _mockHost({
  required String state,
  String? afterStart,
  String? afterRequest,
}) {
  final calls = <MethodCall>[];
  _mock((call) async {
    calls.add(call);
    switch (call.method) {
      case 'getState':
        return state;
      case 'start':
        return afterStart ?? state;
      case 'requestLocalNetworkPermission':
        return afterRequest ?? state;
      default:
        return jsonEncode(<String, dynamic>{'ok': true});
    }
  });
  return calls;
}

/// 打桩事件通道，返回可向页面推 `"changed"` 的 sink 引用（listen 之前为 null）。
/// 回调必须是块体：SDK 会把 `onListen` 的返回值当作 `listen` 方法的回复去编码。
_SinkRef _mockEvents() {
  final ref = _SinkRef();
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockStreamHandler(
    PairingChannel.events,
    MockStreamHandler.inline(
      onListen: (_, events) {
        ref.sink = events;
      },
      onCancel: (_) {
        ref.sink = null;
      },
    ),
  );
  return ref;
}

class _SinkRef {
  MockStreamHandlerEventSink? sink;
}

int _count(List<MethodCall> calls, String method) =>
    calls.where((c) => c.method == method).length;

/// 卡片叠起来会超过默认视口；拉高让整页都在树里。
Future<void> _pumpPairing(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: const PairingScreen(),
    ),
  );
  await tester.pumpAndSettle();
}

/// "服务启动失败"卡 = 页面上唯一一张主题 Card，底色必须是 errorContainer。
Finder get _errorCard => find.byType(Card);

void main() {
  tearDown(() {
    _mock(null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockStreamHandler(PairingChannel.events, null);
  });

  testWidgets(
      'no host: fallback renders (title, back arrow, notification-blocked card), '
      'start called exactly once, no crash', (tester) async {
    final calls = <MethodCall>[];
    _mock((call) async {
      calls.add(call);
      throw MissingPluginException();
    });
    await _pumpPairing(tester);

    const f = PairingCopy.fallback;
    expect(find.text(f.title), findsOneWidget);
    expect(find.byIcon(Icons.arrow_back_outlined), findsOneWidget);
    expect(find.byTooltip(f.back), findsOneWidget);
    // 无宿主 = 通知未开：只有通知引导段 + 按钮，没有三步。
    expect(find.text(f.permissionMissing), findsOneWidget);
    expect(find.text(f.notificationBlocked), findsOneWidget);
    expect(find.text(f.notificationSettings), findsOneWidget);
    expect(find.text(f.steps), findsNothing);
    expect(find.text(f.serviceStartFailed), findsNothing);
    expect(find.byType(GlassPanel), findsOneWidget);
    expect(_errorCard, findsNothing);

    expect(_count(calls, 'start'), 1);
    expect(_count(calls, 'getState'), greaterThanOrEqualTo(1));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'notificationEnabled=false: notification card + button; tap -> openNotificationOptions',
      (tester) async {
    final calls = _mockHost(state: _snapshot(notificationEnabled: false));
    await _pumpPairing(tester);

    expect(find.text('Pairing title!'), findsOneWidget);
    expect(find.text('Permission missing!'), findsOneWidget);
    expect(find.text('Notifications blocked!'), findsOneWidget);
    expect(find.text('Notification options!'), findsOneWidget);
    // 通知未开时 Compose 不显示网络段、也不显示三步。
    expect(find.text('Needs local network!'), findsNothing);
    expect(find.text('Step one!'), findsNothing);
    expect(find.text(PairingCopy.fallback.notificationBlocked), findsNothing);

    await tester.tap(find.text('Notification options!'));
    await tester.pumpAndSettle();

    expect(_count(calls, 'openNotificationOptions'), 1);
    expect(_count(calls, 'openDeveloperOptions'), 0);
    expect(
      calls.singleWhere((c) => c.method == 'openNotificationOptions').arguments,
      isNull,
    );
  });

  testWidgets(
      'all ready: notification + network callouts, three steps, developer options button; '
      'tap -> openDeveloperOptions', (tester) async {
    final calls = _mockHost(state: _snapshot());
    await _pumpPairing(tester);

    expect(find.text('Notification helps!'), findsOneWidget);
    expect(find.text('Needs local network!'), findsOneWidget);
    expect(find.text('Background limitation!'), findsOneWidget);
    expect(find.text('Step one!'), findsOneWidget);
    expect(find.text('Left is clickable!'), findsOneWidget);
    expect(find.text('Step two!'), findsOneWidget);
    expect(find.text('Step three!'), findsOneWidget);
    expect(find.text('Developer options!'), findsOneWidget);
    expect(find.text('Permission missing!'), findsNothing);
    expect(find.text('Service start failed!'), findsNothing);
    expect(find.text('MIUI hint!'), findsNothing);
    // 状态卡 + 三步卡 = 2 张页面级 GlassPanel；没有 errorContainer 卡。
    expect(find.byType(GlassPanel), findsNWidgets(2));
    expect(_errorCard, findsNothing);
    expect(find.byType(FilledButton), findsOneWidget);

    await tester.tap(find.text('Developer options!'));
    await tester.pumpAndSettle();

    expect(_count(calls, 'openDeveloperOptions'), 1);
    expect(_count(calls, 'openNotificationOptions'), 0);
    // 进入 = getState + start；open* 不带回快照也不触发重拉。
    expect(_count(calls, 'start'), 1);
    expect(_count(calls, 'getState'), 1);
  });

  testWidgets(
      'pairingServiceStartFailed=true: errorContainer card appears, steps hidden; '
      'retry -> requestLocalNetworkPermission and returned snapshot is applied',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(pairingServiceStartFailed: true),
      afterRequest: _snapshot(pairingServiceStartFailed: false),
    );
    await _pumpPairing(tester);

    expect(find.text('Service start failed!'), findsOneWidget);
    expect(find.text('Pairing service failed!'), findsOneWidget);
    expect(find.text('Retry!'), findsOneWidget);
    expect(find.text('Step one!'), findsNothing);
    expect(_errorCard, findsOneWidget);
    final cs = AppTheme.light().colorScheme;
    expect(tester.widget<Card>(_errorCard).color, cs.errorContainer);
    // 其它面都不是 errorContainer：状态卡仍是 GlassPanel。
    expect(find.byType(GlassPanel), findsOneWidget);

    await tester.tap(find.text('Retry!'));
    await tester.pumpAndSettle();

    expect(_count(calls, 'requestLocalNetworkPermission'), 1);
    // 重试带回"未失败"的快照 → 直接应用：错误卡消失、三步出现，且没有二次 getState。
    expect(_errorCard, findsNothing);
    expect(find.text('Step one!'), findsOneWidget);
    expect(_count(calls, 'getState'), 1);
  });

  testWidgets(
      'bad input: supported is a string -> treated as true (normal branch), '
      'other bad types -> defaults, no crash', (tester) async {
    _mockHost(
      state: _snapshot(
        supported: 'yes',
        notificationEnabled: 'true',
        localNetworkPermissionGranted: 1,
        pairingServiceStartFailed: 'nope',
        showMiuiHint: 0,
      ),
    );
    await _pumpPairing(tester);

    // supported 非 false → 正常分支：不是"只有标题"的占位，而是状态卡。
    // notificationEnabled 非 true → 视为未开 → 通知引导段；localNetworkPermissionGranted 非 false → 视为已授。
    expect(find.text('Permission missing!'), findsOneWidget);
    expect(find.text('Notifications blocked!'), findsOneWidget);
    expect(find.text('Notification options!'), findsOneWidget);
    expect(find.text('Service start failed!'), findsNothing);
    expect(find.text('MIUI hint!'), findsNothing);
    expect(find.byType(GlassPanel), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('supported=false: only the title placeholder, no steps or buttons',
      (tester) async {
    _mockHost(state: _snapshot(supported: false));
    await _pumpPairing(tester);

    // 标题：顶栏一份 + 占位卡一份。
    expect(find.text('Pairing title!'), findsNWidgets(2));
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(find.text('Notification helps!'), findsNothing);
    expect(find.text('Step one!'), findsNothing);
    expect(find.byType(FilledButton), findsNothing);
    expect(find.byType(GlassNoticeCard), findsOneWidget);
  });

  testWidgets(
      'listener disabled but auto-pairing off: no notification access hint (Compose parity)',
      (tester) async {
    _mockHost(state: _snapshot(notificationListenerEnabled: false));
    await _pumpPairing(tester);

    expect(find.text('Need notification access!'), findsNothing);
    expect(find.text('OK!'), findsNothing);
    expect(find.text('Step one!'), findsOneWidget);
  });

  testWidgets(
      'listener disabled and auto-pairing on: hint inside the status panel; '
      'OK -> openNotificationAccessSettings', (tester) async {
    final calls = _mockHost(
      state: _snapshot(notificationListenerEnabled: false, autoPairingEnabled: true),
    );
    await _pumpPairing(tester);

    expect(find.text('Need notification access!'), findsOneWidget);
    expect(find.text('OK!'), findsOneWidget);
    // 全就绪 + 监听提示：仍只有 2 张 GlassPanel（提示合并在状态卡里）。
    expect(find.byType(GlassPanel), findsNWidgets(2));

    await tester.tap(find.text('OK!'));
    await tester.pumpAndSettle();
    expect(_count(calls, 'openNotificationAccessSettings'), 1);
    expect(_count(calls, 'openNotificationOptions'), 0);
  });

  testWidgets(
      'local network blocked: retry -> requestLocalNetworkPermission; '
      'MIUI hint renders its own panel; failed and steps never coexist',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(
        localNetworkPermissionGranted: false,
        showMiuiHint: true,
        pairingServiceStartFailed: true,
      ),
    );
    await _pumpPairing(tester);

    expect(find.text('Local network blocked!'), findsOneWidget);
    expect(find.text('Needs local network!'), findsNothing);
    expect(find.text('MIUI hint!'), findsOneWidget);
    expect(find.text('MIUI hint 2!'), findsOneWidget);
    expect(find.text('Service start failed!'), findsOneWidget);
    expect(find.text('Step one!'), findsNothing);
    // 状态卡 + MIUI 卡 = 2 张 GlassPanel，加 1 张 errorContainer Card；≤ 4 上限成立。
    expect(find.byType(GlassPanel), findsNWidgets(2));
    expect(_errorCard, findsOneWidget);
    // 网络段与错误卡各有一个 Retry。
    expect(find.text('Retry!'), findsNWidgets(2));

    await tester.tap(find.text('Retry!').first);
    await tester.pumpAndSettle();
    expect(_count(calls, 'requestLocalNetworkPermission'), 1);
  });

  testWidgets('"changed" event on shizuku/pairing/events re-pulls getState',
      (tester) async {
    final events = _mockEvents();
    final calls = _mockHost(state: _snapshot());
    await _pumpPairing(tester);
    expect(events.sink, isNotNull);
    expect(_count(calls, 'getState'), 1);

    events.sink!.success('changed');
    await tester.pumpAndSettle();
    expect(_count(calls, 'getState'), 2);

    events.sink!.success('changed');
    await tester.pumpAndSettle();
    expect(_count(calls, 'getState'), 3);
    expect(tester.takeException(), isNull);
  });

  testWidgets('start returning a snapshot is applied without a second getState',
      (tester) async {
    final calls = _mockHost(
      state: _snapshot(notificationEnabled: false),
      afterStart: _snapshot(pairingServiceStartFailed: true),
    );
    await _pumpPairing(tester);

    // getState 说通知未开，start 之后说服务启动失败 → 以 start 带回的为准。
    expect(find.text('Notifications blocked!'), findsNothing);
    expect(find.text('Service start failed!'), findsOneWidget);
    expect(_count(calls, 'getState'), 1);
    expect(_count(calls, 'start'), 1);
  });

  testWidgets('back arrow pops the pushed route', (tester) async {
    _mockHost(state: _snapshot());
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const PairingScreen(),
                  ),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byType(PairingScreen), findsOneWidget);

    await tester.tap(find.byIcon(Icons.arrow_back_outlined));
    await tester.pumpAndSettle();
    expect(find.byType(PairingScreen), findsNothing);
    expect(find.text('open'), findsOneWidget);
  });

  test('PairingSnapshot.fromJson: empty -> empty; bad types -> defaults', () {
    expect(
      PairingSnapshot.fromJson(const <String, dynamic>{}),
      same(PairingSnapshot.empty),
    );

    final snap = PairingSnapshot.fromJson(<String, dynamic>{
      'supported': 'yes',
      'notificationEnabled': 'true',
      'notificationListenerEnabled': 1,
      'localNetworkPermissionGranted': 'no',
      'pairingServiceStartFailed': 0,
      'autoPairingEnabled': 'on',
      'showMiuiHint': <Object>[],
      'copy': 'not-a-map',
    });
    expect(snap.supported, isTrue);
    expect(snap.notificationEnabled, isFalse);
    expect(snap.notificationListenerEnabled, isFalse);
    expect(snap.localNetworkPermissionGranted, isTrue);
    expect(snap.pairingServiceStartFailed, isFalse);
    expect(snap.autoPairingEnabled, isFalse);
    expect(snap.showMiuiHint, isFalse);
    expect(snap.copy.title, PairingCopy.fallback.title);
    expect(snap.readyForPairing, isFalse);
    expect(snap.showNotificationListenerHint, isFalse);

    // unavailable 形态（无宿主）走的也是同一套默认。
    final unavailable = PairingSnapshot.fromJson(
      const <String, dynamic>{'ok': false, 'unavailable': true},
    );
    expect(unavailable.supported, isTrue);
    expect(unavailable.notificationEnabled, isFalse);
    expect(unavailable.localNetworkPermissionGranted, isTrue);
  });

  test('derived flags mirror Compose readyForPairing / listener-hint conditions', () {
    PairingSnapshot make({
      bool notification = true,
      bool listener = true,
      bool network = true,
      bool failed = false,
      bool autoPairing = false,
    }) =>
        PairingSnapshot.fromJson(<String, dynamic>{
          'notificationEnabled': notification,
          'notificationListenerEnabled': listener,
          'localNetworkPermissionGranted': network,
          'pairingServiceStartFailed': failed,
          'autoPairingEnabled': autoPairing,
        });

    expect(make().readyForPairing, isTrue);
    expect(make(notification: false).readyForPairing, isFalse);
    expect(make(network: false).readyForPairing, isFalse);
    expect(make(failed: true).readyForPairing, isFalse);

    expect(make(listener: false).showNotificationListenerHint, isFalse);
    expect(make(listener: false, autoPairing: true).showNotificationListenerHint, isTrue);
    expect(make(listener: true, autoPairing: true).showNotificationListenerHint, isFalse);
  });

  test('PairingCopy.fromJson: keeps given keys, fills the rest from fallback', () {
    expect(PairingCopy.fromJson(null), same(PairingCopy.fallback));
    expect(PairingCopy.fromJson(const <String, dynamic>{}), same(PairingCopy.fallback));
    final copy = PairingCopy.fromJson(<String, dynamic>{
      'title': 'T',
      'retry': 7,
      'ok': '',
    });
    expect(copy.title, 'T');
    expect(copy.retry, PairingCopy.fallback.retry);
    expect(copy.ok, PairingCopy.fallback.ok);
    expect(copy.finish, PairingCopy.fallback.finish);
  });
}
