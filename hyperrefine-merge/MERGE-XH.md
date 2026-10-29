# 以 XiaomiHelper 为底座：双排信号图标 + HyperRefine 的 7 个功能

产物：**`XiaomiHelper-26.05-merged-arm64-release.apk`** —— **6.4 MB**
源码树：`build-xh/`（XiaomiHelper master @ 2026-10-25 + hyperx-compose 子模块 + 我写的 `cn.ianzb.hyperrefine.hook` 包）
状态：`assembleDebug` / `assembleRelease` 均 `BUILD SUCCESSFUL`，R8 混淆 + 资源压缩已开启，签名验证通过。

> 与上一版的关系：上一版是把 7 个功能做在 HyperRefine 里、双排信号用 HyperRefine 自己的
> `DualSimSignalHook`；这次反过来 —— **底座换成 XiaomiHelper，双排信号用它那套自绘引擎**，
> 我的 7 个功能作为第二个 Xposed 入口挂进去。上一版的 APK（`HyperRefine-1.14.0-lite-arm64-release.apk`，3.1 MB）
> 仍然保留，两条路都在。

---

## 一、装的是什么

包名 `dev.lackluster.mihelper`（XiaomiHelper 自己的包名），所以**它和 HyperRefine 是两个独立模块**，
可以同时装。但要替代 XiaomiHelper 官方版，得先卸载官方版（签名不同：这里用的是本机 debug 证书）。

界面上不再有独立页面，主界面直接按功能分组：

| 分组 | 里面是什么 |
|---|---|
| 双排信号图标 | XiaomiHelper 原本的图标定制页，已裁到只剩「移动网络」一个页签：总开关、单卡/双卡风格（MIUI / iOS / iOS27 / 自定义 SVG）、前景/背景/错误色透明度、缩放、左右间距、网络类型字体等 |
| 图标顺序（状态栏分组内） | 排序模式（默认 / 互换移动网络与 WLAN / 自定义）+ 拖拽排序页 + 自动重排开关，保存后重启系统界面生效 |
| 桌面文件夹 | 大文件夹内图标大小自适应（3x3 九宫格 / 2x2）、禁用文件夹点击缩放动画、禁用文件夹内图标点击遮罩 |
| 通知 | 移除焦点通知白名单（任意应用都能以焦点通知 / 超级岛样式展示） |
| 融合设备中心 | 加快图标加载 / 隐藏省略号 / 缩小设备点击范围 |
| 安全服务 | 隐藏进入、退出快充加速提醒 |
| 设备互联 | 妙播音频低延迟 |
| 息屏 | 全屏 AOD 智能显示修复 |
| 第三方应用 | 微信小程序兼容（客户端版本伪装 8.0.79）、支付宝极速模式（伪造低端机信号） |

> v8（2026-10-29）：并入两个第三方模块（微信小程序兼容、支付宝极速模式），
> 用「assets 内置 dex + 运行时装载 + de.robv 兼容桩」的方式接入，作用域扩到
> `com.tencent.mm`、`com.eg.android.AlipayGphone`，详见第九节。
>
> v7（2026-10-29）：新增「通知」分组 → 移除焦点通知白名单（`FocusNotifWhitelist`，见第八节）。
>
> v6（2026-10-28）：退出快充提醒仍弹出 → 改为「方法级全量 hook + NotificationManager 发送级拦截」两层，
> 不再依赖混淆方法名，详见第七节。
>
> v5（2026-10-28）：修「隐藏快充加速不好用」和「开关会莫名关闭」，共三个 bug，详见第六节。
>
> v4（2026-10-28）：新增「桌面文件夹」分组，把作用域扩到 `com.miui.home`。
> 说明：**没有**「九宫格文件夹」这个独立功能——3x3 九宫格预览是桌面自带的大文件夹，不需要模块；
> 模块里与九宫格相关的是「大文件夹内图标大小自适应」（`FolderAdaptIconSize`，hook 的正是
> `FolderIconPreviewContainer2X2_9` / `_2X2_4` 这两个 3x3、2x2 九宫格容器）。
>
> v3（2026-10-28）：把「调整状态栏图标顺序」加回来了 —— 页面与 hook 一直都在，只是主界面没入口；
> 现在主界面「状态栏」分组里多一条「图标顺序」，点进去是 [排序模式 + 拖拽页 + 自动重排]。
> 排序逻辑本身复用已挂载的 `IconManager`（hook `StatusBarIconList` 构造器改写槽位数组），不需要新 hook。
>
> v2（2026-10-28）：原先 7 个开关单独占一个「HyperRefine 功能」页面，已拆掉，
> 按所作用的系统功能直接并入主界面分组；同时修了双排信号的两个实机 bug（见第四节）。

