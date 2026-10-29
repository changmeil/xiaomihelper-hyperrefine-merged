package cn.ianzb.hyperrefine.hook.securitycenter

import android.app.Notification
import cn.ianzb.hyperrefine.hook.base.BaseHook
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import cn.ianzb.hyperrefine.hook.xposed.HookParam
import cn.ianzb.hyperrefine.hook.xposed.Reflect
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method

/**
 * 快充加速通知隐藏基类。
 *
 * 90W 及以上快充机型在快充加速过程中，安全服务（`com.miui.securitycenter`）会在
 * `com.miui.powercenter.high`（通知类别「省电与电池重要通知」）上发布两条通知：
 * - 进入提醒（标题 `pc_fast_charge_notifi_title`、动作「立即使用」）；
 * - 退出提醒（标题 `pc_fast_charge_notifi_title_on`、动作「退出」）。
 *
 * 二者由同一个混淆类中的两个静态方法构建并发送，通知 id 均为 `0x7876cce2`。
 * 手机与平板功能一致、仅混淆类名不同（如 `ch.d` / `yg.d`），故统一用 DexKit
 * 按字符串特征定位，不写死类名。
 *
 * v6 加固（实机反馈「退出快充加速提醒仍在」后重做）：
 * 1. 原先只 hook 命中的**第一个**方法，而 `usingStrings(marker)` 是 Contains 匹配，
 *    同一个 marker 往往命中多个方法（渠道注册、标题拼接、真正发送），取第一个很容易
 *    打在无关方法上；现在把命中的方法**全部** hook。
 * 2. 增加 [FastChargeNotifyGuard]：直接在 `NotificationManager#notify*` 上拦截，
 *    按通知渠道 / 文案判定。发送链路无论换成哪个方法、哪个进程，只要还是安全服务发的
 *    通知就一定经过这里，不再依赖混淆方法名。
 */
abstract class BaseFastChargeNotifyHook : BaseHook() {

    /** 通知发送方法内引用的特征串（在通知类内唯一）。 */
    protected abstract val marker: String

    override fun init() {
        installMethodHooks()
        // 兜底：方法级 hook 没命中时，仍然能在 NotificationManager 处拦住。
        FastChargeNotifyGuard.ensureInstalled()
    }

    /**
     * 把通知类里所有引用 [marker] 的方法都挂上。
     *
     * 同时保留全局兜底查询：一旦特征类定位失败（不同 ROM 的 `showExitFastChargeNotification`
     * 被改名或合并），退化为全 dex 搜索 [marker]。命中集合可能偏大，但对这条提示来说
     * 「多挂几个方法」的代价远小于「一条都没挂上」。
     */
    private fun installMethodHooks() {
        val methods = resolveNotifyMethods()
        if (methods.isEmpty()) {
            HookHelper.log("$tag: notify method not found, rely on notify guard")
            return
        }
        methods.forEach { method ->
            runCatching {
                HookHelper.deoptimize(method)
                HookHelper.hookBefore(method) { param ->
                    if (HookPrefs.getBoolean(key, false)) param.setResultValue(null)
                }
            }.onFailure { HookHelper.log("$tag: hook ${method.name} failed", it) }
        }
        HookHelper.log("$tag: hooked ${methods.size} method(s) ${methods.map { it.name }}")
    }

    private fun resolveNotifyMethods(): List<Method> {
        val info = target.applicationInfo ?: return emptyList()
        val loader = target.classLoader ?: return emptyList()
        return runCatching {
            DexKitBridge.create(info.sourceDir).use { bridge ->
                // 注意：不能写成 StringMatchType.Equals。DexKit 的 StringMatcher 默认匹配模式是
                // Contains，上游 HyperRefine 也是用默认值；改成 Equals 后要求整个字符串常量
                // 与标记完全相同，实际 dex 里的常量往往带后缀，匹配不到就会导致整个功能失效。
                val notifyClass = bridge.findClass {
                    matcher {
                        usingStrings(listOf(CLASS_MARKER))
                    }
                }.firstOrNull()

                val scoped = if (notifyClass != null) {
                    bridge.findMethod {
                        matcher {
                            usingStrings(listOf(marker))
                        }
                        searchClasses = listOf(notifyClass)
                    }
                } else {
                    emptyList()
                }

                val hits = scoped.ifEmpty {
                    bridge.findMethod {
                        matcher {
                            usingStrings(listOf(marker))
                        }
                    }
                }
                hits.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .distinctBy { "${it.declaringClass.name}#${it.name}${it.parameterTypes.contentToString()}" }
            }
        }.onFailure { HookHelper.log("$tag: dexkit query failed", it) }.getOrDefault(emptyList())
    }

    companion object {
        /** 仅「退出快充加速」通知使用的日志串，用于唯一定位通知类。 */
        private const val CLASS_MARKER = "showExitFastChargeNotification"
    }
}

