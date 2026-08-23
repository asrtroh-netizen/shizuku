-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}

-assumenosideeffects class java.util.Objects{
    ** requireNonNull(...);
}

-keepnames class moe.shizuku.api.BinderContainer

# Missing class android.app.IProcessObserver$Stub
# Missing class android.app.IUidObserver$Stub
-keepclassmembers class rikka.hidden.compat.adapter.ProcessObserverAdapter {
    <methods>;
}

-keepclassmembers class rikka.hidden.compat.adapter.UidObserverAdapter {
    <methods>;
}

# Entrance of Shizuku service
-keep class rikka.shizuku.server.ShizukuService {
    public static void main(java.lang.String[]);
}

# Entrance of user service starter
-keep class moe.shizuku.starter.ServiceStarter {
    public static void main(java.lang.String[]);
}

# Entrance of shell
-keep class moe.shizuku.manager.shell.Shell {
    public static void main(java.lang.String[], java.lang.String, android.os.IBinder, android.os.Handler);
}

-assumenosideeffects class android.util.Log {
    public static *** d(...);
}

-assumenosideeffects class moe.shizuku.manager.utils.Logger {
    public *** d(...);
}

#noinspection ShrinkerUnresolvedReference
-assumenosideeffects class rikka.shizuku.server.util.Logger {
    public *** d(...);
}

# V15.1.2 真机 dex：K60 = ViewModel，只剩 a/b/c/d；X3 仍 invoke-virtual K60.clear()
# → NoSuchMethodError。fullMode + allowaccessmodification 会裁掉方法却留下调用。
# 不能全局 -dontoptimize：rikkax_lifecycle_ViewModel 仍调 clear()，而 2.9 的 JVM 名是
# clear$lifecycle_viewmodel_release；关掉优化会让这处调用永远对不上。
-repackageclasses rikka.shizuku
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Flutter add-to-app：Launcher / JNI / 插件入口不能被 R8 改名或裁掉。
# `-repackageclasses rikka.shizuku` 尤其会误伤 io.flutter 与 FlutterHostActivity。
-keep class moe.shizuku.manager.flutter.FlutterHostActivity { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.collection.** { *; }
# 15.1.3 mapping：K60 = androidx.lifecycle.ViewModel，X3 = AndroidComposeView_androidKt
-keep class androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * {
    void clear();
}
-dontwarn io.flutter.embedding.**
-dontwarn io.flutter.plugin.**
-dontwarn android.**
-if class * implements io.flutter.embedding.engine.plugins.FlutterPlugin
-keep,allowshrinking,allowobfuscation class <1>

# Flutter / androidx.window 在设备侧由 OEM 扩展提供，R8 编译期没有这些类。
-dontwarn androidx.window.extensions.WindowExtensions
-dontwarn androidx.window.extensions.WindowExtensionsProvider
-dontwarn androidx.window.extensions.area.ExtensionWindowAreaPresentation
-dontwarn androidx.window.extensions.layout.DisplayFeature
-dontwarn androidx.window.extensions.layout.FoldingFeature
-dontwarn androidx.window.extensions.layout.WindowLayoutComponent
-dontwarn androidx.window.extensions.layout.WindowLayoutInfo
-dontwarn androidx.window.sidecar.SidecarDeviceState
-dontwarn androidx.window.sidecar.SidecarDisplayFeature
-dontwarn androidx.window.sidecar.SidecarInterface$SidecarCallback
-dontwarn androidx.window.sidecar.SidecarInterface
-dontwarn androidx.window.sidecar.SidecarProvider
-dontwarn androidx.window.sidecar.SidecarWindowLayoutInfo
