package cn.ianzb.hyperrefine.hook.splash

import android.app.Activity
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * 学习通开屏图替换的实际挂载逻辑（移植自 ChaoxingSplash 1.9.4，去掉自定义选图）。
 *
 * 两种形态：
 *
 * - **应用侧**（`com.chaoxing.mobile`）：先用 `getIdentifier` 解出 `drawable/splash` 的真实 id，
 *   在 `Resources#loadDrawable` 里按 id 比对替换；再 hook `Activity#onCreate` 扫 `id/iv_splash` 兜底。
 * - **系统侧**（`system_server` / `com.android.systemui`）：拿不到对方进程解出的 id，只能反查
 *   资源名（`com.chaoxing.mobile:drawable/splash`）。先用 `0x7f` 高位做免费过滤，命中才查名，
 *   结果按 `(Resources 身份哈希, resId)` 缓存，别给系统资源加载加长期开销。
 *
 * 两条铁律：
 * 1. 开关**在拦截时判**，不在安装时判 —— system_server 起来时 remote preferences 还没就绪，
 *    读到默认值就永久不装，症状和「没注入」一模一样；
 * 2. 系统进程里**绝不能抛异常**，任何问题都 `chain.proceed()` 退回原逻辑。
 */
object SplashHooks {

    @Volatile
    private var appInstalled = false

    @Volatile
    private var systemInstalled = false

    @Volatile
    private var extName = "system"

    @Volatile
    private var lastCheckAt = 0L

    @Volatile
    private var lastEnabled = false

    private var splashIds: IntArray? = null
    private var splashNames: Array<String>? = null
    private var idsResolved = false
    private var resolveAttempts = 0
    private var installedAt = 0L

    /** system_server 里的判定缓存；键 = identityHashCode(res) shl 32 or resId。 */
    private val systemDecisions = HashMap<Long, Boolean>()
    private var systemProbeLogs = 0
    private var systemReplaceCount = 0

    // ------------------------------------------------------------ 开关

