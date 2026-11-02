package cn.ianzb.hyperrefine.hook.systemui.media

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.view.View
import cn.ianzb.hyperrefine.hook.base.BaseHook
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import cn.ianzb.hyperrefine.hook.xposed.Reflect
import java.util.Collections
import java.util.WeakHashMap

/**
 * 只在锁屏时隐藏哔哩哔哩的媒体控制卡片，解锁后下拉照旧可以控制播放。
 *
 * 目标：`MiuiMediaNotificationControllerImpl` —— 小米把媒体卡片塞进通知栈顶部的那套实现，
 * 字段 `mediaContainerView` 就是实际显示的 `MiuiMediaHeaderView`，`topMediaData` 是当前置顶的媒体。
 *
 * 拦截方式：在这个 controller 每次重排布局（`updateLayout*`）或改可见性（`setVisibility*`）**之后**
 * 再压一次 `GONE`。选 after 而不是 replace，是因为布局逻辑本身还要做动画、尺寸计算，
 * 只改最终结果风险最小；只要不是「B 站 + 锁屏」，就一律不碰，别人家的卡片不受影响。
 *
 * 恢复：用 [suppressed] 记住「这块 view 是我压下去的」，一旦切到非锁屏（或换了媒体来源），
 * 只还原自己压过的那次，避免和 controller 自己的可见性计算打架。
 */
class BiliKeyguardMediaHook : BaseHook() {

    override val tag: String = "BiliKeyguardMedia"
    override val key: String = BiliMediaKeys.KEYGUARD

    /** 被我压成 GONE 的 view（弱引用，视图销毁后自动回收）。 */
    private val suppressed = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    @Volatile
    private var lastKeyguardCheckAt = 0L

    @Volatile
    private var lastKeyguardValue = false

    override fun init() {
        val loader = target.classLoader ?: return
        val clz = Reflect.findClassIfExists(TARGET_CLASS, loader) ?: run {
            HookHelper.log("$tag: $TARGET_CLASS not found")
            return
        }

        // updateLayout*()：布局/状态变化入口；setVisibility*(int)：直接改可见性的入口。
        // 两个都挂，谁先谁后就都能压住。
        val noArg = clz.declaredMethods.filter {
            it.parameterTypes.isEmpty() && it.name.startsWith("updateLayout")
        }
        val oneInt = clz.declaredMethods.filter {
            it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.name.startsWith("setVisibility")
        }
        if (noArg.isEmpty() && oneInt.isEmpty()) {
            HookHelper.log("$tag: no layout entry found in $TARGET_CLASS")
            return
        }

        (noArg + oneInt).forEach { method ->
            HookHelper.hookAfter(method) { param -> apply(param.thisObject) }
        }
        HookHelper.log("$tag: hooked ${noArg.size + oneInt.size} method(s)")
    }

    private fun apply(controller: Any?) {
        controller ?: return
        val view = runCatching {
            Reflect.getObjectField(controller, "mediaContainerView") as? View
        }.getOrNull()

        val isBili = mediaPackageOf(
            runCatching { Reflect.getObjectField(controller, "topMediaData") }.getOrNull()
        ) == BiliMediaKeys.PKG

        if (!isBili) {
            restore(view)
            return
        }

        val enabled = HookPrefs.getBoolean(BiliMediaKeys.KEYGUARD, false)
        if (enabled && isKeyguard(controller)) {
            if (view != null && view.visibility != View.GONE) {
                view.visibility = View.GONE
                suppressed[view] = true
                HookHelper.log("$tag: 锁屏隐藏 B 站媒体卡")
            }
        } else {
            restore(view)
        }
    }

    private fun restore(view: View?) {
        view ?: return
        if (suppressed.remove(view) != null && view.visibility == View.GONE) {
            view.visibility = View.VISIBLE
            HookHelper.log("$tag: 恢复 B 站媒体卡")
        }
    }

    /**
     * 是否处于锁屏。
     *
     * 优先读 controller 自己的 `statusBarState` 字段（KEYGUARD = 1，零开销），
     * 读不到或不认这个值时退回 `KeyguardManager#isKeyguardLocked`（AOD 也算锁屏）。
     * 结果缓存 300ms —— 布局回调可能一帧调好几次。
     */
    private fun isKeyguard(controller: Any): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKeyguardCheckAt < 300L) return lastKeyguardValue

        val state = runCatching { Reflect.getObjectField(controller, "statusBarState") as? Int }.getOrNull()
        val value = if (state != null) {
            state == STATE_KEYGUARD
        } else {
            val context = runCatching {
                Reflect.getObjectField(controller, "context") as? Context
            }.getOrNull()
            (context?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.isKeyguardLocked == true
        }
        lastKeyguardCheckAt = now
        lastKeyguardValue = value
        return value
    }

    internal companion object {
        const val TARGET_CLASS =
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaNotificationControllerImpl"

        /** `com.android.systemui.statusbar.StatusBarState.KEYGUARD`。 */
        const val STATE_KEYGUARD = 1
    }
}
