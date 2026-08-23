package moe.shizuku.manager.flutter

/**
 * Flutter JNI 在 Activity onResume 的瞬间还可能处于 paused。
 * 宿主恢复时不要从原生再推 EventChannel，Dart 的 AppLifecycleState.resumed 会自己拉状态。
 */
internal fun shouldEmitHomeEvent(
    dartExecuting: Boolean,
    hostResumed: Boolean,
    triggeredByHostResume: Boolean,
): Boolean {
    if (triggeredByHostResume) return false
    return dartExecuting && hostResumed
}
