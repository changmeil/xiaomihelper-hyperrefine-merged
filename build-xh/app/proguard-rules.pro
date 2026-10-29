# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class org.luckypray.dexkit.DexKitBridge {
    native <methods>;
}

-keep class kotlin.Unit
-keep class kotlin.jvm.functions.Function0

# FreeReflection
-keep class me.weishu.reflection.** {*;}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static *** throwUninitializedProperty(...);
    public static *** throwUninitializedPropertyAccessException(...);
}

-keep class * extends android.app.Activity
-keep class * implements androidx.viewbinding.ViewBinding {
    <init>();
    *** inflate(android.view.LayoutInflater);
}

-keep class dev.lackluster.mihelper.app.MainActivity
-keep class dev.lackluster.mihelper.hook.HookEntry

-dontwarn java.lang.reflect.AnnotatedType

-keepnames class dev.lackluster.mihelper.app.repository.** { *; }
-keepnames class dev.lackluster.mihelper.app.screen.**.*ViewModel { *; }

-keepnames class org.koin.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# Xposed
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
}
-keepclassmembers,allowoptimization class ** implements io.github.libxposed.api.XposedInterface$Hooker {
    public *** intercept(...);
}

-keepnames class * extends dev.lackluster.mihelper.hook.base.BaseHooker
# 迁移自 HyperRefine 的第二个入口：保留类名，便于 LSPosed 日志里定位（否则会被混淆成 er0）
-keep class cn.ianzb.hyperrefine.hook.xposed.HfEntry
-keep class cn.ianzb.hyperrefine.hook.base.BaseHook
-keep class cn.ianzb.hyperrefine.hook.base.BaseLoad

# 内置第三方模块（微信小程序兼容 / 支付宝极速模式）：
# 它们的 dex 放在 assets/modules/ 里，运行期用 InMemoryDexClassLoader 加载，
# 引用的类名与成员名来自外部 dex，R8 看不出来，必须整包保留。
-keep class de.robv.android.xposed.** { *; }
-keep class cn.ianzb.hyperrefine.hook.embed.** { *; }