**不含**：小米主题破解（`com.fuck.HyperOSTheme`）。它 hook `com.android.thememanager` 的
`Resource` / `OnlineResourceDetail` / `DiscountPriceView` / `validateTheme`，本质是伪造已购状态让付费主题免费套用，
属于绕过授权的盗版行为，不并入本包。

7 个开关与作用进程：

| 开关 | 包 | 实现来源 |
|---|---|---|
| 加快图标加载 | `com.android.systemui` | HyperRefine `DeviceCenterFastLoadHook` |
| 隐藏省略号 | `com.android.systemui` | HyperRefine `DeviceCenterMoreHook` |
| 缩小设备点击范围 | `com.android.systemui` | HyperRefine `DeviceCenterHitAreaHook` |
| 隐藏进入快充加速提醒 | `com.miui.securitycenter` | HyperRefine `FastChargeEnterNotifyHook` |
| 隐藏退出快充加速提醒 | `com.miui.securitycenter` | HyperRefine `FastChargeExitNotifyHook` |
| 妙播音频低延迟 | `com.milink.service` | HyperRefine `MiPlayDelayHook` |
| 全屏 AOD 智能显示修复 | `system`（system_server） | HyperRefine `AodDozeBridgeHook` |

`scope.list` 只有五行：`system`、`com.android.systemui`、`com.miui.securitycenter`、`com.milink.service`、`com.miui.home`。
XiaomiHelper 原本 32 个包其余全部去掉 —— 界面看不到的功能，注入也不给它开口。

## 二、怎么合的（三处关键设计）

### 1. 两个 Xposed 入口并列，而不是把 7 个 Hook 改写成 XiaomiHelper 的规则

`app/src/main/resources/META-INF/xposed/java_init.list`：

```
dev.lackluster.mihelper.hook.HookEntry          # 宿主：只挂双排信号图标
cn.ianzb.hyperrefine.hook.xposed.HfEntry        # 迁移：7 个功能
```

libxposed 支持一个模块注册多个入口类，各自独立收发 `onPackageLoaded` / `onSystemServerStarting`。
这样 7 个 Hook 的挂载代码**一行没改**（`DeviceCenterMoreHook` 那种 200 多行、十几个 hook 点的规则
原样搬过来就行），不用把 `HookHelper.hookBefore` 那套改写成 XiaomiHelper 的 `BaseHooker.hook { }` 风格——
那种改写量大且容易在细节上改错。

### 2. 配置共用一份 `config` 偏好

XiaomiHelper 的偏好文件是 `config`（`Preferences.NAME`），hook 侧通过
`module.getRemotePreferences("config")` 读。迁移版只做了两件事：

- `HookPrefs.init(sharedPreferences)` 换成读 `config` 分组；
- 去掉上游的 `prefs_key_` 前缀，键名与 `Preferences.HyperRefine.*` 完全一致。

于是 UI 里的开关和 Hook 侧读到的键天然对齐，不需要任何同步桥接代码。

### 3. 裁剪靠「不注册 + 不收作用域」，不删源码

| 位置 | 改法 |
|---|---|
| `HookEntry#getHookerByPackageName` | 30 个 `when` 分支只留 `Scope.SYSTEM_UI`、`Scope.MIUI_HOME` |
| `HookEntry#onSystemServerStarting` | 不再挂 `Android` 规则（system_server 只剩 AOD 修复） |
| `SystemUI#onInit` | 40 多个 `attach(...)` 只留 6 个：`ResourcesUtils`、`CommonClassUtils`、`IconManager`、`StackedMobileIcon`、`HideCellularIcon`、`StatusBarIconTint`（见第四节：后三个缺一不可） |
| `MiuiHome#onInit` | 16 个 `attach(...)` 只留 2 个：`FolderAdaptIconSize`、`DisableIconAnim` |
| `IconDetailPage` | `IconTab.entries` → `listOf(IconTab.MOBILE)` |
| `MainPage` | 6 个入口 → 按功能分组的开关列表（双排信号图标 / 桌面文件夹 / 融合设备中心 / 安全服务 / 设备互联 / 息屏） |
| `scope.list` | 32 行 → 5 行 |

其余 190 个规则文件仍留在源码里，但既不在 `attach` 列表、也不在作用域，R8 会把它们连同依赖整片裁掉——
所以 release 包只有 6.4 MB，而 debug 是 42.7 MB。

### 4. v2 修的两个实机 bug（都出在裁剪过度）

第一版只挂了「图标本体」，把配色和隐藏原图标两条链路一起裁掉了，表现为：
深色壁纸下双排图标是纯黑的、系统原来的信号图标还和新图标并排显示。

