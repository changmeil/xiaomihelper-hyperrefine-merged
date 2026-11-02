package cn.ianzb.hyperrefine.hook.systemui.media

/**
 * 哔哩哔哩（`tv.danmaku.bili`）媒体控制隐藏。
 *
 * HyperOS 的媒体控制卡片在 SystemUI 里只有一份数据源（AOSP 的 `MediaDataManager`），
 * 但会被画在两个位置：下拉通知栏顶部的媒体卡，以及锁屏通知列表顶部同一块区域
 * （小米在通知栈里插了 `MiuiMediaHeaderView`，由 `MiuiMediaNotificationControllerImpl` 驱动）。
 * 所以「锁屏」和「通知栏」要分开处理：
 *
 * | 开关 | 做法 | 效果 |
 * |---|---|---|
 * | [KEYGUARD] | 只在状态栏处于 KEYGUARD 时把这块 view 压成 GONE | 锁屏看不到，解锁后下拉照旧能控制播放 |
 * | [SHADE] | 数据层直接不发布这条 MediaData | 锁屏、下拉都不出现（普通通知不受影响） |
 *
 * 两个开关互相独立：只开 [KEYGUARD] 是最小改动；两个都开等价于彻底不生成媒体条目。
 */
object BiliMediaKeys {

    /** 哔哩哔哩国内版包名。 */
    const val PKG = "tv.danmaku.bili"

    /** 锁屏隐藏（下拉保留）。 */
    const val KEYGUARD = "third_party_bili_media_keyguard"

    /** 通知栏也不显示（等价于不生成媒体条目）。 */
    const val SHADE = "third_party_bili_media_shade"
}
