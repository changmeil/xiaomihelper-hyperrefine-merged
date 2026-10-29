package cn.ianzb.hyperrefine.hook.aod

import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs

/**
 * 全屏 AOD 修复目标 Load。
 *
 * 只作用于 `system`（system_server）——显示框架的亮度策略、息屏自动亮度、LocalDisplayAdapter
 * 都在框架进程内，与 AOD 应用（`com.miui.aod`）无关。
 */
class AodBridgeLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf(TARGET_SYSTEM)

    override fun onPackageLoaded(target: PackageTarget) {
        if (!target.isSystemServer) return
        initHook(
            AodDozeBridgeHook(),
            HookPrefs.getBoolean(AodBridgeKeys.FULL_AOD_BRIDGE, false),
        )
    }

    companion object {
        const val TARGET_SYSTEM = "system"
    }
}