| 症状 | 根因 | 补回 |
|---|---|---|
| 深色背景下图标全黑看不清 | 自绘图标是 `Bitmap.Config.ALPHA_8`（只有 alpha 通道，RGB 恒为 0），必须靠 SystemUI 的明暗 Tint 上色。`StatusBarIconTint` 挂了 `StatusBarIconView#updateLightDarkTint`，用 `DarkIconDispatcherExt#getTint` 拿到当前壁纸对应的颜色再 `setColorFilter`。它被裁掉后，位图就按黑色原样画出来了 | `StatusBarIconTint` |
| 系统原信号图标没隐藏，新旧两套并排 | 开启双排后要由 `HideCellularIcon` 把 `MobileIconViewModel#isVisible` 置成 `false`（否则 `mobile` / `mobile2` 槽位照旧显示），原实现里 `hideSimOne/hideSimTwo` 也默认跟随 `StackedMobile.ENABLED` | `HideCellularIcon` |
| （连带）图标位置靠左、不在信号位 | `IconManager` 负责把 `stacked_mobile_icon`、`stacked_mobile_type`、`single_mobile_sim1/2` 写进 `MiuiIconManagerUtils.RIGHT_BLOCK_LIST` 并按槽位顺序排布；缺了它，槽位只能靠 `setIcon` 现场创建，位置不对 | `IconManager` |

改完 `SystemUI` 里只剩 6 个 hooker，其中 `ResourcesUtils` / `CommonClassUtils` 是资源与类缓存基础设施。
注意这三条都是 `StaticHooker`，开关在 SystemUI 进程启动时读取，**改完要重启系统界面**（图标详情页右上角的重启按钮）。

## 三、我新增/改写的文件

新增（`app/src/main/java/cn/ianzb/hyperrefine/hook/`，共 19 个文件 / 约 1600 行）：

| 文件 | 说明 |
|---|---|
| `xposed/HfEntry.kt` | 新入口。按包路由 4 个 Load；跟随宿主总开关 `enable_module` |
| `xposed/HookApi.kt`、`xposed/Reflect.kt` | 原样搬（自包含的 hook 门面 + 反射工具） |
| `base/BaseHook.kt`、`base/BaseLoad.kt` | **重写的精简版**：砍掉 DexKit 会话、原生 hook、版本门禁、状态上报，只留「拿 ClassLoader → 挂 hook」 |
| `base/PackageTarget.kt` | 原样搬 |
| `prefs/HookPrefs.kt` | 改写：读 `config`、去前缀 |
| `systemui/SystemUiLoad.kt`、`connect/MiLinkLoad.kt` | 重写：各自只装 3 条 / 1 条 Hook |
| `systemui/PluginLoader.kt` | 原样搬（控制中心插件 ClassLoader 捕获，三个设备中心 Hook 都靠它） |
| `systemui/DeviceCenter*.kt`（3 个） | 原样搬 |
| `securitycenter/FastChargeNotifyHooks.kt` | **改写 DexKit 部分**：改成直接用 `DexKitBridge.create(...)` 查一次、用完即关，不搬上游 460 行的缓存基础设施 |
| `securitycenter/SecurityCenterLoad.kt`、`aod/*`（3 个）、`connect/ConnectKeys.kt`、`connect/MiPlayDelayHook.kt` | 原样搬 |

改动的宿主文件：

- `data/preference/Preferences.kt`：新增 `object HyperRefine`，7 个 `PreferenceKey`；
- `data/Pages.kt` / `app/MainActivity.kt`：v2 里把 `Route.HyperRefine` 与页面注册整体删掉了；
- `app/screen/MainPage.kt`：7 个开关按功能分组直接放在主界面（`SwitchPreference` 文案写死中文，没走 strings.xml）；
- `app/screen/systemui/icon/IconTunerPage.kt`：v3 里裁到只剩「图标顺序」一组（排序模式 / 自定义入口 / 自动重排），
  其余图标显隐、复合图标、左侧容器等分组删掉（对应 hooker 未挂载，留着就是死开关）；
- `app/screen/systemui/icon/detail/IconDetailPage.kt`：如上裁剪；
- `hook/HookEntry.kt`、`hook/scopes/SystemUI.kt`：如上裁剪 / 补回；
- `hook/rules/systemui/notif/FocusNotifWhitelist.kt`（v7 新增）：移除焦点通知白名单，
  挂 `NotificationSettingsManager#canShowFocusState` / `#canShowFocusStateApp`；
- `app/screen/MainPage.kt` 的 `MAIN_NOTIF` 分组（v7 新增）：上面那条开关的入口；
- `app/proguard-rules.pro`：`-keep` 掉新入口与两个基类名（否则入口会被混淆成 `er0`，
  虽然 `-adaptresourcefilecontents` 能自动改写 `java_init.list`，保留原名方便看日志）；
- `app/stableIds.txt` 从仓库根目录复制了一份（构建脚本里的 `--stable-ids` 是相对 `app/` 解析的）。

