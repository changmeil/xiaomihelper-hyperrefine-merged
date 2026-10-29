package cn.ianzb.hyperrefine.hook.embed

import android.annotation.SuppressLint
import android.util.Log
import cn.ianzb.hyperrefine.hook.prefs.HookPrefs
import cn.ianzb.hyperrefine.hook.xposed.HookHelper
import dalvik.system.InMemoryDexClassLoader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.lackluster.mihelper.BuildConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * 内置第三方模块的运行时装载器。
 *
 * 两个模块的 dex 以 assets 形式打包进本 APK，资源路径前缀见 [ASSET_PREFIX]，
 * 运行期用 `InMemoryDexClassLoader` 加载，父 ClassLoader 是本模块的 ClassLoader。
 *
 * | 模块 | 入口 | API | 作用域 |
 * |---|---|---|---|
 * | 微信小程序兼容 1.0.6 | `com.xici.wxcompat.WxCompatModule` | libxposed 101（可直接用） | `com.tencent.mm` |
 * | 支付宝极速模式 1.0.0 | `xiaojw.alipayspeed.MainHook` | 老 de.robv API（走兼容桩） | `com.eg.android.AlipayGphone` |
 *
 * 之所以不在编译期直接引用它们的类：它们都是独立发布的 APK，没有源码也没有 maven 坐标。
 * 运行时装载可以**原样保留**作者的 hook 逻辑，不需要照着重写一遍（重写必然对不上混淆方法名）。
 *
 * 设计约束（踩过的坑，改之前先看）：
 *
 * 1. **不能依赖回调顺序**。`onModuleLoaded` 与 `onPackageReady` 的先后在个别框架实现上不可靠，
 *    所以 APK 路径与进程名都在真正要用的时候才去解析（[resolveApkPath] / [LoadedParam] 兜底）。
 * 2. **不能只看第一个包**。微信的版本上报/小程序在 `com.tencent.mm:appbrandN` 等子进程里也会跑，
 *    只给「第一个包」装 hook 会出现「主进程装了、子进程没装」的半残状态。
 * 3. **失败必须留痕**。任何一步失败都同时写 logcat（[TAG]）和框架日志（[HookHelper]），
 *    否则表现为「开关打开了但什么都没发生」，完全没法排查。
 *
 * 配置：两个开关都在主界面「第三方应用」分组，键见 [KEY_WECHAT] / [KEY_ALIPAY]。
 * 由于 hook 是在目标进程启动时装上的，**改完开关必须强制停止对应的 App**。
 */
object EmbeddedModules {

    private const val TAG = "HyperRefineEmbed"

    const val KEY_WECHAT = "embed_wechat_compat"
    const val KEY_ALIPAY = "embed_alipay_speed"

    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    private const val WECHAT_DEX = "wxcompat.dex"
    private const val ALIPAY_DEX = "alipayspeed.dex"

    private const val WECHAT_ENTRY = "com.xici.wxcompat.WxCompatModule"
    private const val ALIPAY_ENTRY = "xiaojw.alipayspeed.MainHook"

    private const val ASSET_PREFIX = "assets/modules/"

    /** dex 文件头魔数：`dex\n035\0` / `dex\n037\0` / `dex\n038\0` / `dex\n039\0`。 */
    private const val DEX_MAGIC = "dex\n"

    @Volatile
    private var apkPath: String? = null

    @Volatile
    private var processName: String = ""

    /** 宿主入口在 `onModuleLoaded` 收到的框架参数，转手交给内置模块复用。 */
    @Volatile
    private var loadedParam: XposedModuleInterface.ModuleLoadedParam? = null

    @Volatile
    private var wechatModule: XposedModule? = null

    @Volatile
    private var alipayHook: IXposedHookLoadPackage? = null

    /** 由入口在 `onModuleLoaded` 阶段调用，记下入口实例、模块 APK 路径与当前进程信息。 */
    fun attach(
        module: XposedModule,
        moduleApkPath: String?,
        currentProcess: String,
        param: XposedModuleInterface.ModuleLoadedParam?,
    ) {
        loadedModule = module
        if (!moduleApkPath.isNullOrEmpty()) apkPath = moduleApkPath
        processName = currentProcess
        loadedParam = param
    }

    /** 在目标包就绪时调用；内部按包名分派，并按开关决定是否装载。 */
    fun onPackageReady(param: XposedModuleInterface.PackageReadyParam, xposed: XposedInterface) {
        when (param.packageName) {
            WECHAT_PACKAGE -> {
                if (!HookPrefs.getBoolean(KEY_WECHAT, false)) {
                    log("wechat: switch off, skip (process=$processName)")
                    return
                }
                installWeChat(param, xposed)
            }

            ALIPAY_PACKAGE -> {
                if (!HookPrefs.getBoolean(KEY_ALIPAY, false)) {
                    log("alipay: switch off, skip (process=$processName)")
                    return
                }
                installAlipay(param)
            }
        }
    }

