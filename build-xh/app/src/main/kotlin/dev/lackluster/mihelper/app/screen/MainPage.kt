package dev.lackluster.mihelper.app.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.navigation.HyperXRoute
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.dialog.AlertDialog
import dev.lackluster.hyperx.ui.dialog.AlertDialogMode
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.SwitchPreference
import dev.lackluster.hyperx.ui.preference.TextPreference
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import dev.lackluster.mihelper.R
import dev.lackluster.mihelper.data.Constants
import dev.lackluster.mihelper.data.Route
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.app.state.AppEnvViewModel
import dev.lackluster.mihelper.app.utils.SystemCommander
import dev.lackluster.mihelper.app.utils.showToast
import dev.lackluster.mihelper.utils.Device
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

private sealed interface MainPageAction {
    data class NavigateTo(val route: Route) : MainPageAction
    object OpenMenu : MainPageAction
    object OpenLSPosed : MainPageAction
    object RefreshEnvironment : MainPageAction
    object IgnoreSystemVersionWarning : MainPageAction
}

@Composable
fun MainPage(
    appEnvVm: AppEnvViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val envState by appEnvVm.envState.collectAsState()

    val showDisabledDialog = remember { mutableStateOf(false) }
    val showInactiveDialog = remember { mutableStateOf(false) }
    val showRootDialog = remember { mutableStateOf(false) }
    val showSystemVersionDialog = remember { mutableStateOf(false) }

    LaunchedEffect(envState) {
        if (envState.canWork) {
            showDisabledDialog.value = false
            showInactiveDialog.value = false
            showRootDialog.value = false
            showSystemVersionDialog.value = !envState.isSystemVersionRequirementSatisfied
        } else if (envState.isModuleActivated) {
            showInactiveDialog.value = false
            showSystemVersionDialog.value = false
            if (!envState.isModuleEnabled) {
                showDisabledDialog.value = true
                showRootDialog.value = false
            } else if (!envState.isRootRequirementSatisfied) {
                showRootDialog.value = true
                showDisabledDialog.value = false
            }
        } else {
            delay(500.milliseconds)
            showInactiveDialog.value = true
            showDisabledDialog.value = false
            showRootDialog.value = false
            showSystemVersionDialog.value = false
        }
    }

    val onAction: (MainPageAction) -> Unit = { action ->
        when (action) {
            is MainPageAction.NavigateTo -> {
                navigator.popUntil { it is HyperXRoute.Main }
                navigator.push(action.route)
            }
            is MainPageAction.OpenMenu -> {
                navigator.popUntil { it is HyperXRoute.Main }
                navigator.push(Route.Menu)
            }
            is MainPageAction.OpenLSPosed -> {
                scope.launch {
                    val result = SystemCommander.execAsync(
                        command = Constants.CMD_LSPOSED,
                        useRoot = true,
                        silent = true
                    )
                    if (!result.isSuccess) {
                        context.showToast(result.err, true)
                    }
                }
            }
            is MainPageAction.RefreshEnvironment -> {
                appEnvVm.refreshEnvState()
            }
            is MainPageAction.IgnoreSystemVersionWarning -> {
                appEnvVm.ignoreSystemVersionWarning()
            }
        }
    }

    MainPageContent(
        onAction = onAction
    )

    AlertDialog(
        visible = showDisabledDialog.value,
        onDismissRequest = { showDisabledDialog.value = false},
        title = stringResource(R.string.dialog_warning),
        message = stringResource(R.string.main_module_disabled_tips),
        cancelable = false,
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_ignore),
        positiveText = stringResource(R.string.button_enable)
    ) {
        showDisabledDialog.value = false
        onAction(MainPageAction.NavigateTo(Route.ModuleSettings))
    }
    AlertDialog(
        visible = showInactiveDialog.value,
        onDismissRequest = { showInactiveDialog.value = false },
        title = stringResource(R.string.dialog_error),
        message = stringResource(R.string.main_module_inactive_tips),
        cancelable = false,
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_ignore),
        positiveText = stringResource(R.string.main_module_inactive_dialog_lsposed)
    ) {
        onAction(MainPageAction.OpenLSPosed)
    }
    AlertDialog(
        visible = showRootDialog.value,
        onDismissRequest = { showRootDialog.value = false },
        title = stringResource(R.string.dialog_warning),
        message = stringResource(R.string.main_app_root_tips),
        cancelable = false,
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_ignore),
        positiveText = stringResource(R.string.button_enable)
    ) {
        onAction(MainPageAction.RefreshEnvironment)
    }
    AlertDialog(
        visible = showSystemVersionDialog.value,
        onDismissRequest = { showSystemVersionDialog.value = false },
        title = stringResource(R.string.dialog_warning),
        message = stringResource(R.string.main_system_version_tips),
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_do_not_remind),
        positiveText = stringResource(R.string.button_ignore),
        onNegativeButton = {
            showSystemVersionDialog.value = false
            onAction(MainPageAction.IgnoreSystemVersionWarning)
        }
    )
}

