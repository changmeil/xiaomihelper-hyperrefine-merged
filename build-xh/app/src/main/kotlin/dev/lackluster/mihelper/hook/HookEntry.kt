package dev.lackluster.mihelper.hook

import dev.lackluster.mihelper.data.Scope
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.scopes.MiuiHome
import dev.lackluster.mihelper.hook.scopes.SystemUI
import dev.lackluster.mihelper.hook.base.BaseHooker
import dev.lackluster.mihelper.hook.base.HookParam
import dev.lackluster.mihelper.hook.utils.RemotePreferences
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.RemotePreferences.observe
import dev.lackluster.mihelper.utils.MLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.ConcurrentHashMap

class HookEntry : XposedModule() {
    private var isModuleEnabled: Boolean = true
    private val injectedPackages = ConcurrentHashMap.newKeySet<String>()
    private lateinit var processName: String
    private var isSystemServer: Boolean = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        isSystemServer = param.isSystemServer
        MLog.init(this)
        RemotePreferences.init(this)
        Preferences.Module.DEBUG.observe {
            MLog.isDebugEnabled = it
        }
        isModuleEnabled = Preferences.Module.MODULE_ENABLED.get()
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        // 精简版：system_server 只保留 HyperRefine 的全屏 AOD 修复（由 HfEntry 挂载）。
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!isModuleEnabled) return
        if (!injectedPackages.add(param.packageName)) return

        val appHooker = getHookerByPackageName(param.packageName) ?: return

        val hookParam = HookParam(
            processName = processName,
            packageName = param.packageName,
            isSystemServer = isSystemServer,
            isFirstPackage = param.isFirstPackage,
            isPackageReady = false,
            appInfo = param.applicationInfo
        )
        attachHooker(appHooker, param.defaultClassLoader, hookParam)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!isModuleEnabled) return
    }

    /**
     * 精简版：只保留两个作用域。
     *
     * - [Scope.SYSTEM_UI]：双排信号图标与状态栏图标顺序；
     * - [Scope.MIUI_HOME]：桌面文件夹（大文件夹图标自适应、文件夹动画）。
     *
     * 其余作用域的 hook 规则不再注册（见各 scope 的 onInit），对应包也已从 `scope.list` 移除，
     * 保证「界面看不到 = 不会注入」。
     */
    private fun getHookerByPackageName(pkg: String): BaseHooker? = when (pkg) {
        Scope.SYSTEM_UI -> SystemUI
        Scope.MIUI_HOME -> MiuiHome
        else -> null
    }

    private fun attachHooker(
        hooker: BaseHooker,
        targetClassLoader: ClassLoader,
        param: HookParam,
    ) {
        hooker.module = this
        hooker.classLoader = targetClassLoader
        hooker.hookParam = param

        hooker.performInit()
        hooker.updateParentState(true)
    }

}