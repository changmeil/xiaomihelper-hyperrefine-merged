package de.robv.android.xposed

import java.lang.reflect.Member

/**
 * 老版（`de.robv.android.xposed`）API 的兼容桩，只实现本工程内置模块真正用到的那一小部分。
 *
 * 作用不是「还原 Xposed 框架」，而是让按老 API 编译的第三方模块，
 * 能在 libxposed（API 101）的模块进程里被原样加载并工作：
 *
 * ```
 * 第三方模块 (dex, 编译期依赖 de.robv.*)  →  本桩  →  HookHelper  →  libxposed
 * ```
 *
 * 第三方模块的 dex 通过 [cn.ianzb.hyperrefine.hook.embed.EmbeddedModules] 运行时加载，
 * 其父 ClassLoader 就是本模块的 ClassLoader，因此这里的 `de.robv.*` 会先于任何残留实现被解析到。
 *
 * 注意：这些类的类名/字段/方法签名必须与老 API 完全一致（见 proguard 里的 `-keep`），
 * 否则第三方 dex 会抛 `NoSuchMethodError` / `NoSuchFieldError`。
 */
abstract class XC_MethodHook {

    /** 原方法执行前回调；可修改 [MethodHookParam.args]，或 `setResult` 跳过原方法。 */
    open fun beforeHookedMethod(param: MethodHookParam) {}

    /** 原方法执行后回调；可通过 `getResult` / `setResult` 改写返回值。 */
    open fun afterHookedMethod(param: MethodHookParam) {}

    /** hook 句柄，供第三方模块主动卸载（多数模块只是丢弃返回值）。 */
    class Unhook internal constructor(private val handle: Any?) {
        fun unhook() {
            (handle as? io.github.libxposed.api.XposedInterface.HookHandle)?.unhook()
        }
    }

    class MethodHookParam @JvmOverloads constructor(
        @JvmField val method: Member? = null,
        @JvmField var thisObject: Any? = null,
        @JvmField var args: Array<Any?> = emptyArray(),
    ) {
        /** 执行结果。字段直接暴露，与老 API 一致（老 API 里它也是 public 字段）。 */
        @JvmField
        var result: Any? = null

        /** 原方法抛出的异常。 */
        @JvmField
        var throwable: Throwable? = null

        /** 是否已通过 [setResult] 指定结果。 */
        @JvmField
        var hasResult: Boolean = false

        fun getResult(): Any? = result

        fun setResult(value: Any?) {
            result = value
            hasResult = true
        }

        fun getArgs(): Array<Any?> = args

        fun getThisObject(): Any? = thisObject

        fun getMethod(): Member? = method

        fun getThrowable(): Throwable? = throwable

        fun setThrowable(t: Throwable?) {
            throwable = t
        }

        fun hasThrowable(): Boolean = throwable != null
    }
}
