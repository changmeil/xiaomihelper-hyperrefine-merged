package cn.ianzb.hyperrefine.hook.base

import cn.ianzb.hyperrefine.hook.xposed.HookHelper

/**
 * 单条 hook 规则的极简基类（移植版）。
 *
 * 原 HyperRefine 版本带有 DexKit / 原生 hook / 版本门禁 / 状态上报等基础设施，
 * 合入 XiaomiHelper 时这些能力由宿主模块提供，迁移过来的 7 条规则都只需要
 * 「拿到目标包 ClassLoader → 反射挂 hook」这一条路径，故裁剪为最小实现。
 */
abstract class BaseHook {

    /** 日志 TAG。 */
    open val tag: String get() = javaClass.simpleName

    /** 绑定的配置键（与 App 侧 PreferenceKey 同名）。 */
    open val key: String get() = javaClass.simpleName

    /** 本条规则覆盖的配置键。 */
    open val keys: List<String> get() = listOf(key)

    /** 当前目标包信息，由 [BaseLoad] 在安装前注入。 */
    protected lateinit var target: PackageTarget

    /** 执行挂载。 */
    open fun init() {}

    internal fun apply(target: PackageTarget) {
        this.target = target
        init()
    }

    /** 供子类统一记录失败日志。 */
    protected fun logFailure(what: String, throwable: Throwable) {
        HookHelper.log("$tag: $what failed", throwable)
    }
}
