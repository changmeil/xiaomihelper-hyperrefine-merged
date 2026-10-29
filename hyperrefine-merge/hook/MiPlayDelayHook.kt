package cn.ianzb.hyperrefine.hook.connect

import cn.ianzb.hyperrefine.hook.base.BaseHook
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import cn.ianzb.hyperrefine.hook.xposed.Reflect

/**
 * 妙播（MiPlay）音频延迟压缩。
 *
 * 目标进程：`com.milink.service` 中包含 MiPlay 镜像实现的进程。
 *
 * 官方 `MultiMirrorControl#setAudioPlayDelayTime(long, int)` 在建流时按 Wi-Fi 频段写死缓冲：
 * 5GHz 用 800ms、2.4GHz 用 1000ms（第二个参数单位为微秒，即 800000 / 1000000）。音频流本身码率
 * 很低（约 50KB/s），这个余量过大，直接后果是流转到音箱 / 耳机后看视频音画不同步。
 *
 * 本 Hook 在方法执行前把第二个参数改写为 [DELAY_US]（默认 50ms），其余逻辑保持不变。
 *
 * 参考：酷安 @无尽世界的尽头 的 MiPlayFix（github.com/edgeofendlessworld/MiPlayFix_Module）。
 */
class MiPlayDelayHook : BaseHook() {

    override val key: String = ConnectKeys.MIPLAY_DELAY

    override fun init() {
        val loader = target.classLoader ?: return
        val cl = Reflect.findClassIfExists(TARGET_CLASS, loader) ?: run {
            HookHelper.log("$tag: $TARGET_CLASS not found")
            return
        }
        val method = Reflect.findMethodIfExists(
            cl,
            TARGET_METHOD,
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        ) ?: run {
            HookHelper.log("$tag: $TARGET_METHOD not found")
            return
        }
        HookHelper.hookBefore(method) { param ->
            if (!HookPrefs.getBoolean(ConnectKeys.MIPLAY_DELAY, false)) return@hookBefore
            val current = param.args.getOrNull(1) as? Int ?: return@hookBefore
            // 已经比目标值更小时不动，避免和官方后续的自适应逻辑打架。
            if (current in 1..DELAY_US) return@hookBefore
            param.setArg(1, DELAY_US)
        }
    }

    internal companion object {
        const val TARGET_CLASS = "com.xiaomi.miplay.mylibrary.mirror.MultiMirrorControl"
        const val TARGET_METHOD = "setAudioPlayDelayTime"

        /** 50ms。若仍有卡顿可上调到 100_000 ~ 200_000；不建议低于 30ms。 */
        const val DELAY_US = 50_000
    }
}
