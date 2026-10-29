package cn.ianzb.hyperrefine.hook.aod

/**
 * 息屏 / 全屏 AOD 相关配置键。
 */
object AodBridgeKeys {

    /**
     * 全屏 AOD 智能显示恢复修复（目标 `system`，即 system_server）。
     *
     * 强制开启全屏 AOD 后，智能显示熄灭再恢复时面板停在 LP1、通用背光被丢弃导致黑屏；
     * 开启本项后在 OFF→DOZE 恢复边沿临时切到 NORMAL 写入亮度再回 DOZE。
     */
    const val FULL_AOD_BRIDGE = "aod_fullscreen_bridge"
}