    // ---------------- 微信 ----------------

    private fun installWeChat(param: XposedModuleInterface.PackageReadyParam, xposed: XposedInterface) {
        val startedAt = android.os.SystemClock.elapsedRealtime()

        val module = wechatModule ?: loadWeChatModule(xposed) ?: return

        runCatching { module.onPackageReady(param) }
            .onFailure { log("wechat: onPackageReady failed", it) }
            .onSuccess {
                log(
                    "wechat: onPackageReady done in ${param.packageName} (process=$processName, " +
                        "${android.os.SystemClock.elapsedRealtime() - startedAt}ms, target=${targetVersion()})"
                )
                probeWeChat(param.classLoader)
            }
    }

    /**
     * 自检：模块是按**具体微信版本**的混淆类名定位 hook 点的（见 dex 里的 CLS_*）。
     * 微信一升级，`c64.xx` 这类名字就变了，模块会静默什么都不做。
     *
     * 这里只做**只读探测**、不改任何行为，把结果打进日志，用来区分两种「不生效」：
     *
     * - `setup/device/z/baseReq/...` 全 false → 版本不匹配，hook 点没找到（要重新定位类名）；
     * - 全 true 而小程序仍提示版本低 → hook 装上了但被服务端别处拦，或进程不对。
     */
    private fun probeWeChat(classLoader: ClassLoader?) {
        if (classLoader == null) {
            log("wechat: probe skipped (classLoader=null)")
            return
        }
        val probes = listOf(
            "com.tencent.mm.app.e5" to "setup",
            "c64.xe" to "device",
            "c64.ze" to "z",
            "c64.bg" to "baseReq",
            "com.tencent.mm.modelbase.i1" to "reqRespBase",
            "com.tencent.mm.sdk.platformtools.z" to "platformZ",
        )
        val result = probes.joinToString(", ") { (name, tag) ->
            val clazz = runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            val extra = when {
                clazz == null -> ""
                tag == "reqRespBase" -> ", fillBaseReq=" + clazz.declaredMethods
                    .any { it.name == "fillBaseReq" }
                tag == "baseReq" -> ", setClientVersion=" + clazz.declaredMethods
                    .any { it.name == "setClientVersion" }
                else -> ""
            }
            "$tag=${clazz != null}$extra"
        }
        log("wechat: probe $result")
    }

    private fun loadWeChatModule(xposed: XposedInterface): XposedModule? {
        val instance = runCatching {
            val bytes = readAsset(WECHAT_DEX) ?: return null
            val loader = InMemoryDexClassLoader(ByteBuffer.wrap(bytes), javaClass.classLoader)
            val entry = loader.loadClass(WECHAT_ENTRY)
            val module = entry.getDeclaredConstructor().newInstance() as XposedModule
            // 必须先把 libxposed 的 XposedInterface 接上：模块在 onModuleLoaded 里就会调用
            // getFrameworkName()/getApiVersion()，没接上会抛 ensureAttached 的 IllegalStateException。
            //
            // 这里**不能**直接写 module.attachFramework(xposed)：那是 api 101 的签名，
            // 而框架注入到目标进程里的 api 版本不一定是 101。实测（LSPosed + 微信 8.0.49）
            // XposedModule 及其父类压根没有这个方法，直接调会抛
            //   NoSuchMethodError: No virtual method attachFramework(XposedInterface)V
            // 于是整个模块装载失败、连 onModuleLoaded 都进不去，表现就是「开关开了但毫无反应」。
            if (!attachCompat(module, xposed)) {
                log("wechat: attach failed, module not usable (shape dumped above)")
                return null
            }
            module
        }.onFailure {
            log("wechat: module load failed ($WECHAT_ENTRY)", it)
        }.getOrNull() ?: return null

        runCatching { instance.onModuleLoaded(loadedParam ?: LoadedParam(processName)) }
            .onFailure {
                log("wechat: onModuleLoaded failed", it)
                return null
            }

        wechatModule = instance
        log("wechat: module loaded ok ($WECHAT_ENTRY, process=$processName)")
        return instance
    }

    // ---------------- libxposed 版本适配 ----------------

    /** 各版本里「把 framework 接给 module」可能用到的方法名。 */
    private val ATTACH_METHODS = listOf("attachFramework", "attach", "setFramework", "setBase", "attachBase")

