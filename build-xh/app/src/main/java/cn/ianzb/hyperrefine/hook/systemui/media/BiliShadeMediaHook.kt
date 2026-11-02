package cn.ianzb.hyperrefine.hook.systemui.media

import cn.ianzb.hyperrefine.hook.base.BaseHook
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import cn.ianzb.hyperrefine.hook.xposed.Reflect
import java.util.concurrent.atomic.AtomicLong

/**
 * 通知栏里也不显示哔哩哔哩的媒体控制（锁屏自然也一起没有）。
 *
 * 拦截点在 `MediaDataManager` 的发布回调 `onMediaDataLoaded(key, oldKey, mediaData)`：
 * 所有媒体条目（不管来自通知还是 MediaSession）最终都要经过这里才会被广播给
 * `MediaCarouselController` / 小米那套 controller。在这里按包名丢掉，
 * 等于这条媒体从来没有出现过 —— 下拉和锁屏都不会有卡片，普通通知不受影响。
 *
 * 为什么不在 `loadMediaData`（加载入口）拦：那条是 suspend 函数，替换返回值要和协程
 * 调度打交道；`onMediaDataLoaded` 是普通 void 方法，跳过即可，风险小得多。
 */
class BiliShadeMediaHook : BaseHook() {

    override val tag: String = "BiliShadeMedia"
    override val key: String = BiliMediaKeys.SHADE

    private val lastLogAt = AtomicLong(0L)

    override fun init() {
        val loader = target.classLoader ?: return
        val method = CANDIDATE_CLASSES
            .asSequence()
            .mapNotNull { Reflect.findClassIfExists(it, loader) }
            .mapNotNull { clz ->
                clz.declaredMethods.firstOrNull { m ->
                    m.name == METHOD_LOADED &&
                        m.parameterTypes.size == 3 &&
                        m.parameterTypes[2].name == MEDIA_DATA_CLASS
                }
            }
            .firstOrNull()

        if (method == null) {
            HookHelper.log("$tag: onMediaDataLoaded(MediaData) not found")
            return
        }

        HookHelper.hookBefore(method) { param ->
            if (!HookPrefs.getBoolean(BiliMediaKeys.SHADE, false)) return@hookBefore
            val data = param.args.getOrNull(2)
            if (mediaPackageOf(data) != BiliMediaKeys.PKG) return@hookBefore
            // 跳过原方法：条目不会写进 mediaEntries，也不会通知任何 listener。
            param.setResultValue(null)
            logThrottled()
        }
        HookHelper.log("$tag: hooked ${method.declaringClass.simpleName}#$METHOD_LOADED")
    }

    private fun logThrottled() {
        val now = System.currentTimeMillis()
        val last = lastLogAt.get()
        if (now - last > 10_000L && lastLogAt.compareAndSet(last, now)) {
            HookHelper.log("$tag: 已丢弃 B 站媒体条目（通知栏与锁屏都不显示）")
        }
    }

    internal companion object {
        /** HyperOS 上实际干活的实现类；后面一个是 AOSP 新版的形态，作为兜底。 */
        val CANDIDATE_CLASSES = listOf(
            "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl",
            "com.android.systemui.media.controls.domain.pipeline.MediaDataManagerImpl",
        )

        const val METHOD_LOADED = "onMediaDataLoaded"
        const val MEDIA_DATA_CLASS = "com.android.systemui.media.controls.shared.model.MediaData"
    }
}
