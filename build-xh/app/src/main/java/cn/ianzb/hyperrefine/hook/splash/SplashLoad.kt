package cn.ianzb.hyperrefine.hook.splash

import cn.ianzb.hyperrefine.hook.base.BaseLoad
import cn.ianzb.hyperrefine.hook.base.PackageTarget

/**
 * 学习通目标 Load：只在 `com.chaoxing.mobile` 的进程里装开屏替换。
 *
 * 之所以不写成 [cn.ianzb.hyperrefine.hook.base.BaseHook]，是因为这里的「开关」不能参与安装决策：
 * 一条 hook 里同时挂资源层和视图层两处，且要求开关实时生效，所以无条件安装、拦截时再判。
 */
class SplashLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf(SplashKeys.PKG)

    override fun onPackageLoaded(target: PackageTarget) {
        SplashHooks.installForApp()
    }
}
