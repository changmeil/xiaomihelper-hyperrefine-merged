package cn.ianzb.hyperrefine.hook.splash

/**
 * 学习通开屏替换的常量（源码级并入，原项目 ChaoxingSplash 1.9.4）。
 *
 * 与上游独立模块的差别：**只保留内置默认图**，去掉了「自定义选图」那套
 * （文件 / Base64 双通道 + 界面选图页），所以这里没有 [SplashStore] 对应的东西。
 */
object SplashKeys {

    /** 开关键，与 `Preferences.ThirdParty.CHAOXING_SPLASH` 一致。 */
    const val REPLACE = "chaoxing_splash_replace"

    /** 默认开关状态：装了就该生效，用户要的正是这个效果。 */
    const val DEFAULT_ON = true

    /** 宿主总开关（`Preferences.Module.MODULE_ENABLED`），关掉时所有功能一起停。 */
    const val MASTER_SWITCH = "enable_module"

    /** 目标应用。 */
    const val PKG = "com.chaoxing.mobile"

    /** HyperOS 上启动窗口的 ViewRootImpl 所在的系统进程。 */
    const val SYSTEMUI = "com.android.systemui"

    /** 开屏页用到的 drawable 资源名（不同版本用其中之一）。 */
    val DRAWABLES = arrayOf("splash", "splash_1080")

    /** 模块 APK 内置的替换图（zip entry 路径）。 */
    const val ASSET = "assets/splash_default.jpg"

    /** 日志标签，`adb logcat -s CxSplash` 直接看。 */
    const val TAG = "CxSplash"
}
