package de.robv.android.xposed

import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member

/**
 * 老 API 的 `XposedBridge` 兼容桩：把 `hookMethod` / `hookAllMethods` 转发到
 * [HookHelper]（libxposed），并把回调适配成 `before` / `after` 语义。
 */
object XposedBridge {

    @JvmStatic
    fun log(message: String) {
        HookHelper.log(message)
    }

    @JvmStatic
    fun log(t: Throwable) {
        HookHelper.log("legacy module throwable", t)
    }

    @JvmStatic
    fun hookMethod(member: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        val executable = member as? Executable
            ?: throw IllegalArgumentException("not a method/constructor: $member")
        val handle = HookHelper.intercept(executable) { chain -> dispatch(executable, chain, callback) }
        return XC_MethodHook.Unhook(handle)
    }

    @JvmStatic
    fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        callback: XC_MethodHook,
    ): Set<XC_MethodHook.Unhook> {
        val result = LinkedHashSet<XC_MethodHook.Unhook>()
        runCatching { clazz.declaredMethods }
            .getOrDefault(emptyArray())
            .filter { it.name == methodName && !it.isSynthetic }
            .forEach { method ->
                runCatching { result.add(hookMethod(method, callback)) }
                    .onFailure { HookHelper.log("hookAllMethods failed: ${clazz.name}#$methodName", it) }
            }
        // 同名方法一个都没找到时，顺着父类再找一层（老 API 不做，但这里补上更稳）
        if (result.isEmpty()) {
            var parent = clazz.superclass
            while (parent != null && result.isEmpty()) {
                runCatching { parent.declaredMethods }
                    .getOrDefault(emptyArray())
                    .filter { it.name == methodName && !it.isSynthetic }
                    .forEach { method ->
                        runCatching { result.add(hookMethod(method, callback)) }
                    }
                parent = parent.superclass
            }
        }
        return result
    }

    /**
     * 老 API 的回调语义：
     * 1. `beforeHookedMethod`；若其中 `setResult` 则跳过原方法；
     * 2. 否则执行原方法（带被改过的参数）；
     * 3. `afterHookedMethod`；期间可以改写结果或异常；
     * 4. 原方法抛出的异常在该阶段仍可被 `setResult` 吞掉，否则继续抛出。
     */
    private fun dispatch(
        exec: Executable,
        chain: XposedInterface.Chain,
        callback: XC_MethodHook,
    ): Any? {
        val param = XC_MethodHook.MethodHookParam(exec, chain.thisObject, chain.args.toTypedArray())
        var thrown: Throwable? = null
        try {
            if (callback is XC_MethodReplacement) {
                param.setResult(callback.replaceHookedMethod(param))
            } else {
                callback.beforeHookedMethod(param)
                if (!param.hasResult) {
                    param.setResult(chain.proceed(param.args))
                }
            }
        } catch (t: Throwable) {
            thrown = t
            param.throwable = t
        }
        runCatching { callback.afterHookedMethod(param) }
            .onFailure { if (thrown == null) thrown = it }
        thrown?.let { throw it }
        return param.getResult()
    }
}
