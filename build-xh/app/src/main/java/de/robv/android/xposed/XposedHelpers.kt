package de.robv.android.xposed

import cn.ianzb.hyperrefine.hook.xposed.Reflect
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 老 API 的 `XposedHelpers` 兼容桩。
 *
 * 只覆盖第三方模块实际用到的查找/挂载方法；找不到时抛出与老 API 同名的异常类型
 * （`ClassNotFoundError` 之类没有还原，直接用 `ClassNotFoundException` 亦可被模块的
 * `try/catch(Throwable)` 捕获）。
 */
object XposedHelpers {

    @JvmStatic
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> =
        Reflect.findClass(className, classLoader)

    @JvmStatic
    fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = Reflect.findMethod(clazz, methodName, *parameterTypes)

    @JvmStatic
    fun findMethodExactIfExists(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? = Reflect.findMethodIfExists(clazz, methodName, *parameterTypes)

    /**
     * 老签名：`findAndHookMethod(Class, String, Object... parameterTypesAndCallback)`，
     * 最后一个参数是 [XC_MethodHook]，前面若干是参数类型。
     */
    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): XC_MethodHook.Unhook {
        val callback = parameterTypesAndCallback.lastOrNull() as? XC_MethodHook
            ?: throw IllegalArgumentException("last argument must be XC_MethodHook: ${clazz.name}#$methodName")
        val types = parameterTypesAndCallback.dropLast(1)
            .map { it as? Class<*> ?: throw IllegalArgumentException("parameter type must be Class: $it") }
            .toTypedArray()
        return XposedBridge.hookMethod(Reflect.findMethod(clazz, methodName, *types), callback)
    }

    @JvmStatic
    fun findAndHookConstructor(
        clazz: Class<*>,
        vararg parameterTypesAndCallback: Any?,
    ): XC_MethodHook.Unhook {
        val callback = parameterTypesAndCallback.lastOrNull() as? XC_MethodHook
            ?: throw IllegalArgumentException("last argument must be XC_MethodHook: ${clazz.name}")
        val types = parameterTypesAndCallback.dropLast(1)
            .map { it as? Class<*> ?: throw IllegalArgumentException("parameter type must be Class: $it") }
            .toTypedArray()
        val constructor: Constructor<*> = clazz.getDeclaredConstructor(*types).also { it.isAccessible = true }
        return XposedBridge.hookMethod(constructor, callback)
    }

    @JvmStatic
    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor = clazz.declaredConstructors
            .filter { it.parameterCount == args.size }
            .firstOrNull { candidate -> paramsMatch(candidate.parameterTypes, args) }
            ?: throw NoSuchMethodException("${clazz.name}(${args.joinToString { it?.javaClass?.name ?: "null" }})")
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    @JvmStatic
    fun getParameterTypes(vararg args: Any?): Array<Class<*>> =
        args.mapNotNull { it as? Class<*> }.toTypedArray()

    private fun paramsMatch(types: Array<Class<*>>, args: Array<out Any?>): Boolean =
        types.indices.all { i ->
            val arg = args[i]
            arg == null || types[i].isInstance(arg)
        }
}