## 四、装之前

1. **先卸载官方 XiaomiHelper**（证书不同，覆盖安装会报错）；装完后要在 LSPosed 里启用，
   并勾选 **系统框架**、**系统界面**、**安全服务**、**设备互联**、**桌面** 五个作用域；
   想用「第三方应用」那两条，还要额外勾 **微信**、**支付宝**（v8 新增，默认不会自动勾）。
2. 设备的 HyperOS 版本要满足 XiaomiHelper 的门槛（`minSdk 35`，工具栏里有系统版本警告可忽略）。
3. AOD 那条必须在「系统框架」作用域里，否则 `AodBridgeLoad` 根本不会加载。
4. 双排信号的改动需要**重启系统界面**生效（`PluginLoader` 只在插件加载时捕获 ClassLoader，与 HyperRefine 一致）。

## 五、没验证的部分

我这边只有编译环境，没有机器可上，所以确认到的程度是：

- 两个入口都在 `java_init.list`，且入口类名未被混淆（已用 dexdump 确认 `HfEntry` 存在）；
- 7 个配置键都在 dex 里（`device_center_fast_load` … `aod_fullscreen_bridge` 各命中 1 次）；
- `scope.list` 七行、签名有效、`module.prop` api 101、单 dex、8.6 MB；
- v2 新增的 `StatusBarIconTint` / `HideCellularIcon` / `IconManager` 三个类都在 dex 里，
  主界面新旧文案（`MAIN_DEVICE_CENTER`…`融合设备中心`）已打包，旧的「HyperRefine 功能」页已消失；
- v3 的排序页链路在 `mapping.txt` 里能查到（`IconTunerPageKt` → `vu0`、`IconPositionPageKt` → `ku0` 等，
  R8 改了名但没裁掉），`ICON_TUNER_ORDER` 分组键也在；
- v4 的 `FolderAdaptIconSize` / `DisableIconAnim` 与其依赖的 `com.miui.home.folder.*` 六个类名都在 dex 里，
  `MAIN_LAUNCHER` 分组键在。
  （第一次编译时坑过一次：`HookEntry` 的 `when` 里没加 `Scope.MIUI_HOME`，整个桌面规则被 R8 当作不可达裁光，
  dex 里搜不到任何 `com.miui.home.*` 字符串——加分支后重新编译才正常。）
- v5 改动编译通过；`showExitFastChargeNotification`、`fast_charge_enter_notification`、
  `fast_charge_exit_notification` 三个 DexKit 特征串与两个开关键都还在 dex 里（没被 R8 裁掉）。
  两个 bug 的判定依据是源码与 DexKit 字节码，**不是**实机日志：`StringMatcher` 的默认
  `matchType` 是从 `dexkit-2.2.0-api.jar` 里 `javap -c` 读出来的 `StringMatchType.Contains`。

**没确认的**是运行期行为：7 个 Hook 在真机上是否都能命中（不同 HyperOS 版本类名/方法签名会变），
以及双排信号图标在新底座下渲染是否正常（它依赖 `ResourcesUtils` 与插件 ClassLoader，两者都保留了，
但只有上机才能确定）。v8 内置的两个第三方模块同理——编译期只能确认「签名对得上」，
真正 hook 有没有命中要看 `adb logcat -s LSPosed | grep -Ei "embedded|WxCompat|AlipaySpeed"`。

上机自检：

```bash
adb logcat -s LSPosed | grep -Ei "HyperRefine|AodDoze|DeviceCenter|MiPlay|FastCharge|embedded|WxCompat|AlipaySpeed"
```

日志里 `HyperRefine bridge loaded in <进程>, enabled=true` 说明第二个入口活了；
每条 Hook 成功会打 `xxx hook success @ <包名>`。

## 六、v5 修的三个 bug

### 1. 「隐藏快充加速」彻底不生效 —— DexKit 匹配模式写错

迁移时把上游的 `usingStrings(CLASS_MARKER)` 改写成了
`usingStrings(listOf(CLASS_MARKER), StringMatchType.Equals)`，多加了 `Equals`。
DexKit 的 `StringMatcher` **默认匹配模式是 `Contains`**（`StringMatcher` 的无参构造里
`matchType = StringMatchType.Contains`；`usingStrings(String...)` 走的正是这个默认值）。
改成 `Equals` 后要求整个字符串常量与标记完全相同，而实际 dex 里的常量往往带后缀，
于是 `findClass` 返回空 → `notify method not found` → 两条 hook 一条都没挂上。

改法：两处都去掉 `StringMatchType.Equals`，回到默认的 `Contains`。
这个 bug 只在「隐藏进入/退出快充加速」两条上发生，其它功能不走 DexKit 所以不受影响。

### 2. 开关在进程已经起来之后才打开 → 必须杀进程才生效

