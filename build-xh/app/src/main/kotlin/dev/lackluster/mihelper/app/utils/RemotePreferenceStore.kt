package dev.lackluster.mihelper.app.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.mihelper.app.manager.XposedServiceManager
import dev.lackluster.mihelper.data.preference.Preferences
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 模块配置的读写入口（走 libxposed 的 remote preferences）。
 *
 * 这里有几个坑必须处理，否则会出现「开关自己关回去」：
 *
 * 1. **服务未绑定时不能当默认值处理**。`XposedServiceManager.currentService` 在 App 刚启动时是
 *    `null`，此时 `getRemotePreferences` 也拿不到对象。若直接返回 `key.default`，界面会把所有
 *    开关画成「关」；此时用户一点，「看起来是关的」就变成了写入 `false`——这就是开关莫名关闭的由来。
 *    现在改为：读不到时回退到内存里最后一次已知的值（[lastKnown]），写入则进入 [pendingWrites] 队列，
 *    等 service 绑定后再落盘。
 * 2. **每次 get/put 都新建 SharedPreferences 实例**（原来的 `get()` 属性）在 LSPosed 上会拿到
 *    各自独立的快照，连续快速切换多个开关时后写的会覆盖先写的。现在按 service 缓存同一个实例。
 * 3. **必须用 commit 同步落盘**。`apply()` 是异步的，切完开关马上划掉后台就可能丢。
 */
class RemotePreferenceStore(
    private val xposedManager: XposedServiceManager
) {
    private val lock = Any()

    private var cachedService: XposedService? = null
    private var cachedPrefs: SharedPreferences? = null

    /** 最后一次读到 / 写入的值，用于 service 暂不可用时兜底。 */
    private val lastKnown = HashMap<String, Any>()

    /** service 未绑定时暂存的待写入项。 */
    private val pendingWrites = LinkedHashMap<String, Any>()

    private val _globalReloadEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val globalReloadEvent = _globalReloadEvent.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            xposedManager.serviceFlow.collect { service ->
                if (service != null) {
                    flushPendingWrites()
                    // 绑定成功后再通知界面重读一遍：界面很可能是在 service 就绪之前就画出来的。
                    _globalReloadEvent.emit(Unit)
                }
            }
        }
    }

    private fun prefs(): SharedPreferences? {
        val service = xposedManager.currentService
        synchronized(lock) {
            if (service !== cachedService) {
                cachedService = service
                cachedPrefs = service?.getRemotePreferences(Preferences.NAME)
            }
            return cachedPrefs
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: PreferenceKey<T>): T {
        val prefs = prefs()

        if (prefs != null) {
            val value: Any? = when (key.default) {
                is Boolean -> prefs.getBoolean(key.name, key.default as Boolean)
                is Int -> prefs.getInt(key.name, key.default as Int)
                is Long -> prefs.getLong(key.name, key.default as Long)
                is Float -> prefs.getFloat(key.name, key.default as Float)
                is String -> prefs.getString(key.name, key.default as String)
                is Set<*> -> {
                    val defSet = (key.default as? Set<String>)?.toMutableSet() ?: mutableSetOf()
                    prefs.getStringSet(key.name, defSet)
                }
                else -> null
            }
            if (value != null) {
                synchronized(lock) { lastKnown[key.name] = value }
                return value as T
            }
        }

        // service 还没就绪：用上次已知值，其次待写入值，最后才是默认值。
        synchronized(lock) {
            (lastKnown[key.name] ?: pendingWrites[key.name])?.let { return it as T }
        }
        return key.default
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> put(key: PreferenceKey<T>, value: T) {
        synchronized(lock) { lastKnown[key.name] = value }

        val prefs = prefs()
        if (prefs == null) {
            // 服务没连上就先把值排队，绑定后统一落盘，避免静默丢写。
            synchronized(lock) { pendingWrites[key.name] = value }
            return
        }
        prefs.edit(commit = true) {
            putAny(key.name, value)
        }
    }

    fun setAll(map: Map<String, Any>) {
        val prefs = prefs()
        if (prefs == null) {
            synchronized(lock) {
                map.forEach { (k, v) -> pendingWrites[k] = v }
            }
            return
        }
        prefs.edit(commit = true) {
            map.forEach { (key, value) -> putAny(key, value) }
        }
        synchronized(lock) {
            map.forEach { (k, v) -> lastKnown[k] = v }
        }
    }

    fun getAll(): Map<String, *>? = prefs()?.all ?: synchronized(lock) { HashMap(lastKnown) }

    fun clearAll() {
        synchronized(lock) {
            pendingWrites.clear()
            lastKnown.clear()
        }
        prefs()?.edit(commit = true) {
            clear()
        }
    }

    private fun flushPendingWrites() {
        val prefs = prefs() ?: return
        val snapshot = synchronized(lock) {
            if (pendingWrites.isEmpty()) return
            HashMap(pendingWrites).also { pendingWrites.clear() }
        }
        prefs.edit(commit = true) {
            snapshot.forEach { (key, value) -> putAny(key, value) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.putAny(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
