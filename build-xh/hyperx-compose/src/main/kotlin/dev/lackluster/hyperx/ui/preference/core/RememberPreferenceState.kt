package dev.lackluster.hyperx.ui.preference.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.filter

@Composable
fun <T : Any> rememberPreferenceState(key: PreferenceKey<T>): MutableState<T> {
    val actions = LocalPreferenceActions.current

    val state = remember(key) {
        mutableStateOf(actions.get(key))
    }

    LaunchedEffect(key, actions) {
        actions.preferenceUpdates
            .filter { it == key }
            .collect {
                state.value = actions.get(key)
            }
    }

    // 数据源整表重载（例如模块配置服务刚连上）时，把显示值校正回真实值，
    // 否则界面会一直停留在「service 未就绪时读到的默认值」上。
    LaunchedEffect(key, actions) {
        actions.preferenceReloads.collect {
            state.value = actions.get(key)
        }
    }

    return remember(key) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    actions.update(key, newValue)
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}