/** 进入快充加速提醒：接入充电器且电量低于设定值时的进入提醒。 */
class FastChargeEnterNotifyHook : BaseFastChargeNotifyHook() {

    override val key: String = KEY

    override val marker: String = "fast_charge_enter_notification"

    companion object {
        const val KEY = "security_center_fast_charge_enter_notify"
    }
}

/** 退出快充加速提醒：快充加速过程中的退出提醒。 */
class FastChargeExitNotifyHook : BaseFastChargeNotifyHook() {

    override val key: String = KEY

    override val marker: String = "fast_charge_exit_notification"

    companion object {
        const val KEY = "security_center_fast_charge_exit_notify"
    }
}

/**
 * 通知发送兜底拦截。
 *
 * 挂在 `android.app.NotificationManager#notify` / `#notifyAsPackage` 上（hook 只在
 * 安全服务进程内生效，不影响其它应用）。判定顺序：
 *
 * 1. 渠道 id 含 `fast_charge` → 按 `enter` / `exit` 关键字分派到对应开关；
 *    含 `fast_charge` 但看不出进出（渠道命名被改过）时，任一开关打开即拦。
 * 2. 渠道 id 看不出来 → 回退到文案：标题/正文同时含「快充」与「退出」按退出提醒处理。
 *
 * 每次命中都会打日志，方便实机核对渠道名。
 */
internal object FastChargeNotifyGuard {

    private const val TAG = "FastChargeNotifyGuard"

    private const val ENTER_KEY = FastChargeEnterNotifyHook.KEY
    private const val EXIT_KEY = FastChargeExitNotifyHook.KEY

    /** 已经打过日志的渠道，避免刷屏。 */
    private val reportedChannels = java.util.Collections.synchronizedSet(HashSet<String>())

    @Volatile
    private var installed = false

    @Synchronized
    fun ensureInstalled() {
        if (installed) return
        installed = true

        val clazz = Reflect.findClassIfExists("android.app.NotificationManager", null)
        if (clazz == null) {
            HookHelper.log("$TAG: NotificationManager not found")
            return
        }

        var count = 0
        clazz.declaredMethods
            .filter { it.name == "notify" || it.name == "notifyAsPackage" }
            .forEach { method ->
                val types = method.parameterTypes
                if (types.isEmpty() || !Notification::class.java.isAssignableFrom(types.last())) return@forEach
                runCatching {
                    HookHelper.deoptimize(method)
                    HookHelper.hookBefore(method) { param -> intercept(param) }
                    count++
                }.onFailure { HookHelper.log("$TAG: hook ${method.name} failed", it) }
            }

        HookHelper.log("$TAG: installed on $count method(s)")
    }

    private fun intercept(param: HookParam) {
        val notification = param.args.lastOrNull() as? Notification ?: return
        val key = classify(notification) ?: return

        val id = reportId(notification)
        val enabled = HookPrefs.getBoolean(key, false)
        // 首次见到某个渠道时把开关值一并打出来：能直接区分「没拦住」到底是
        // hook 没生效，还是开关读到的是 false（配置同步问题）。
        if (reportedChannels.add(id)) {
            HookHelper.log("$TAG: seen $id -> key=$key enabled=$enabled")
        }
        if (!enabled) return

        HookHelper.log("$TAG: blocked $id")
        param.setResultValue(null)
    }

    /** 判定这条通知属于哪个开关；返回 null 表示与本模块无关。 */
    private fun classify(notification: Notification): String? {
        val channel = runCatching { notification.channelId }.getOrNull()?.lowercase().orEmpty()
        if (channel.isNotEmpty() && (channel.contains("fast_charge") || channel.contains("fastcharge"))) {
            return when {
                channel.contains("exit") -> EXIT_KEY
                channel.contains("enter") -> ENTER_KEY
                // 渠道名被改过时无法区分进出，任一开关打开就拦。
                else -> if (HookPrefs.getBoolean(ENTER_KEY, false) ||
                    HookPrefs.getBoolean(EXIT_KEY, false)
                ) EXIT_KEY else null
            }
        }

        // 渠道不匹配时退到文案判断：只处理「快充 + 退出」，避免误伤其它通知。
        val text = extractText(notification) ?: return null
        return if (text.contains("快充") && text.contains("退出")) EXIT_KEY else null
    }

    private fun extractText(notification: Notification): String? = runCatching {
        val extras = notification.extras ?: return null
        buildString {
            listOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
            ).forEach { name ->
                (extras.getCharSequence(name) as? CharSequence)?.let { append(it).append('|') }
            }
            notification.actions?.forEach { action ->
                action?.title?.let { append(it).append('|') }
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun reportId(notification: Notification): String {
        val channel = runCatching { notification.channelId }.getOrNull()
        return "channel=$channel"
    }
}
