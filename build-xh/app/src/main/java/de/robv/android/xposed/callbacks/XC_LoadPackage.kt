package de.robv.android.xposed.callbacks

import android.content.pm.ApplicationInfo

/**
 * 老 API 的 `XC_LoadPackage` 兼容桩。
 *
 * 第三方模块只读 [LoadPackageParam.packageName] 与 [LoadPackageParam.classLoader]，
 * 其余字段为对齐老 API 补齐。
 */
class XC_LoadPackage {

    class LoadPackageParam @JvmOverloads constructor(
        @JvmField var packageName: String = "",
        @JvmField var classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
        @JvmField var appInfo: ApplicationInfo? = null,
        @JvmField var isFirstApplication: Boolean = true,
    ) {
        @JvmField
        var processName: String = packageName

        @JvmField
        var isFirstPackage: Boolean = isFirstApplication
    }
}
