package dev.lackluster.mihelper.hook.scopes

import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils
import dev.lackluster.mihelper.hook.rules.systemui.MonetOverlay
import dev.lackluster.mihelper.hook.rules.systemui.ResourcesUtils
import dev.lackluster.mihelper.hook.rules.systemui.StatusBarActions
import dev.lackluster.mihelper.hook.rules.systemui.StatusBarIconTint
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.CarrierLabelFontWeight
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.ForceColorScheme
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.HideDisturbNotification
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.KeepNotification
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.LockscreenDoubleTapToSleep
import dev.lackluster.mihelper.hook.rules.systemui.lockscreen.StatusBarClockContainer
import dev.lackluster.mihelper.hook.rules.systemui.media.CustomBackground
import dev.lackluster.mihelper.hook.rules.systemui.media.CustomElement
import dev.lackluster.mihelper.hook.rules.systemui.media.CustomLayout
import dev.lackluster.mihelper.hook.rules.systemui.media.CustomProgressBar
import dev.lackluster.mihelper.hook.rules.systemui.media.UnlockCustomAction
import dev.lackluster.mihelper.hook.rules.systemui.notif.ExpandNotification
import dev.lackluster.mihelper.hook.rules.systemui.notif.LayoutAndRankOpt
import dev.lackluster.mihelper.hook.rules.systemui.notif.MiuiXExpandButton
import dev.lackluster.mihelper.hook.rules.systemui.notif.NotifFreeform
import dev.lackluster.mihelper.hook.rules.systemui.notif.NotifWhitelist
import dev.lackluster.mihelper.hook.rules.systemui.plugin.PluginFactory
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.BatteryIndicator
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.CellularIcon
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.BatteryIndicatorStyle
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.CellularTypeIcon
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.HideCarrierLabel
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.HideCellularIcon
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.IconManager
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.IgnoreSysIconSettings
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.CompoundIcon
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.MiuiClock
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.MiuiClockFontWeight
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.NetworkSpeed
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.WifiIcon
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.NotificationMaxNumber
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.RegionSampling
import dev.lackluster.mihelper.hook.rules.systemui.mobile.StackedMobileIcon
import dev.lackluster.mihelper.hook.rules.systemui.wifi.CustomWifiSignalIcon
import dev.lackluster.mihelper.hook.rules.systemui.notif.SuppressFold
import dev.lackluster.mihelper.hook.rules.systemui.statusbar.StatusBarTapGesture

/**
 * 精简版：只挂状态栏图标与通知相关的 Hooker。
 *
 * - [ResourcesUtils] / [CommonClassUtils]：图标链路依赖的资源与类缓存，属基础设施；
 * - [IconManager]：把 stacked_mobile_icon / type / single_mobile_sim1|2 四个槽位注册进
 *   StatusBarIconList 与 RIGHT_BLOCK_LIST，否则图标要么不显示、要么被挤到队尾；
 * - [StackedMobileIcon]：双排堆叠信号图标本体；
 * - [HideCellularIcon]：开启双排后隐藏系统原生的单卡信号图标（否则新旧两套同时出现）；
 * - [StatusBarIconTint]：给自绘的 ALPHA_8 位图套上 SystemUI 的明暗反色 Tint
 *   （不挂会保持纯黑，深色壁纸下看不见）。
 */
object SystemUI : StaticHooker() {
    override fun onInit() {
        attach(ResourcesUtils)
        attach(CommonClassUtils)
        attach(IconManager)
        attach(StackedMobileIcon)
        attach(HideCellularIcon)
        attach(StatusBarIconTint)
    }
}
