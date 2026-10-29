package dev.lackluster.mihelper.hook.rules.systemui.notif

import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.d
import dev.lackluster.mihelper.hook.utils.e
import java.lang.reflect.Method

/**
 * 移除焦点通知白名单。
 *
 * 焦点通知（超级岛 / 锁屏焦点卡片）默认只对系统白名单内的应用开放，判定入口在
 * `NotificationSettingsManager` 上的两个方法：
 *
 * - `canShowFocusState(pkg, channelId)`：某条通知能否以焦点通知样式展示；
 * - `canShowFocusStateApp(pkg, channelId)`：某个应用是否具备焦点通知能力。
 *
 * 打开开关后无条件放行（按方法真实返回类型给 1 或 true），任何应用都能用焦点通知样式，
 * 超级岛类模块拦截任意应用通知的前提也就成立了。
 *
 * 属于 [StaticHooker]：开关值在系统界面进程启动时读取，改完需要重启系统界面。
 */
object FocusNotifWhitelist : StaticHooker() {

    private const val MANAGER = "com.miui.systemui.notification.NotificationSettingsManager"

    override fun onInit() {
        updateSelfState(Preferences.SystemUI.NotifCenter.DISABLE_FOCUS_WHITELIST.get())
    }

    override fun onHook() {
        val clazz = MANAGER.toClassOrNull()
        if (clazz == null) {
            e { "$MANAGER not found, focus notification whitelist untouched" }
            return
        }

        val state = clazz.resolve().firstMethodOrNull { name = "canShowFocusState" }?.self
        val stateApp = clazz.resolve().firstMethodOrNull { name = "canShowFocusStateApp" }?.self

        var hooked = 0
        state?.let {
            forceAllow(it)
            hooked++
        }
        stateApp?.let {
            forceAllow(it)
            hooked++
        }

        if (hooked == 0) {
            e { "canShowFocusState / canShowFocusStateApp not found in $MANAGER" }
        } else {
            d { "focus whitelist unlocked, hooked=$hooked" }
        }
    }

    /**
     * 无条件放行。
     *
     * 这两个方法在不同版本上返回 `int`（1 表示允许）或 `boolean`，按真实返回类型给值，
     * 免得出现 `ClassCastException` 把系统界面搞崩。
     */
    private fun forceAllow(method: Method) {
        val forced: Any = if (method.returnType == Boolean::class.javaPrimitiveType) true else 1
        method.hook { result(forced) }
    }
}