`BaseLoad` 原本是「安装时就按开关判断要不要挂」：开关为假就压根不注册 hook。
而「隐藏省略号」「缩小点击范围」「妙播低延迟」「快充通知」这四条的**回调里本来就会重新读开关**，
安装时再判一次纯属多余，代价是：目标进程（安全服务常驻）已经起来了，用户后打开开关，
hook 根本没装，只能手动杀掉安全服务才生效。

改法：这四条改成**无条件安装**，是否生效完全由回调里的实时判断决定——开关一改立刻起作用。
仍然按开关判断安装的只剩两条，因为它们回调里不看开关：
「加快图标加载」（只改写参数）和「全屏 AOD」（要改 system_server 显示状态）。

### 3. 开关「自己关回去」—— 配置服务还没连上就渲染 / 静默丢写

`XposedServiceManager.currentService` 在 App 刚启动时是 `null`，此时
`RemotePreferenceStore` 有两个致命行为：

| 旧行为 | 后果 |
|---|---|
| `get()` 直接返回 `key.default`（全 false） | 界面在第一帧就把所有开关画成「关」 |
| `put()` 走 `remotePrefs?.edit{}`，对象为 null 时整句不执行 | 用户点开关，看着是开了，实际一个字都没落盘；下次进来又变回关 |
| 每次 get/put 都新建 `getRemotePreferences()` 实例 | 连续快速切几个开关时，各自基于不同快照，后写的覆盖先写的 |
| `edit {}` 默认 `commit = false`（`apply()` 异步） | 切完立刻划掉后台，写入丢失 |

改法：
- 按 service 缓存同一个 `SharedPreferences` 实例；
- 读到 `null` 时回退到内存里的「最后已知值」，不再直接给默认值；
- 写不进去时进 `pendingWrites` 队列，service 绑定后统一 `commit()` 落盘；
- 一律 `commit = true` 同步写；
- service 绑定后发一次 `globalReloadEvent`，界面所有开关重读一遍真实值
  （为此给 `PreferenceActions` 加了 `preferenceReloads`，`rememberPreferenceState` 订阅它）。

另外给 `HookPrefs` 加了 500ms 节流的 `reload()` 反射调用，保证 hook 侧读到的不是进程启动那一刻的快照。

## 七、v6：退出提醒仍然弹出的修复

实机反馈：进入提醒没了，退出提醒还在。v5 已经修好了「完全没挂上」（DexKit 匹配模式），
所以这次是**挂上了但挂错了地方**。两个原因：

1. `usingStrings(marker)` 是 `Contains` 匹配，同一个 marker 在通知类里往往命中多个方法
   （渠道注册、标题拼接、真正发送都在同一个类里），而代码只取了 `.firstOrNull()`——
   进入/退出各取一个，很容易一个取对、一个取到无关方法。
2. 就算取对了方法，退出提醒的发送链路也可能根本不引用 `fast_charge_exit_notification`
   这个常量（比如复用同一个渠道，构建完再由别的方法统一 `notify`）。

改法分两层，不再赌方法名：

| 层 | 做法 |
|---|---|
| 方法级 | 命中 marker 的方法**全部** hook；特征类定位失败时退化为全 dex 搜索 marker |
| 发送级（新增） | hook `NotificationManager#notify` / `#notifyAsPackage`（只在安全服务进程内生效），按 `Notification#getChannelId()` 判定：渠道含 `fast_charge` 时按 `enter`/`exit` 分派到对应开关；渠道名看不出进出则任一开关打开即拦；渠道完全不匹配时回退到文案，标题/正文同时含「快充」与「退出」按退出提醒处理 |

这样无论安全服务把发送代码换成哪个混淆方法、放在哪个进程，只要最终走
`NotificationManager.notify` 就一定被拦。日志里能看到：

```
FastChargeNotifyGuard: installed on 4 method(s)
FastChargeNotifyGuard: seen channel=fast_charge_exit_notification -> key=... enabled=true
FastChargeNotifyGuard: blocked channel=fast_charge_exit_notification
FastChargeExitNotifyHook: hooked 2 method(s) [a, b]
```

- 有 `installed on 0 method(s)` → `NotificationManager` 反射没拿到方法，要把机型报我；
- 有 `seen ... enabled=false` 却已经打开了开关 → 是配置同步问题，不是 hook 问题；
- 一条 `seen` 都没有 → 说明这条通知不是安全服务发的，得再看是哪个包（大概率是
  `com.miui.powerkeeper`），那种情况要再加作用域。

## 八、v7：移除焦点通知白名单

新功能，主界面「通知」分组。焦点通知（超级岛 / 锁屏上的焦点卡片）默认只对系统白名单内的
应用开放，判定入口是 `com.miui.systemui.notification.NotificationSettingsManager` 上的：

