package de.robv.android.xposed

/**
 * 老 API 的「完全替换」回调基类。
 *
 * 第三方模块通常只用 [returnConstant]；被替换的方法不会执行。
 */
abstract class XC_MethodReplacement : XC_MethodHook() {

    /**
     * 返回替代结果。
     *
     * 老 API 里这里是 `protected`，但兼容桩必须让 [XposedBridge] 能调用它，
     * 因此声明为 `open`（public）。第三方 dex 里的 override 仍是 protected，
     * JVM 不校验 override 的访问修饰符，运行期没问题。
     */
    open fun replaceHookedMethod(param: MethodHookParam): Any? = null

    companion object {
        @JvmStatic
        fun returnConstant(value: Any?): XC_MethodReplacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = value
        }
    }
}