    /**
     * 当前是否生效。要求「宿主总开关 + 开屏替换开关」同时为真；1 秒缓存，
     * 既不会被每次资源加载拖慢，又能在用户改开关后很快跟上。
     */
    fun enabledNow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckAt < 1_000L) return lastEnabled
        lastCheckAt = now
        val on = runCatching {
            HookPrefs.getBoolean(SplashKeys.MASTER_SWITCH, true) &&
                HookPrefs.getBoolean(SplashKeys.REPLACE, SplashKeys.DEFAULT_ON)
        }.getOrDefault(false)
        lastEnabled = on
        return on
    }

    // ------------------------------------------------------------ 安装

    /** 应用侧（学习通自己的进程，含 `:push` 等子进程）。 */
    fun installForApp() {
        if (appInstalled) return
        synchronized(this) {
            if (appInstalled) return
            appInstalled = true
            installedAt = System.currentTimeMillis()
        }
        runCatching { hookLoadDrawable(system = false) }
            .onFailure { Log.e(SplashKeys.TAG, "hook Resources#loadDrawable failed", it) }
        runCatching { hookActivityOnCreate() }
            .onFailure { Log.e(SplashKeys.TAG, "hook Activity#onCreate failed", it) }
    }

    /** 系统侧（画启动窗口首帧的进程）。重复调用安全。 */
    fun installForSystem(name: String) {
        if (systemInstalled) return
        synchronized(this) {
            if (systemInstalled) return
            systemInstalled = true
            extName = name
        }
        runCatching {
            val count = hookLoadDrawable(system = true)
            Log.i(SplashKeys.TAG, "[$name] hooked $count loadDrawable method(s)")
            HookHelper.log("${SplashKeys.TAG} [$name] hooked $count loadDrawable method(s)")
        }.onFailure { Log.e(SplashKeys.TAG, "[$name] hook failed", it) }
    }

    // ------------------------------------------------------------ 资源层

    /**
     * 挂 `Resources#loadDrawable(TypedValue, int, int, Theme)`。
     *
     * @param system true = 系统进程，按资源名判定；false = 学习通进程，按已解析的 id 判定
     */
    private fun hookLoadDrawable(system: Boolean): Int {
        var count = 0
        for (method in Resources::class.java.declaredMethods) {
            if (method.name != "loadDrawable") continue
            val types = method.parameterTypes
            if (types.size != 4 || types[0] != TypedValue::class.java || types[1] != Int::class.javaPrimitiveType) continue

            method.isAccessible = true
            HookHelper.intercept(method, mode = XposedInterface.ExceptionMode.PROTECTIVE) { chain ->
                try {
                    val self = chain.thisObject
                    if (self !is Resources) {
                        chain.proceed()
                    } else if (!enabledNow()) {
                        chain.proceed()
                    } else {
                        val resId = chain.args.getOrNull(1) as? Int ?: 0
                        val hit = if (system) isTargetSplashInSystem(self, resId) else splashNameFor(self, resId) != null
                        if (!hit) {
                            chain.proceed()
                        } else {
                            val drawable = SplashAssets.drawable()
                            if (drawable == null) {
                                chain.proceed()
                            } else {
                                if (system) {
                                    systemReplaceCount++
                                    if (systemReplaceCount <= 5 || systemReplaceCount % 20 == 0) {
                                        Log.i(
                                            SplashKeys.TAG,
                                            "[$extName] replaced drawable 0x${Integer.toHexString(resId)} #$systemReplaceCount",
                                        )
                                    }
                                } else {
                                    Log.i(SplashKeys.TAG, "replaced drawable 0x${Integer.toHexString(resId)}")
                                }
                                drawable
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(SplashKeys.TAG, "intercept error: $t")
                    chain.proceed()
                }
            }
            count++
        }
        return count
    }

    /**
     * system_server 里判断这个 id 是不是学习通的开屏图。
     * 先用资源 id 的高位（0x7f = 应用包）做免费过滤，命中才做较贵的 [Resources.getResourceName]，
     * 结果按 Resources 实例 + id 缓存。
     */
    private fun isTargetSplashInSystem(res: Resources, resId: Int): Boolean {
        if (resId == 0 || (resId ushr 24) != 0x7f) return false
        val key = (System.identityHashCode(res).toLong() shl 32) or (resId.toLong() and 0xffffffffL)
        synchronized(systemDecisions) {
            systemDecisions[key]?.let { return it }
        }

        var hit = false
        runCatching {
            val name = res.getResourceName(resId) // com.chaoxing.mobile:drawable/splash
            if (name != null && name.startsWith(SplashKeys.PKG + ":")) {
                if (systemProbeLogs < 40) {
                    systemProbeLogs++
                    Log.i(SplashKeys.TAG, "[$extName] probe 0x${Integer.toHexString(resId)} = $name")
                }
                val slash = name.indexOf('/')
                if (slash > 0) {
                    val type = name.substring(SplashKeys.PKG.length + 1, slash)
                    if (type == "drawable" || type == "mipmap") {
                        val entry = name.substring(slash + 1)
                        hit = SplashKeys.DRAWABLES.any { it == entry } || entry.contains("splash")
                    }
                }
            }
        }

        synchronized(systemDecisions) { systemDecisions[key] = hit }
        return hit
    }

    /**
     * 学习通进程里判断这个 id 是不是开屏图，命中时返回资源名（日志用）。
     */
    private fun splashNameFor(res: Resources, resId: Int): String? {
        if (resId == 0 || (resId ushr 24) != 0x7f) return null
        if (!idsResolved) resolveIds(res)
        val ids = splashIds
        val names = splashNames
        if (ids != null && names != null) {
            for (i in ids.indices) {
                if (ids[i] == resId) return names[i]
            }
        }
        // 资源被改名等情况下，启动早期用资源名兜底（只在前 30 秒生效，避免长期开销）
        val at = installedAt
        if (at != 0L && System.currentTimeMillis() - at < 30_000L) {
            runCatching {
                val name = res.getResourceName(resId)
                val prefix = SplashKeys.PKG + ":drawable/"
                if (name != null && name.startsWith(prefix)) {
                    val entry = name.substring(prefix.length)
                    if (entry.contains("splash") || entry.contains("launch") || entry.contains("welcome")) return entry
                }
            }
        }
        return null
    }

    /** 解析开屏 drawable 的资源 id；找不到不锁定结果，下次重试（最多 5 次）。 */
    private fun resolveIds(res: Resources) {
        synchronized(this) {
            if (idsResolved || resolveAttempts >= 5) return
            resolveAttempts++
            val ids = ArrayList<Int>()
            val names = ArrayList<String>()
            for (name in SplashKeys.DRAWABLES) {
                val id = runCatching { res.getIdentifier(name, "drawable", SplashKeys.PKG) }.getOrDefault(0)
                if (id != 0) {
                    ids.add(id)
                    names.add(name)
                }
            }
            if (ids.isEmpty()) {
                Log.w(SplashKeys.TAG, "splash drawables not found (attempt $resolveAttempts)")
                return
            }
            splashIds = ids.toIntArray()
            splashNames = names.toTypedArray()
            idsResolved = true
            Log.i(
                SplashKeys.TAG,
                "resolved splash ids: ${ids.joinToString(", ") { "0x" + Integer.toHexString(it) }} $names",
            )
        }
    }

    // ------------------------------------------------------------ 视图兜底

    /** 开屏 Activity 里把 `id/iv_splash` 之类的整屏图换成内置图（防止开屏图不走 Resources）。 */
    private fun hookActivityOnCreate() {
        val onCreate = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
        onCreate.isAccessible = true
        HookHelper.intercept(onCreate, mode = XposedInterface.ExceptionMode.PROTECTIVE) { chain ->
            val result = chain.proceed()
            runCatching {
                val self = chain.thisObject
                if (self is Activity && enabledNow()) {
                    val name = self.javaClass.name
                    if (name.contains("Splash") || name.contains("splash")) applyToSplashActivity(self)
                }
            }.onFailure { Log.e(SplashKeys.TAG, "applyToSplashActivity failed", it) }
            result
        }
        Log.i(SplashKeys.TAG, "hooked Activity#onCreate")
    }

    private fun applyToSplashActivity(activity: Activity) {
        if (SplashAssets.bitmap() == null) return
        // 不要覆盖 window 背景：开屏主题的 windowBackground 本身就是 @drawable/splash，
        // 已经被资源层换掉了，覆盖成纯色反而会把它弄丢。
        val res = activity.resources
        val id = res.getIdentifier("iv_splash", "id", SplashKeys.PKG)
        val target = if (id != 0) activity.findViewById<View>(id) else null
        if (target is ImageView) applyImage(target)

        val images = ArrayList<ImageView>()
        collectImageViews(activity.window?.decorView, images)
        for (image in images) {
            if (image !== target && looksLikeSplash(image)) applyImage(image)
        }
    }

    private fun applyImage(view: ImageView) {
        val drawable: Drawable = SplashAssets.drawable() ?: return
        view.setImageDrawable(drawable)
        view.scaleType = ImageView.ScaleType.FIT_XY
        view.setPadding(0, 0, 0, 0)
        val params = view.layoutParams
        if (params != null &&
            (params.width != ViewGroup.LayoutParams.MATCH_PARENT || params.height != ViewGroup.LayoutParams.MATCH_PARENT)
        ) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = params
        }
        Log.i(SplashKeys.TAG, "ImageView replaced: $view")
    }

    private fun collectImageViews(view: View?, out: MutableList<ImageView>) {
        if (view == null) return
        if (view is ImageView) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectImageViews(view.getChildAt(i), out)
        }
    }

    /** 开屏图是整屏 1080x1920 的图，用尺寸做启发式判断。 */
    private fun looksLikeSplash(view: ImageView): Boolean {
        val d = view.drawable ?: return false
        return d.intrinsicWidth >= 480 && d.intrinsicHeight >= 800
    }
}