- `canShowFocusState(pkg, channelId)`：这条通知能不能以焦点通知样式展示；
- `canShowFocusStateApp(pkg, channelId)`：这个应用有没有焦点通知能力。

打开开关后两个方法都无条件返回「允许」——按方法真实返回类型给 `1` 或 `true`，
避免不同版本上 `int` / `boolean` 不一致导致 `ClassCastException` 把系统界面搞崩。

配套：`Preferences.SystemUI.NotifCenter.DISABLE_FOCUS_WHITELIST`
（键名 `systemui_notif_focus_no_whitelist`）、`SystemUI#onInit` 里 `attach(FocusNotifWhitelist)`。
属于 StaticHooker，**改完要重启系统界面**。作用域不需要新增，仍用 `com.android.systemui`。

日志（tag 为 `FocusNotifWhitelist`）：

- `focus whitelist unlocked, hooked=2` → 两个方法都挂上了；
- `canShowFocusState / canShowFocusStateApp not found ...` → 这版系统界面换了类名或方法名，
  把这行日志给我，我按你机器的实际名字改。

## 九、v8：并入两个第三方模块（微信小程序兼容 / 支付宝极速模式）

### 两个包是什么

| 包 | 包名 | API | 作用域 | 干什么 |
|---|---|---|---|---|
| 微信小程序兼容 1.0.6 | `com.xici.wxcompat` | libxposed **101**（与本模块同代） | `com.tencent.mm` | 把微信 `CLIENT_VERSION` 伪装成 **8.0.79 / versionCode 3200 / 0x28004f30**，解决部分小程序提示「客户端版本过低」 |
| 支付宝极速模式 1.0.0 | `xiaojw.alipayspeed` | **`de.robv` 老 API** | `com.eg.android.AlipayGphone` | 伪造低端机信号（`isLowRamDevice`、电池温度、`Debug.MemoryInfo`、`SystemProperties`），让支付宝走自带的降级/省流逻辑 |

### 为什么不重写、而是「内置 dex + 运行时装载」

两个模块都没有源码、也没有 maven 坐标，只能拿到 dex。重写等于照着重写别人的 hook 逻辑，
而这类模块的目标全是**混淆类名/方法名**（`com.tencent.mm.sdk.platformtools.z` 这种），
照抄必然对不上你机器上的版本。所以改成：

1. 两个 dex 原样放进 `assets/modules/`（`wxcompat.dex`、`alipayspeed.dex`）；
2. 目标进程里用 `InMemoryDexClassLoader` 加载，**父 ClassLoader 是本模块的**，
   于是 `io.github.libxposed.*` 由框架提供、`de.robv.*` 由本模块的兼容桩提供；
3. 按包名调用原作者入口：
   - 微信：`WxCompatModule` 先 `attachFramework(this)`（把 libxposed 的 `XposedInterface` 接上，
     否则它的 `hook()` / `getFrameworkName()` 会抛「module not attached」），
     再喂 `onModuleLoaded` / `onPackageReady` 的**真实框架参数**；
   - 支付宝：`MainHook` 走 `initZygote` + `handleLoadPackage`，参数用本模块收到的
     `PackageReadyParam` 填 `LoadPackageParam`。

这样作者的 hook 逻辑一行没改，版本差异也由它自己处理。

### 兼容桩（`de.robv.android.xposed.*`）

支付宝那个用的是 2016 年的老 API，而本模块声明的是 libxposed 101，两者不能共存于同一个
模块描述里（`module.prop` 的 api 只有一份）。所以补了一层桥，把老 API 转发到 libxposed：

| 桩 | 说明 |
|---|---|
| `XC_MethodHook` / `XC_MethodHook$MethodHookParam` | `args`、`thisObject` 为**公开字段**（老 API 就是这么用的）；`getResult` / `setResult` / `hasThrowable` 齐全 |
| `XC_MethodReplacement` + `returnConstant` | 完全替换型回调 |
| `XposedBridge.hookMethod` / `hookAllMethods` / `log` | 转发到 `HookHelper.intercept` |
| `XposedHelpers.findClass` / `findMethodExact` / `findAndHookMethod` / `findAndHookConstructor` / `newInstance` | 反射查找 + 挂载 |
| `XC_LoadPackage$LoadPackageParam`、`IXposedHookLoadPackage`、`IXposedHookZygoteInit` | 入口形态 |

回调语义按老 API 还原：`before` → 若 `setResult` 则跳过原方法 → 否则带着（可能被改过的）参数执行
→ `after` → 期间可改写结果或吞掉异常。签名是逐条对着 dex 里的 method ref 核对过的
（`(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Lde/robv/android/xposed/XC_MethodHook$Unhook;` 等），
类与成员名靠 `-keep class de.robv.android.xposed.** { *; }` 保住——R8 看不出来外部 dex 在用它们。

