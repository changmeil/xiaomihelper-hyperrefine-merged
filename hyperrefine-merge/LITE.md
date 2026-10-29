# 精简版：只留你要的 7 个开关（3.0 MB）

产物：**`HyperRefine-1.14.0-lite-arm64-release.apk`** —— **3.0 MB**（最初 debug 版是 18 MB）
源码树：`build-lite/`
状态：`BUILD SUCCESSFUL`，R8 混淆 + 资源压缩已开启，签名验证通过。

签名用的是本机 debug 证书（`C=US, O=Android, CN=Android Debug`），与之前的 lite 包同一证书，
**可以直接覆盖安装，不用卸载**。

---

## 一、留下的开关

| 分区 | 开关 | 作用进程 |
|---|---|---|
| 融合设备中心 | 加快图标加载 | `com.android.systemui` |
| 融合设备中心 | 隐藏省略号 | `com.android.systemui` |
| 融合设备中心 | 缩小设备点击范围 | `com.android.systemui` |
| 安全服务 | 隐藏进入快充加速提醒 | `com.miui.securitycenter` |
| 安全服务 | 隐藏退出快充加速提醒 | `com.miui.securitycenter` |
| 设备互联 | 妙播音频低延迟 | `com.milink.service` |
| 息屏 | 全屏 AOD 智能显示修复 | `system` |

作用域（`META-INF/xposed/scope.list`）只有 4 行，与上表一致：
`system`、`com.android.systemui`、`com.miui.securitycenter`、`com.milink.service`。

## 二、已移除：双排信号图标

上一版加过的「状态栏 → 显示双排堆叠信号」，因为实机表现有问题，**这一版整块撤掉了**：

- `SystemUiLoad#onPackageLoaded` 不再安装 `DualSimSignalHook`，只剩 3 个融合设备中心 Hook；
- 功能页的「状态栏」分区、以及 `dualSimSpecs()` 取键 helper 一并删除，
  `featureSpecs()` 不再拼接 `statusBarFeatureSpecs()`；
- 已用 `dexdump` 反汇编核对：`SystemUiLoad.onPackageLoaded` 里 **`DualSimSignalHook` 出现 0 次、
  `dual_rows` 常量不在包内**，只有三个 `DeviceCenter*Hook` 的 `initHook` 调用。

> Hook 类 `DualSimSignalHook` 本身还留在 dex 里 —— 因为 `-keep class cn.ianzb.hyperrefine.hook.**` 是整体保留规则。
> 但没有地方实例化它，等于死代码，不占运行开销。要彻底删文件也可以，删 `hook/.../statusbar/DualSimSignalHook.kt`
> 后重编即可（注意 `StatusBarOptions.kt`、`IconSettingsActivity.kt` 还在引用 `StatusBarKeys`，别一起删）。

如果你之后想要 XiaomiHelper 那种「iOS 风格自绘信号图标」，那是另一条路：要移植
`CellularIconRenderEngine`（约 1500 行 + 6 套 SVG + 字体度量 + KavaRef/RemotePreferences 依赖），
并且必须上机调，我只能保证编译过、没法保证画得对。

## 三、体积：18 MB → 3.0 MB，是怎么省下来的

| | debug（最早那版） | release（这一版） |
|---|---|---|
| 安装包 | 18 MB | **3.0 MB** |
| 解压后 | 45.4 MB | 5.3 MB |
| dex 数量 | 27 个 | 1 个 |

省下来的不是「删功能」，是编译方式：

1. **R8 代码压缩**（`isMinifyEnabled = true`）：未引用的 Compose / AndroidX / Miuix / Haze / Material 图标全部剥掉，
   27 个 dex 合成 1 个。debug 包之所以大，就是因为上面这些**一行没删全打进去了**（Compose 运行时 + miuix 全家桶本身就有 30 MB 量级）。
2. **资源压缩**（`isShrinkResources = true`）：没被引用的 drawable / 字符串 / 布局连同 `resources.arsc` 一起丢弃。
3. `-keep class cn.ianzb.hyperrefine.hook.** { *; }` 这条规则保留了下来 —— hook 侧代码与框架按类名交互，
   不能被改名或裁剪，所以 hook 部分的体积没动，被压掉的是 UI 与他们依赖的库。

也就是说：**功能一个没少，只是不再把用不到的 UI 库塞进包里。**

## 四、AOD 修复：实机已确认正常

把移植版和上游 `silverpoetry/hyperos-full-aod-bridge` 的 `AodDozeBridge.java` 逐行对过，完全一致：

| 上游动作 | 本模块 `AodDozeBridgeHook` | 结论 |
|---|---|---|
| Hook `DozeBrightnessStrategyImpl#updateAodMode(int)`，proceed 后读 `mIsFullAod` | 同 | 一致 |
| Hook `DozeAutoBrightnessController#updateAutoBrightness()`，proceed 后读 `mScreenAutoBrightness` | 同 | 一致 |
| 用「2 个方法 + 4 个捕获字段」在 `LocalDisplayAdapter$LocalDisplayDevice$1..32` 里找请求类 | 同（同样 1..32 遍历同一判据） | 一致 |
| Hook `setDisplayState(int)`，proceed 后 `recordState` | 同 | 一致 |
| 边沿判定 `fullAod && oldState==OFF && state in (DOZE, DOZE_SUSPEND)` | 同 | 一致 |
| `setDisplayBrightness` 上：`setDisplayPowerMode(NORMAL)` → `proceed(b,b)` → `sleep 16ms` → `finally setPowerMode(DOZE)` | 同 | 一致 |
| 常量 OFF=1 / ON=2 / DOZE=3 / DOZE_SUSPEND=4；POWER_DOZE=1 / POWER_NORMAL=2 | 同 | 一致 |

唯一写法差异：上游用 `getInvoker(...).setType(ORIGIN)` 调 `SurfaceControl#setDisplayPowerMode`，
这里用反射 `Method.invoke` —— 该方法本身没被本模块 Hook，效果一样。

**实机结论：正常。** 上机自检命令：`adb logcat -s LSPosed | grep AodDoze`。

## 五、重新编译

```bash
cd build-lite
export JAVA_HOME=<jdk25> ANDROID_HOME=<sdk> GRADLE_USER_HOME=<gradle缓存>
export KEYSTORE_FILE=C:/Users/<你>/.android/debug.keystore \
       KEYSTORE_PASSWORD=android KEY_ALIAS=androiddebugkey KEY_PASSWORD=android
gradle assembleRelease --max-workers=1 --no-configuration-cache
```

- `--max-workers=1` 必须加：Windows + Gradle 9.7 并发建 `transforms/.internal/locks/*.lock` 会「拒绝访问」。
- 不传 `KEYSTORE_FILE` 时回落到自带的 `release.keystore`（密码见 `app/build.gradle.kts` 的环境变量）。
- `assembleDebug` 也能用，但出来的包是 18 MB，体积上没有优势。

要加/减功能：`HookEntryRegistry` 的 `loads` 决定装载哪些 Hook，`scope.list` 决定允许注入哪些包，
`featureSpecs()` + `FeaturesPage` 决定界面露出什么。三者要同时对得上，否则会出现「有开关但没生效」。
