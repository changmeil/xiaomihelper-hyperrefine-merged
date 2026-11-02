package cn.ianzb.hyperrefine.hook.systemui.media

import cn.ianzb.hyperrefine.hook.xposed.Reflect

/**
 * 从 `com.android.systemui.media.controls.shared.model.MediaData` 里取来源包名。
 *
 * 该类型是 Kotlin data class，属性是「私有字段 + getter」，不同版本里可能两种形态都在，
 * 所以先走 `getPackageName()`，失败再读字段。
 */
internal fun mediaPackageOf(mediaData: Any?): String? {
    mediaData ?: return null
    runCatching {
        mediaData.javaClass.getMethod("getPackageName").invoke(mediaData) as? String
    }.getOrNull()?.let { return it }
    return runCatching {
        Reflect.getObjectField(mediaData, "packageName") as? String
    }.getOrNull()
}