### 配置与生效

- 主界面新增**「第三方应用」**分组，两个开关：`embed_wechat_compat`、`embed_alipay_speed`（默认都关）；
- hook 在目标进程启动时装，**改完开关必须重启微信 / 支付宝**，杀进程不够的是配置缓存那一层，
  正常划掉后台再打开就行；
- `scope.list` 增加两行：`com.tencent.mm`、`com.eg.android.AlipayGphone`。本模块是 `staticScope=true`，
  LSPosed 会按声明注入，但升级后作用域列表有时仍是旧快照——如果你在 LSPosed 里看到这两项没勾，手动勾上再重启手机；
- 支付宝那条会拦 `ActivityManager.isLowRamDevice`、电池温度、`SystemProperties` 等，
  等于在支付类 App 里伪造系统状态。**它能改变支付宝的降级判断，但不碰支付与风控逻辑**；
  如果支付宝因此弹出环境异常提示，把开关关掉即可，不会留下残留（hook 只在本进程内生效）。

日志（tag 为 `MiuixTemplate`）：

- `embedded WeChat module loaded: com.xici.wxcompat.WxCompatModule` → 微信侧装载成功；
- `embedded Alipay module loaded: xiaojw.alipayspeed.MainHook` → 支付宝侧装载成功；
- `embedded dex load failed: ...` → 读 assets 失败（通常是 `getModuleApplicationInfo()` 拿不到路径）；
- `embedded Alipay handleLoadPackage failed` → 载荷装上了但 hook 报错，把堆栈给我。

### 排查「微信 hook 不生效」

先看三件事，按顺序基本能定位到底卡在哪一层：

1. **LSPosed 作用域**。本模块 `staticScope=true`，作用域是安装时从 `scope.list` 读的快照。
   升级 APK 后如果 LSPosed 里「微信 / 支付宝」没打勾，模块根本不会被注入到目标进程，
   代码写得再对也没用。手动勾上、重启手机。
2. **开关 + 冷启动**。开关是通过 remote preferences 读的，装 hook 的时机是目标进程启动时，
   所以必须「强制停止微信」再打开（划掉后台不算强制停止）。
3. **日志**。v9 起每一步都写两份：logcat（tag `HyperRefineEmbed`）与框架日志（tag `MiuixTemplate`）。
   `adb logcat -s HyperRefineEmbed` 或者桌面 LSPosed → 日志页搜 `HyperRefineEmbed`。

日志与结论的对应关系：

| 日志 | 含义 |
|---|---|
| `wechat: switch off, skip` | 开关没开，或 remote preferences 没读到（键 `embed_wechat_compat`） |
| `apk path unresolved` / `dex read failed` | 拿不到本模块 APK 路径 / assets 读不出，内置 dex 根本没加载 |
| `not a dex` | assets 里的文件不是合法 dex（打包出错） |
| `wechat: module load failed` | dex 能读，但类加载 / 实例化失败，堆栈在下一行 |
| `wechat: onModuleLoaded failed` | 模块自身在 `onModuleLoaded` 里抛了（多为 `attachFramework` 没接上） |
| `wechat: module loaded ok` | 装载成功，接下来看 hook 点 |
| `wechat: probe setup=false device=false z=false baseReq=false ...` | **版本不匹配**：模块按固定混淆类名（`c64.xe` / `c64.bg` / `modelbase.i1` / `app.e5`）定位，微信一升级这些名字就变，模块会静默什么都不做 |
| `wechat: probe ... baseReq=true reqRespBase=true` | hook 点都在，装上了；此时仍无效就是被服务端别处拦，或跑在错误的进程 |
| `wechat: onPackageReady done in ... target=8.0.x/xxxx clientVersion=0x...` | 顺带把微信真实版本与协议版本号打出来，用来对照模块的 `targetClientVersion` |

v9 的改动（相对 v8）：

- **不再依赖回调顺序**：APK 路径、进程名都在真正要用的那一刻才解析，`getModuleApplicationInfo()`
  拿不到时用 `ActivityThread.getSystemContext()` + PackageManager 兜底；`ModuleLoadedParam` 缺失时本地造一个。
- **不再被宿主总开关和「第一个包」挡住**：内置模块有自己的开关，微信的 `:appbrand*` 等子进程也要装，
  原来 `if (!isEnabled || !param.isFirstPackage) return` 会把这两条路一起掐掉。
- **加了 `probeWeChat` 只读自检**，把「模块没加载」和「模块加载了但 hook 点没对上」区分开。

## 九点五、v10：真机实测定位到根因（api 102 没有 attachFramework）