    /**
     * 把 `XposedInterface` 接到内置模块实例上，**不依赖具体 api 版本**。
     *
     * 三种策略依次尝试，任一成功即返回：
     *
     * 1. 按方法名找：整个继承链上任意「单参数、参数能接受 [xposed]」的 attach/setBase 方法；
     * 2. 按字段找：整个继承链上任意「能装下 [xposed] 的实例字段」直接赋值
     *    —— `attachFramework` 内部本质上就是 `mBase = framework`，所以这条路等价；
     * 3. 全失败时把 `XposedModule` 的真实方法/字段结构打进日志，下一轮照实际签名接。
     */
    private fun attachCompat(module: XposedModule, xposed: XposedInterface): Boolean {
        var clazz: Class<*>? = module.javaClass
        while (clazz != null) {
            val owner = clazz
            for (m in owner.declaredMethods) {
                if (m.parameterCount != 1 || m.name !in ATTACH_METHODS) continue
                if (!m.parameterTypes[0].isInstance(xposed)) continue
                val invoked = runCatching {
                    m.isAccessible = true
                    m.invoke(module, xposed)
                    true
                }.onFailure { log("wechat: attach via ${owner.name}#${m.name} threw", it) }
                    .getOrDefault(false)
                if (invoked && verifyAttached(module)) {
                    log("wechat: attached via method ${owner.name}#${m.name}(${m.parameterTypes[0].name})")
                    return true
                }
            }
            clazz = owner.superclass
        }

        clazz = module.javaClass
        while (clazz != null) {
            val owner = clazz
            for (f in owner.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                if (!f.type.isInstance(xposed) && !f.type.name.endsWith("XposedInterface")) continue
                val assigned = runCatching {
                    f.isAccessible = true
                    f.set(module, xposed)
                    f.get(module) === xposed
                }.onFailure { log("wechat: set ${owner.name}.${f.name} failed", it) }
                    .getOrDefault(false)
                if (assigned) {
                    log("wechat: attached via field ${owner.name}.${f.name} (${f.type.name})")
                    return true
                }
            }
            clazz = owner.superclass
        }

        dumpApiShape()
        return false
    }

    /**
     * 是否真的接上了：调 `getApiVersion()` 试一下。
     *
     * - 正常返回 → 接上了；
     * - 抛 `IllegalStateException`（ensureAttached 拦的）→ 没接上；
     * - 方法不存在 → 该 api 版本没有这个方法，无法判断，按「接上了」处理
     *   （此时前面的方法/字段调用没抛异常，已经是最好的证据）。
     */
    private fun verifyAttached(module: XposedModule): Boolean = runCatching {
        module.javaClass.getMethod("getApiVersion").invoke(module)
        true
    }.getOrElse { it is NoSuchMethodException }

    /** 把框架注入的 `XposedModule` 的真实结构打进日志（分块，避免单行过长被截断）。 */
    private fun dumpApiShape() {
        val sb = StringBuilder("XposedModule shape:")
        var clazz: Class<*>? = XposedModule::class.java
        while (clazz != null) {
            val owner = clazz
            sb.append("\n  ").append(owner.name).append(" {")
            for (f in owner.declaredFields) {
                sb.append("\n    F ").append(java.lang.reflect.Modifier.toString(f.modifiers))
                    .append(' ').append(f.type.name).append(' ').append(f.name)
            }
            for (m in owner.declaredMethods) {
                sb.append("\n    M ").append(m.name)
                    .append('(').append(m.parameterTypes.joinToString(",") { it.name }).append(')')
            }
            sb.append("\n  }")
            clazz = owner.superclass
        }
        val text = sb.toString()
        text.chunked(1500).forEach { log(it) }
    }

    // ---------------- 支付宝 ----------------

    private fun installAlipay(param: XposedModuleInterface.PackageReadyParam) {
        val hook = alipayHook ?: runCatching {
            val bytes = readAsset(ALIPAY_DEX) ?: return
            val loader = InMemoryDexClassLoader(ByteBuffer.wrap(bytes), javaClass.classLoader)
            val entry = loader.loadClass(ALIPAY_ENTRY)
            val instance = entry.getDeclaredConstructor().newInstance() as IXposedHookLoadPackage
            (instance as? IXposedHookZygoteInit)
                ?.initZygote(IXposedHookZygoteInit.StartupParam(resolveApkPath() ?: "", false))
            alipayHook = instance
            log("alipay: module loaded ok ($ALIPAY_ENTRY, process=$processName)")
            instance
        }.onFailure {
            log("alipay: module load failed ($ALIPAY_ENTRY)", it)
        }.getOrNull() ?: return

        val lpparam = XC_LoadPackage.LoadPackageParam(
            packageName = param.packageName,
            classLoader = param.classLoader,
            appInfo = param.applicationInfo,
            isFirstApplication = true,
        ).apply { processName = this@EmbeddedModules.processName }

        runCatching { hook.handleLoadPackage(lpparam) }
            .onFailure { log("alipay: handleLoadPackage failed", it) }
    }

