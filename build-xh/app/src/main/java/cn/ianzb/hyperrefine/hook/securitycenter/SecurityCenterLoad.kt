package cn.ianzb.hyperrefine.hook.securitycenter

import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget

/**
 * 安全服务（com.miui.securitycenter）目标 Load：
 * 隐藏 90W 及以上快充机型在快充加速时发布的「快充加速通知」。
 * 进入提醒与退出提醒各有独立开关。
 *
 * 注意：这里**无条件安装**两条 hook（第二参数恒为 true）。原因是开关值在 hook 回调里
 * 每次都重新读，安装时不必看开关；反过来，如果安装时就按开关判断，用户在安全服务进程
 * 已经起来之后才打开开关，hook 根本没挂上，必须手动杀掉安全服务才生效——这正是
 * 「开关打开了但不生效」的成因。
 */
class SecurityCenterLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf(TARGET_PACKAGE)

    override fun onPackageLoaded(target: PackageTarget) {
        initHook(FastChargeEnterNotifyHook(), true)
        initHook(FastChargeExitNotifyHook(), true)
    }

    companion object {
        const val TARGET_PACKAGE = "com.miui.securitycenter"
    }
}