前面 v8/v9 都只是「编译通过 + 静态校验」，v10 第一次上真机（小米 14 Pro / aurora，LSPosed 2.2.1，
微信 8.0.49 / versionCode 2600），一跑就抓到了确切成因。

### 现象

每一个微信进程都同一条：

```
E HyperRefineEmbed: java.lang.NoSuchMethodError: No virtual method
  attachFramework(Lio/github/libxposed/api/XposedInterface;)V
  in class Lio/github/libxposed/api/XposedModule; or its super classes
```

`dex ready: wxcompat.dex 32356 bytes` 已经打出来了 —— 说明 APK 路径、assets 读取、dex 校验全都正常，
卡在**把 XposedInterface 接给内置模块**这一步。模块连 `onModuleLoaded` 都没进，所以表现就是
「开关开了，微信毫无变化」。

### 根因

`attachFramework` 是 **libxposed api 101** 的写法。真机框架实际给的是 **api 102**：

```
WxCompat: module loaded, framework=LSPosed 2.2.1, api=102
```

api 102 里 `XposedModule` 仍然继承 `XposedInterfaceWrapper`，但 `attachFramework` 方法**已经没了**，
只剩内部字段 `mBase`。所以：

- 编译期按 101 编 → `invokevirtual XposedModule.attachFramework` 生成进 dex；
- 运行期 102 解析不到该方法 → `NoSuchMethodError` → 整个装载失败。

换个框架版本（api 101）又能跑 —— 这就是「代码没错但就是不生效」的典型来源。

### 修法

不再硬调方法，改成**结构探测**（[EmbeddedModules.attachCompat]），三级降级：

1. 继承链上找任意 `attachFramework / attach / setFramework / setBase / attachBase` 单参方法，
   参数类型能接受当前的 `XposedInterface` 就反射调用；
2. 找不到方法就找**字段**：继承链上任意能装下 `XposedInterface` 的实例字段，直接赋值
   （`attachFramework` 内部本来就是 `mBase = framework`，等价且跨版本稳定）；
3. 都失败则把 `XposedModule` 的真实方法/字段结构分块 dump 进日志，下一轮照实际签名接。

真机命中第 2 条：

```
I HyperRefineEmbed: wechat: attached via field io.github.libxposed.api.XposedInterfaceWrapper.mBase
I HyperRefineEmbed: wechat: module loaded ok (com.xici.wxcompat.WxCompatModule, process=com.tencent.mm)
```

### 实测结果（微信 8.0.49 / 2600，四个进程全覆盖）

```
com.tencent.mm            module loaded ok
com.tencent.mm:push       module loaded ok
com.tencent.mm:appbrand0  module loaded ok
com.tencent.mm:appbrand1  module loaded ok

probe setup=true, device=true, z=true, baseReq=true,
      setClientVersion=true, reqRespBase=true, fillBaseReq=true, platformZ=true

hooked getPackageInfo/2参数   (android.app.ApplicationPackageManager#getPackageInfo)
hooked c64.xe.b(int)
hooked c64.bg.setClientVersion(int)
生效配置: spoofVersionCode=true spoofClientVersion=true target=8.0.79/3200/0x28004f30
```

即：**混淆类名和微信 8.0.49 完全对得上，hook 已经真正装上**，不再是「静默什么都不做」。

### 两个附带结论

- **框架版本**：本机是 LSPosed 2.2.1 + libxposed api 102。模块自己按 api 101 编译，
  除 `attachFramework` 外其余接口在 102 下都兼容（宿主 7 个功能日志正常：`HyperRefine bridge loaded`、
  `z80 hook success @ com.milink.service`）。
- **独立版共存**：设备上仍装着 `com.xici.wxcompat`（微信小程序兼容独立版）。日志里每个进程只有一次
  `module loaded`，说明它当前没在作用域里生效；但为避免将来和内置版重复装 hook，建议在 LSPosed 里
  停用或直接卸载独立版。
- **配置开关**：模块会读微信私有目录下的 `wxcompat_config.txt`（本机已有，9 项）。
  其中 `spoofPackageInfo=false`：只伪装协议层版本，不动 `getPackageInfo`，是作者为了避开
  「开聊天窗口闪退」给的保守默认。若某个小程序仍然提示版本过低，把它改成 `true` 再强制停止微信重开。
  `logHookTrace=true` 会把微信每次探测 Xposed 包名（`a.b.c` / `io.va.exposed` …）的异常都打出来，
  日志会很吵，排查完可关掉。

---

## 十一、许可

- XiaomiHelper：GPL-3.0；`hyperx-compose`：Apache-2.0。
- HyperRefine：AGPL-3.0。AGPLv3 第 13 条允许与 GPLv3 代码结合，**结合后的整体按 AGPL-3.0 分发**。
- 这是给自己用的本地构建；如果要公开发布这个合并版，两个项目的许可与署名都得保留。