    // ---------------- 工具 ----------------

    /** 从本模块 APK 的 assets 里读一份 dex；读不到返回 null（已记日志）。 */
    private fun readAsset(assetName: String): ByteArray? = runCatching {
        val path = resolveApkPath() ?: error("module apk path unknown")
        val bytes = ZipFile(path).use { zip ->
            val entry = zip.getEntry(ASSET_PREFIX + assetName) ?: error("asset missing: $assetName")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        if (bytes.size < 8 || String(bytes, 0, 4, Charsets.US_ASCII) != DEX_MAGIC) {
            error("not a dex: $assetName (${bytes.size} bytes)")
        }
        log("dex ready: $assetName ${bytes.size} bytes from $path")
        bytes
    }.onFailure {
        log("dex read failed: $assetName", it)
    }.getOrNull()

    /**
     * 本模块 APK 的绝对路径。三条路依次尝试，任何一条成功即缓存：
     *
     * 1. 框架给的 `ApplicationInfo.sourceDir`（正常情况）；
     * 2. `ActivityThread` 的系统 Context + PackageManager 查自己（框架实现异常时兜底）；
     * 3. 直接猜 `/data/app/...` 不可靠，不做。
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun resolveApkPath(): String? {
        apkPath?.let { return it }

        val fromFramework = runCatching { moduleAppInfo()?.sourceDir }.getOrNull()
        if (!fromFramework.isNullOrEmpty()) {
            apkPath = fromFramework
            return fromFramework
        }

        val fromPm = runCatching {
            val thread = Class.forName("android.app.ActivityThread")
                .getMethod("currentActivityThread")
                .invoke(null) ?: return@runCatching null
            val context = thread.javaClass.getMethod("getSystemContext").invoke(thread) as? android.content.Context
                ?: return@runCatching null
            context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).sourceDir
        }.onFailure { log("apk path via PackageManager failed", it) }
            .getOrNull()

        if (!fromPm.isNullOrEmpty()) {
            apkPath = fromPm
            return fromPm
        }

        log("apk path unresolved (framework + PackageManager both failed)")
        return null
    }

    /** `getModuleApplicationInfo()` 需要框架已 attach；单独包一层便于日志定位。 */
    private fun moduleAppInfo(): android.content.pm.ApplicationInfo? = runCatching {
        val wrapper = loadedModule
        wrapper?.moduleApplicationInfo
    }.getOrNull()

    /**
     * 本模块入口实例（`HfEntry`），由 [attach] 时塞进来。
     *
     * 不能直接引用 [io.github.libxposed.api.XposedModule] 上的方法：`getModuleApplicationInfo()`
     * 是接口默认实现，必须拿到真实入口实例才调得通。
     */
    @Volatile
    private var loadedModule: XposedModule? = null

    /** 目标应用当前版本（仅用于日志，方便判断「模块到底跑没跑、对没对上版本」）。 */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun targetVersion(): String = runCatching {
        val thread = Class.forName("android.app.ActivityThread")
            .getMethod("currentActivityThread")
            .invoke(null) ?: return@runCatching "?"
        val context = thread.javaClass.getMethod("getSystemContext").invoke(thread) as? android.content.Context
            ?: return@runCatching "?"
        val info = context.packageManager.getPackageInfo(WECHAT_PACKAGE, android.content.pm.PackageManager.GET_META_DATA)
        // CLIENT_VERSION 是微信上报用的协议版本号（形如 0x28004f30），模块的伪装目标就是它。
        val clientVersion = runCatching {
            info.applicationInfo?.metaData?.get("com.tencent.mm.BuildInfo.CLIENT_VERSION")?.let {
                "0x" + java.lang.Long.toHexString((it as Number).toLong())
            }
        }.getOrNull()
        "${info.versionName}/${info.longVersionCode} clientVersion=$clientVersion"
    }.getOrDefault("?")

    /** 同时写 logcat 与框架日志：前者给 `adb logcat -s HyperRefineEmbed`，后者给 LSPosed 日志页。 */
    private fun log(message: String, throwable: Throwable? = null) {
        Log.i(TAG, message)
        if (throwable != null) {
            Log.e(TAG, message, throwable)
            HookHelper.log(message, throwable)
        } else {
            HookHelper.log(message)
        }
    }

    /** 框架没有给 `ModuleLoadedParam` 时的兜底（只用到 `getProcessName()`）。 */
    private class LoadedParam(private val process: String) : XposedModuleInterface.ModuleLoadedParam {
        override fun isSystemServer(): Boolean = false
        override fun getProcessName(): String = process
    }
}
