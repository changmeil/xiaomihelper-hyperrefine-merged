package de.robv.android.xposed

import de.robv.android.xposed.callbacks.XC_LoadPackage

/** 老 API 的包加载入口。 */
interface IXposedHookLoadPackage {

    @Throws(Throwable::class)
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam)
}

/** 老 API 的 zygote 阶段入口（本工程的第三方模块只是实现它，没有实际依赖）。 */
interface IXposedHookZygoteInit {

    fun initZygote(startupParam: StartupParam)

    class StartupParam @JvmOverloads constructor(
        @JvmField var modulePath: String = "",
        @JvmField var startsSystemServer: Boolean = false,
    )
}
