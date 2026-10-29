package dev.lackluster.mihelper.app.screen.systemui.icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.SwitchPreference
import dev.lackluster.hyperx.ui.preference.TextPreference
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import dev.lackluster.mihelper.R
import dev.lackluster.mihelper.app.component.RebootActionItem
import dev.lackluster.mihelper.app.widget.preference.DropDownOption
import dev.lackluster.mihelper.app.widget.preference.DropDownPreference
import dev.lackluster.mihelper.data.Route
import dev.lackluster.mihelper.data.Scope
import dev.lackluster.mihelper.data.preference.Preferences

/**
 * 精简版：只保留「图标顺序」这一组。
 *
 * 排序本身由 `IconManager` 实现（读 ICON_POSITION / ICON_POSITION_VAL，并 hook
 * `StatusBarIconList` 的构造器改写槽位数组），所以这里不需要额外的 hooker。
 * 换序结果要重启系统界面才生效，右上角留了重启按钮。
 */
private val iconPositionOptions = listOf(
    DropDownOption(0, R.string.icon_tuner_general_order_default),
    DropDownOption(1, R.string.icon_tuner_general_order_swap),
    DropDownOption(2, R.string.icon_tuner_general_order_custom),
)

@Composable
fun IconTunerPage() {
    val navigator = LocalNavigator.current

    HyperXPage(
        title = stringResource(R.string.page_status_bar_icon_order),
        actions = {
            RebootActionItem(
                appName = stringResource(R.string.scope_systemui),
                appPkg = arrayOf(Scope.SYSTEM_UI),
            )
        }
    ) {
        itemPreferenceGroup(
            key = "ICON_TUNER_ORDER",
            position = ItemPosition.Single
        ) {
            val iconPosition = rememberPreferenceState(Preferences.SystemUI.StatusBar.IconTuner.ICON_POSITION)
            DropDownPreference(
                title = stringResource(R.string.icon_tuner_general_order),
                summary = stringResource(R.string.icon_tuner_general_order_tips),
                options = iconPositionOptions,
                value = iconPosition.value,
                onValueChange = { iconPosition.value = it }
            )
            AnimatedVisibility(iconPosition.value == 2) {
                TextPreference(
                    title = stringResource(R.string.icon_tuner_general_order_custom_entry),
                    onClick = { navigator.push(Route.StatusBarIconPosition) }
                )
            }
            SwitchPreference(
                key = Preferences.SystemUI.StatusBar.IconTuner.ICON_POSITION_REORDER,
                title = stringResource(R.string.icon_tuner_general_auto_reorder),
                summary = stringResource(R.string.icon_tuner_general_auto_reorder_tips)
            )
        }
    }
}
