package cn.ianzb.hyperrefine.hook.systemui

import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs

/**
 * 系统界面（com.android.systemui）目标 Load：只保留融合设备中心的三个开关。
 *
 * 安装策略：
 * - 「隐藏省略号」「缩小设备点击范围」的回调里每次都会读开关，故**无条件安装**，
 *   开关一改就立即生效，不必重启系统界面；
 * - 「加快图标加载」的回调只是改写参数、不看开关，因此仍按开关决定是否安装
 *   （代价是改这个开关要重启系统界面）。
 */
class SystemUiLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf(TARGET_PACKAGE)

    override fun onPackageLoaded(target: PackageTarget) {
        initHook(
            DeviceCenterFastLoadHook(),
            HookPrefs.getBoolean(DeviceCenterFastLoadHook.KEY, false),
        )
        initHook(DeviceCenterMoreHook(), true)
        initHook(DeviceCenterHitAreaHook(), true)
    }

    companion object {
        const val TARGET_PACKAGE = "com.android.systemui"
    }
}
