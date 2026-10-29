package dev.lackluster.mihelper.hook.scopes

import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.miuihome.anim.DisableIconAnim
import dev.lackluster.mihelper.hook.rules.miuihome.folder.FolderAdaptIconSize

/**
 * 精简版：只挂与「桌面文件夹」相关的两个 Hooker。
 *
 * - [FolderAdaptIconSize]：大文件夹（3x3 九宫格 / 2x2）内图标大小自适应；
 * - [DisableIconAnim]：文件夹点击缩放动画、图标点击黑色遮罩。
 *
 * 两者都在 onInit 阶段按各自的偏好开关决定是否生效，未开启时等价于不挂载。
 */
object MiuiHome : StaticHooker() {
    override fun onInit() {
        attach(FolderAdaptIconSize)
        attach(DisableIconAnim)
    }
}
