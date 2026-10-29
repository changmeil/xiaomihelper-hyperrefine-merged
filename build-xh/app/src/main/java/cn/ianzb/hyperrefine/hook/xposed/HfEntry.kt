package cn.ianzb.hyperrefine.hook.xposed

import cn.ianzb.hyperrefine.hook.aod.AodBridgeLoad
import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget
import cn.ianzb.hyperrefine.hook.connect.MiLinkLoad
import cn.ianzb.hyperrefine.hook.embed.EmbeddedModules
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.securitycenter.SecurityCenterLoad
import cn.ianzb.hyperrefine.hook.systemui.SystemUiLoad
import dev.lackluster.mihelper.data.preference.Preferences
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 迁移自 HyperRefine 的第二个 Xposed 入口。
 *
 * 与宿主 `dev.lackluster.mihelper.hook.HookEntry` 并列注册（见 `java_init.list`），
 * 只负责 7 个开关对应的 hook：
 *
 * | 包 | 功能 |
 * |---|---|
 * | `com.android.systemui` | 加快图标加载 / 隐藏省略号 / 缩小设备点击范围 |
 * | `com.miui.securitycenter` | 隐藏进入·退出快充加速提醒 |
 * | `com.milink.service` | 妙播音频低延迟 |
 * | `system`（system_server） | 全屏 AOD 智能显示修复 |
 *
 * 配置与宿主共用 `config` 偏好分组，键名见 [Preferences.HyperRefine]。
 */
class HfEntry : XposedModule() {

    @Volatile
    private var isEnabled: Boolean = true

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HookHelper.init(this)
        HookPrefs.init(getRemotePreferences(Preferences.NAME))
        // 内置第三方模块（微信小程序兼容 / 支付宝极速模式）需要 APK 路径来读 assets 里的 dex。
        EmbeddedModules.attach(
            this,
            runCatching { getModuleApplicationInfo()?.sourceDir }.getOrNull(),
            param.processName,
            param,
        )
        // 跟随宿主的模块总开关：关掉时这 7 个功能也一起停，避免出现「主开关关了还在生效」。
        isEnabled = HookPrefs.getBoolean(HOST_MASTER_SWITCH, true)
        HookHelper.log("HyperRefine bridge loaded in ${param.processName}, enabled=$isEnabled")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // 内置第三方模块与宿主总开关、与「是否第一个包」都无关：
        // 它们有自己的开关，且微信的版本上报在 com.tencent.mm:appbrand* 等子进程里也要装。
        runCatching { EmbeddedModules.onPackageReady(param, this) }
            .onFailure { HookHelper.log("embedded module failed for ${param.packageName}", it) }

        if (!isEnabled || !param.isFirstPackage) return
        // 冷启动时 onModuleLoaded 可能晚于本回调，这里再读一次配置。
        HookPrefs.init(getRemotePreferences(Preferences.NAME))
        val target = PackageTarget.from(param)
        loadsFor(target.packageName).forEach { load ->
            runCatching { load.onPackageReady(target) }
                .onFailure { HookHelper.log("load failed for ${target.packageName}", it) }
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (!isEnabled) return
        HookPrefs.init(getRemotePreferences(Preferences.NAME))
        val target = PackageTarget.fromSystemServer(param)
        runCatching { AodBridgeLoad().onPackageReady(target) }
            .onFailure { HookHelper.log("system-server load failed", it) }
    }

    private companion object {
        /** 宿主 XiaomiHelper 的模块总开关键（`Preferences.Module.MODULE_ENABLED`）。 */
        const val HOST_MASTER_SWITCH = "enable_module"
    }

    private fun loadsFor(pkg: String): List<BaseLoad> = when (pkg) {
        SystemUiLoad.TARGET_PACKAGE -> listOf(SystemUiLoad())
        SecurityCenterLoad.TARGET_PACKAGE -> listOf(SecurityCenterLoad())
        MiLinkLoad.TARGET_PACKAGE -> listOf(MiLinkLoad())
        else -> emptyList()
    }
}
