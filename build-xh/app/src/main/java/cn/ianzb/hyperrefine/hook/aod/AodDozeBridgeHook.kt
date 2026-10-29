package cn.ianzb.hyperrefine.hook.aod

import android.os.IBinder
import android.os.SystemClock
import cn.ianzb.hyperrefine.hook.base.BaseHook
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import cn.ianzb.hyperrefine.hook.xposed.Reflect
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 全屏 AOD 智能显示恢复修复（目标 `system_server`）。
 *
 * 移植自 [hyperos-full-aod-bridge](https://github.com/silverpoetry/hyperos-full-aod-bridge)
 * （MIT，作者 silverpoetry），改动仅限接入 HyperRefine 的 Hook 框架与开关。
 *
 * 问题：机型特性 XML 里补上 `support_aod_fullscreen` 强开全屏 AOD 后，第一次锁屏正常，
 * 智能显示因遮挡 / 暗光 / 无注视关闭屏幕后，条件恢复时 AOD 一直黑屏。传感器、注视判断、
 * AOD 内容合成全部正常，系统也算出了新亮度，卡在最后一步「写亮度到物理面板」：
 *
 * - 用户态显示库在 OFF→DOZE 时序里以「power state pending / aod layer 0」拒绝；
 * - 即使放行，N2 面板已进 LP1，内核 `is_backlight_set_skip()` 再次拒绝普通背光。
 *
 * 第一次锁屏走的是另一条时序：面板还在 ON 时先写连续亮度，再切 DOZE/LP1，亮度得以保留。
 * 本 Hook 在 OFF→DOZE 恢复边沿复现这个顺序：
 *
 * ```
 * OFF → DOZE → 临时 NORMAL → 写入最新亮度 → 等待 16ms → DOZE
 * ```
 *
 * 四个 Hook 点：
 * 1. `DozeBrightnessStrategyImpl#updateAodMode` → 缓存全屏 AOD 状态；
 * 2. `DozeAutoBrightnessController#updateAutoBrightness` → 缓存系统算出的连续亮度；
 * 3. `LocalDisplayAdapter$LocalDisplayDevice$N#setDisplayState` → 记录真实 OFF→DOZE 边沿；
 * 4. 同一对象 `setDisplayBrightness` → 在边沿上执行一次修正。
 *
 * 纯事件驱动，事件之间完全空闲；普通 AOD 与正常亮屏不受影响。
 */
class AodDozeBridgeHook : BaseHook() {

    override val key: String = AodBridgeKeys.FULL_AOD_BRIDGE

    @Volatile
    private var fullAod: Boolean = false

    @Volatile
    private var latestBrightness: Float = Float.NaN

    @Volatile
    private var pendingShowEdge: Long = NO_EDGE

    private lateinit var screenAutoBrightness: Field
    private lateinit var physicalDisplayId: Field
    private lateinit var oldState: Field
    private lateinit var targetState: Field
    private lateinit var displayToken: Field
    private lateinit var setDisplayPowerMode: Method

    override fun init() {
        val loader = target.classLoader ?: return
        runCatching { hookAodMode(loader) }
            .onFailure { HookHelper.log("$tag: updateAodMode failed", it) }
        runCatching { hookAutoBrightness(loader) }
            .onFailure { HookHelper.log("$tag: updateAutoBrightness failed", it) }
        runCatching { hookDisplayRequest(loader) }
            .onFailure { HookHelper.log("$tag: LocalDisplayAdapter failed", it) }
    }

    /** 缓存全屏 AOD 开关状态；切回普通 AOD 时清掉待处理边沿。 */
    private fun hookAodMode(loader: ClassLoader) {
        val strategy = Reflect.findClassIfExists(CLASS_DOZE_STRATEGY, loader) ?: return
        val isFullAod = Reflect.findField(strategy, "mIsFullAod")
        val method = Reflect.findMethodIfExists(strategy, "updateAodMode", Int::class.javaPrimitiveType!!)
            ?: return
        HookHelper.intercept(method, mode = PROTECTIVE) { chain ->
            val result = chain.proceed()
            fullAod = runCatching { isFullAod.getBoolean(chain.thisObject) }.getOrDefault(false)
            if (!fullAod) pendingShowEdge = NO_EDGE
            result
        }
    }

    /** 缓存小米原自动亮度控制器算出的最终浮点亮度。 */
    private fun hookAutoBrightness(loader: ClassLoader) {
        val controller = Reflect.findClassIfExists(CLASS_DOZE_AUTO_BRIGHTNESS, loader) ?: return
        screenAutoBrightness = Reflect.findField(controller, "mScreenAutoBrightness")
        val method = Reflect.findMethodIfExists(controller, "updateAutoBrightness") ?: return
        HookHelper.intercept(method, mode = PROTECTIVE) { chain ->
            val result = chain.proceed()
            latestBrightness = runCatching { screenAutoBrightness.getFloat(chain.thisObject) }
                .getOrDefault(Float.NaN)
            result
        }
    }

    /** 记录 OFF→DOZE 恢复边沿，并在该边沿的亮度提交上执行一次修正。 */
    private fun hookDisplayRequest(loader: ClassLoader) {
        val request = findDisplayRequestClass(loader) ?: run {
            HookHelper.log("$tag: LocalDisplayAdapter display request not found")
            return
        }
        physicalDisplayId = Reflect.findField(request, "val\$physicalDisplayId")
        oldState = Reflect.findField(request, "val\$oldState")
        targetState = Reflect.findField(request, "val\$state")
        displayToken = Reflect.findField(request, "val\$token")

        val surfaceControl = Reflect.findClassIfExists("android.view.SurfaceControl", loader) ?: return
        setDisplayPowerMode = surfaceControl.getDeclaredMethod(
            "setDisplayPowerMode", IBinder::class.java, Int::class.javaPrimitiveType!!,
        ).apply { isAccessible = true }

        val setState = Reflect.findMethodIfExists(request, "setDisplayState", Int::class.javaPrimitiveType!!)
            ?: return
        HookHelper.intercept(setState, mode = PROTECTIVE) { chain ->
            val result = chain.proceed()
            val state = chain.args.getOrNull(0) as? Int
            if (state != null) {
                runCatching { recordState(chain.thisObject, state) }
                    .onFailure { HookHelper.log("$tag: recordState failed", it) }
            }
            result
        }

        val setBrightness = Reflect.findMethodIfExists(
            request, "setDisplayBrightness",
            Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
        ) ?: return
        HookHelper.intercept(setBrightness, mode = PROTECTIVE) { chain ->
            runCatching { writeBrightnessAtShowEdge(chain) }
                .getOrElse {
                    HookHelper.log("$tag: writeBrightnessAtShowEdge failed", it)
                    chain.proceed()
                }
        }
    }

    private fun recordState(request: Any?, state: Int) {
        if (request == null) return
        if (state == STATE_OFF || state == STATE_ON) {
            pendingShowEdge = NO_EDGE
            if (state == STATE_OFF) latestBrightness = Float.NaN
            return
        }
        if (fullAod && oldState.getInt(request) == STATE_OFF && isDoze(state)) {
            pendingShowEdge = physicalDisplayId.getLong(request)
        }
    }

    private fun writeBrightnessAtShowEdge(chain: Chain): Any? {
        val request = chain.thisObject ?: return chain.proceed()
        val brightness = latestBrightness
        if (!fullAod
            || pendingShowEdge != physicalDisplayId.getLong(request)
            || !isDoze(targetState.getInt(request))
            || !brightness.isFinite()
            || brightness <= 0f
        ) {
            return chain.proceed()
        }

        val token = displayToken.get(request) as? IBinder ?: return chain.proceed()
        setPowerMode(token, POWER_NORMAL)
        pendingShowEdge = NO_EDGE
        try {
            val result = chain.proceed(arrayOf<Any?>(brightness, brightness))
            // 给 SurfaceControl 的异步事务留一个提交窗口，保证亮度先于 DOZE 生效。
            SystemClock.sleep(SHOW_EDGE_COMMIT_WINDOW_MS)
            return result
        } finally {
            setPowerMode(token, POWER_DOZE)
        }
    }

    private fun setPowerMode(token: IBinder, mode: Int) {
        runCatching { setDisplayPowerMode.invoke(null, token, mode) }
            .onFailure { HookHelper.log("$tag: setDisplayPowerMode($mode) failed", it) }
    }

    /**
     * 定位 `LocalDisplayAdapter` 内部的显示请求匿名类。
     *
     * 匿名类编号会随 framework 构建变化（`$1`、`$3`…），所以按「同时具备这两个方法与四个
     * 捕获字段」来识别，而不是写死编号。
     */
    private fun findDisplayRequestClass(loader: ClassLoader): Class<*>? {
        for (index in 1..MAX_ANONYMOUS_INDEX) {
            val candidate = Reflect.findClassIfExists("$CLASS_DISPLAY_REQUEST_PREFIX$index", loader) ?: continue
            val ok = runCatching {
                candidate.getDeclaredMethod("setDisplayState", Int::class.javaPrimitiveType!!)
                candidate.getDeclaredMethod(
                    "setDisplayBrightness",
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                )
                candidate.getDeclaredField("val\$physicalDisplayId")
                candidate.getDeclaredField("val\$oldState")
                candidate.getDeclaredField("val\$state")
                candidate.getDeclaredField("val\$token")
            }.isSuccess
            if (ok) return candidate
        }
        return null
    }

    private fun isDoze(state: Int): Boolean = state == STATE_DOZE || state == STATE_DOZE_SUSPEND

    private companion object {
        const val CLASS_DOZE_STRATEGY =
            "com.android.server.display.brightness.strategy.DozeBrightnessStrategyImpl"
        const val CLASS_DOZE_AUTO_BRIGHTNESS = "com.android.server.display.DozeAutoBrightnessController"
        const val CLASS_DISPLAY_REQUEST_PREFIX = "com.android.server.display.LocalDisplayAdapter\$LocalDisplayDevice\$"

        const val MAX_ANONYMOUS_INDEX = 32
        const val SHOW_EDGE_COMMIT_WINDOW_MS = 16L

        const val NO_EDGE = Long.MIN_VALUE

        const val STATE_OFF = 1
        const val STATE_ON = 2
        const val STATE_DOZE = 3
        const val STATE_DOZE_SUSPEND = 4

        const val POWER_DOZE = 1
        const val POWER_NORMAL = 2

        val PROTECTIVE: ExceptionMode = ExceptionMode.PROTECTIVE
    }
}
