package cn.ianzb.hyperrefine.hook.base

import cn.ianzb.hyperrefine.hook.xposed.HookHelper

/**
 * 按目标包组织的一组 hook（移植精简版）。
 *
 * 子类在 [onPackageLoaded] 中通过 [initHook] 声明本包需要安装的 hook 及开关状态。
 * 原版还负责 DexKit 会话与原生 hook 装载，合入 XiaomiHelper 后这两块由宿主提供。
 */
abstract class BaseLoad {

    /** 本 Load 负责的目标包名。 */
    abstract val targetPackages: List<String>

    private val pendingHooks = mutableListOf<Pair<BaseHook, Boolean>>()

    @Volatile
    private var currentTarget: PackageTarget? = null

    /** 目标包就绪时回调，子类在此注册 hook。 */
    protected open fun onPackageLoaded(target: PackageTarget) {}

    /** 注册一条 hook 及其启用状态（通常来自配置键）。 */
    protected fun initHook(hook: BaseHook, enabled: Boolean) {
        pendingHooks.add(hook to enabled)
    }

    fun onPackageReady(target: PackageTarget) {
        currentTarget = target
        pendingHooks.clear()
        runCatching { onPackageLoaded(target) }
            .onFailure { HookHelper.log("onPackageLoaded failed: ${target.packageName}", it) }

        pendingHooks.forEach { (hook, enabled) ->
            if (enabled) install(hook)
        }
    }

    private fun install(hook: BaseHook) {
        val target = currentTarget ?: return
        try {
            hook.apply(target)
            HookHelper.log("${hook.tag} hook success @ ${target.packageName}")
        } catch (t: Throwable) {
            HookHelper.log("${hook.tag} hook failed @ ${target.packageName}", t)
        }
    }
}
