package dev.lackluster.hyperx.ui.preference.core

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface PreferenceActions {
    fun <T : Any> get(key: PreferenceKey<T>): T
    fun <T : Any> update(key: PreferenceKey<T>, value: T)
    val preferenceUpdates: Flow<PreferenceKey<*>>
        get() = emptyFlow()

    /**
     * 整表重载信号（例如模块配置服务刚连上、导入备份、重置设置）。
     *
     * 与 [preferenceUpdates] 的区别：那个只通知「某个键刚被改动」，订阅者按 key 过滤；
     * 这个是「所有键都可能变了，都重读一遍」，用于界面在数据源就绪后校正显示。
     */
    val preferenceReloads: Flow<Unit>
        get() = emptyFlow()
}

val LocalPreferenceActions = compositionLocalOf<PreferenceActions> {
    error("PreferenceActions not provided")
}