package cn.ianzb.hyperrefine.hook.connect

import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget
import cn.ianzb.hyperrefine.hook.xposed.Reflect

/**
 * MiLink（`com.milink.service`）目标 Load：只保留妙播音频低延迟。
 *
 * 按「目标类是否存在」路由进程，避免在不相关的 `:ui` / `crossdeviceservice` 进程里白挂 hook。
 */
class MiLinkLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf(TARGET_PACKAGE)

    override fun onPackageLoaded(target: PackageTarget) {
        val loader = target.classLoader ?: return
        if (Reflect.findClassIfExists(MiPlayDelayHook.TARGET_CLASS, loader) != null) {
            // 回调里每次都会读开关，故无条件安装，开关一改立刻生效。
            initHook(MiPlayDelayHook(), true)
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.milink.service"
    }
}
