package cn.ianzb.hyperrefine.hook.prefs

import android.content.SharedPreferences
import android.os.SystemClock

/**
 * 迁移到 XiaomiHelper 后的配置读取（只读）。
 *
 * 与上游 HyperRefine 的差别：
 * - 偏好分组改为 XiaomiHelper 的 `Preferences.NAME`（`config`），两边共用同一份文件；
 * - 去掉了上游的 `prefs_key_` 前缀，键名与 `Preferences.HyperRefine.*` 完全一致；
 * - 增加了节流的 [refresh]：libxposed 拿到的 remote preferences 底层是带内存缓存的实现，
 *   不主动 reload 的话，进程启动后读到的就是那一刻的快照——用户改开关要等目标进程重启才生效。
 *   这里每次读取前最多每 [RELOAD_INTERVAL_MS] 毫秒尝试 reload 一次，开关改完立即生效。
 */
@Suppress("unused")
object HookPrefs {

    private const val RELOAD_INTERVAL_MS = 500L

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var reloadMethod: java.lang.reflect.Method? = null

    @Volatile
    private var reloadProbed = false

    @Volatile
    private var lastReloadAt = 0L

    fun init(remote: SharedPreferences) {
        prefs = remote
        reloadMethod = null
        reloadProbed = false
        lastReloadAt = 0L
    }

    /**
     * 让底层配置重新从磁盘加载。实现类不一定有 `reload()`，没有就静默跳过
     * （此时说明该实现本身就是实时的，或只能等进程重启）。
     */
    private fun refresh() {
        val target = prefs ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastReloadAt < RELOAD_INTERVAL_MS) return
        lastReloadAt = now

        if (!reloadProbed) {
            reloadProbed = true
            reloadMethod = runCatching {
                target.javaClass.getMethod("reload").also { it.isAccessible = true }
            }.getOrNull()
        }
        runCatching { reloadMethod?.invoke(target) }
    }

    private inline fun <T> safe(defaultValue: T, read: (SharedPreferences) -> T): T {
        refresh()
        val target = prefs ?: return defaultValue
        return try {
            read(target)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        safe(defaultValue) { it.getBoolean(key, defaultValue) }

    fun getString(key: String, defaultValue: String? = null): String? =
        safe(defaultValue) { it.getString(key, defaultValue) }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        safe(defaultValue) { it.getInt(key, defaultValue) }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        safe(defaultValue) { it.getLong(key, defaultValue) }

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        safe(defaultValue) { it.getFloat(key, defaultValue) }

    fun getAll(): Map<String, *> {
        refresh()
        return prefs?.all ?: emptyMap<String, Any>()
    }
}