@Composable
private fun MainPageContent(
    onAction: (MainPageAction) -> Unit
) {
    val showTopPopup = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    val contextMenuItems = listOf(
        stringResource(R.string.ui_title_menu_reboot),
        stringResource(R.string.menu_shortcut_lsposed)
    )

    HyperXPage(
        title = stringResource(R.string.page_main),
        navigationIcon = {},
        actions = {
            OverlayListPopup(
                show = showTopPopup.value,
                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                alignment = PopupPositionProvider.Align.TopEnd,
                onDismissRequest = { showTopPopup.value = false }
            ) {
                ListPopupColumn {
                    contextMenuItems.forEachIndexed { index, string ->
                        DropdownImpl(
                            text = string,
                            optionSize = contextMenuItems.size,
                            isSelected = false,
                            onSelectedIndexChange = {
                                when (it) {
                                    0 -> onAction(MainPageAction.OpenMenu)
                                    1 -> onAction(MainPageAction.OpenLSPosed)
                                }
                                showTopPopup.value = false
                            },
                            index = index
                        )
                    }
                }
            }
            IconButton(
                modifier = Modifier
                    .padding(end = 21.dp)
                    .size(40.dp),
                onClick = {
                    showTopPopup.value = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                holdDownState = showTopPopup.value
            ) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    imageVector = MiuixIcons.More,
                    contentDescription = "Menu",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    ) {
        itemPreferenceGroup(
            key = "MAIN_MODULE",
            position = ItemPosition.First
        ) {
            TextPreference(
                icon = ImageIcon(R.drawable.ic_header_hyper_helper_gray),
                title = stringResource(R.string.page_module),
            ) { onAction(MainPageAction.NavigateTo(Route.ModuleSettings)) }
        }
        itemPreferenceGroup(
            key = "MAIN_STATUSBAR",
        ) {
            TextPreference(
                icon = ImageIcon(R.drawable.ic_stat_sys_stacked_icon),
                title = "双排信号图标",
                summary = "图标样式、双卡堆叠、字体与网络类型文字",
                onClick = { onAction(MainPageAction.NavigateTo(Route.IconDetail)) },
            )
            TextPreference(
                title = stringResource(R.string.icon_tuner_general_order),
                summary = stringResource(R.string.icon_tuner_general_order_tips),
                onClick = { onAction(MainPageAction.NavigateTo(Route.IconTuner)) },
            )
        }
        itemPreferenceGroup(
            key = "MAIN_LAUNCHER",
            title = "桌面文件夹",
        ) {
            SwitchPreference(
                key = Preferences.MiuiHome.FOLDER_ADAPT_SIZE,
                title = stringResource(R.string.home_folder_adapt_icon_size),
                summary = stringResource(R.string.home_folder_adapt_icon_size_tips),
            )
            SwitchPreference(
                key = Preferences.MiuiHome.DISABLE_FOLDER_ZOOM_ANIM,
                title = stringResource(R.string.home_anim_folder_zoom),
                summary = "文件夹按下时不再整体缩放",
            )
            SwitchPreference(
                key = Preferences.MiuiHome.DISABLE_FOLDER_DARKEN_ANIM,
                title = stringResource(R.string.home_anim_folder_icon_darken),
                summary = "按下文件夹内图标时不再加一层黑色遮罩",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_DEVICE_CENTER",
            title = "融合设备中心",
        ) {
            SwitchPreference(
                key = Preferences.HyperRefine.DEVICE_CENTER_FAST_LOAD,
                title = "加快图标加载",
                summary = "打开控制中心立即显示已连接设备图标，不再等待扫描延迟",
            )
            SwitchPreference(
                key = Preferences.HyperRefine.DEVICE_CENTER_HIDE_MORE,
                title = "隐藏省略号",
                summary = "隐藏末尾的「…」，设备占满一行时不再换行",
            )
            SwitchPreference(
                key = Preferences.HyperRefine.DEVICE_CENTER_HIT_AREA,
                title = "缩小设备点击范围",
                summary = "点击范围缩小到图标本身，点卡片空白处改为打开融合设备中心",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_SECURITY",
            title = "安全服务",
        ) {
            SwitchPreference(
                key = Preferences.HyperRefine.FAST_CHARGE_ENTER,
                title = "隐藏进入快充加速提醒",
                summary = "充电时不再弹出进入快充加速的提醒",
            )
            SwitchPreference(
                key = Preferences.HyperRefine.FAST_CHARGE_EXIT,
                title = "隐藏退出快充加速提醒",
                summary = "退出快充加速时不再弹出提醒",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_MILINK",
            title = "设备互联",
        ) {
            SwitchPreference(
                key = Preferences.HyperRefine.MIPLAY_DELAY,
                title = "妙播音频低延迟",
                summary = "把妙播建流时的音频缓冲从 800ms/1000ms 压到 50ms，改善流转到音箱、耳机时看视频的音画不同步",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_AOD",
            title = "息屏",
        ) {
            SwitchPreference(
                key = Preferences.HyperRefine.AOD_FULL_BRIDGE,
                title = "全屏 AOD 智能显示修复",
                summary = "强开全屏 AOD 后，智能显示熄灭再恢复时不再黑屏。改动需重启系统生效",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_THIRD_PARTY",
            title = "第三方应用",
        ) {
            SwitchPreference(
                key = Preferences.ThirdParty.WECHAT_COMPAT,
                title = "微信小程序兼容",
                summary = "把微信客户端版本伪装成 8.0.79（versionCode 3200），解决部分小程序提示版本过低、打不开。需在 LSPosed 作用域里勾选微信；改完必须强制停止微信再打开",
            )
            SwitchPreference(
                key = Preferences.ThirdParty.ALIPAY_SPEED,
                title = "支付宝极速模式",
                summary = "向支付宝伪造低端机信号，让它走自带的降级省流逻辑。需在 LSPosed 作用域里勾选支付宝；改完必须强制停止支付宝再打开",
            )
        }
        itemPreferenceGroup(
            key = "MAIN_ABOUT",
            position = ItemPosition.Last
        ) {
            TextPreference(
                icon = ImageIcon(R.drawable.ic_header_about),
                title = stringResource(R.string.page_about),
                onClick = { onAction(MainPageAction.NavigateTo(Route.About)) },
            )
        }
    }
}
