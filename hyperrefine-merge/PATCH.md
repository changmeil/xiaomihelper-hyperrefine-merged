# 合入点（基于 HyperRefine 1.14.0）

共 **8 处编辑 + 4 个新文件**。锚点写的是「插在谁后面」，不依赖行号，版本小改也不会错位。
这些改动已经全部应用在 `../build-hyperrefine/`，那份树可直接 `gradle assembleDebug`。

---

## A. 妙播音频低延迟（MiPlayFix）

### A1 新文件

`hook/src/main/java/cn/ianzb/hyperrefine/hook/connect/MiPlayDelayHook.kt`
（内容见 `hook/MiPlayDelayHook.kt`）

### A2 `hook/src/main/java/cn/ianzb/hyperrefine/hook/connect/ConnectKeys.kt`

插在 `CLIPBOARD_SYNC_BOOST` 常量之后：

```kotlin
    /** 妙播（MiPlay）镜像音频延迟压缩。 */
    const val MIPLAY_DELAY = "connect_miplay_delay"
```

### A3 `hook/src/main/java/cn/ianzb/hyperrefine/hook/connect/MiLinkLoad.kt`

插在「跨设备剪贴板加速」那段 `if (loader != null && ...CLIPBOARD_PUBLISHER...) { ... }` 之后：

```kotlin
        // 妙播音频延迟：安装到含 MiPlay 镜像控制类的进程。
        if (loader != null && Reflect.findClassIfExists(MiPlayDelayHook.TARGET_CLASS, loader) != null) {
            initHook(MiPlayDelayHook(), HookPrefs.getBoolean(ConnectKeys.MIPLAY_DELAY, false))
        }
```

文件头已 import `Reflect` / `HookPrefs`，无需新增 import。

---

## B. 全屏 AOD 智能显示修复（hyperos-full-aod-bridge，MIT）

### B1 新文件（3 个）

- `hook/src/main/java/cn/ianzb/hyperrefine/hook/aod/AodBridgeKeys.kt`
- `hook/src/main/java/cn/ianzb/hyperrefine/hook/aod/AodDozeBridgeHook.kt`
- `hook/src/main/java/cn/ianzb/hyperrefine/hook/aod/AodBridgeLoad.kt`

内容见本目录 `hook/`。

### B2 `hook/src/main/java/cn/ianzb/hyperrefine/hook/base/HookEntryRegistry.kt`

```kotlin
import cn.ianzb.hyperrefine.hook.aod.AodBridgeLoad   // 加在 import 区
```

```kotlin
    private val loads: List<BaseLoad> = listOf(
        AodBridgeLoad(),        // ← 新增，必须是 listOf 的第一项之前/之内
        AssistantLoad(),
        ...
```

`system` 已在 `hook/src/main/resources/META-INF/xposed/scope.list` 中，无需改作用域清单。

---

## C. App 侧配置项与文案

### C1 `app/src/main/java/cn/ianzb/hyperrefine/ui/screen/features/FeaturesPage.kt`

四处：

```kotlin
import cn.ianzb.hyperrefine.hook.aod.AodBridgeKeys   // import 区，assistant 之前
```

```kotlin
const val KEY_AOD_FULL_BRIDGE = AodBridgeKeys.FULL_AOD_BRIDGE   // 跟在 KEY_AOD_FULL 之后
const val KEY_MIPLAY_DELAY = ConnectKeys.MIPLAY_DELAY           // 跟在 KEY_CLIPBOARD_SYNC_BOOST 之后
```

注册进全局索引（`specByKey` 区）：

```kotlin
    specByKey(specs, KEY_AOD_FULL_BRIDGE),   // 跟在 specByKey(specs, KEY_AOD_FULL) 之后
    specByKey(specs, KEY_MIPLAY_DELAY),      // 跟在 specByKey(specs, ConnectKeys.CLIPBOARD_SYNC_BOOST) 之后
```

`OptionSpec` 声明（AOD 桥接插在 `KEY_AOD_BOTTOM_ICONS` 那个 block 之前，妙播插在
`KEY_SR_ANIMATION_RADIUS` 那个 block 之前）：

```kotlin
        OptionSpec(
            key = KEY_AOD_FULL_BRIDGE,
            type = OptionType.SWITCH,
            titleRes = R.string.aod_full_screen_bridge,
            summaryRes = R.string.aod_full_screen_bridge_summary,
            defaultBoolean = false,
            showStatus = true,
        ),
```

```kotlin
        OptionSpec(
            key = KEY_MIPLAY_DELAY,
            type = OptionType.SWITCH,
            titleRes = R.string.connect_miplay_delay,
            summaryRes = R.string.connect_miplay_delay_summary,
            defaultBoolean = false,
            targetPackages = listOf("com.milink.service"),
            showStatus = true,
        ),
```

> AOD 桥接故意不写 `targetPackages`：它作用在 system_server，而 `system` 作用域本来就在
> `scope.list` 里，不必走「主动申请作用域」那条路（Passkey 的系统侧功能也是同样处理）。

### C2 `.../features/AodActivity.kt`

`aodSection` 的 `specs` 里追加：

```kotlin
                featureSpec(KEY_AOD_FULL_BRIDGE),
```

### C3 `.../features/DeviceConnectActivity.kt`

`featureSection` 的 `specs` 里，`featureSpec(ConnectKeys.CLIPBOARD_SYNC_BOOST)` 之后追加：

```kotlin
                    featureSpec(ConnectKeys.MIPLAY_DELAY),
```

### C4 字符串

`app/src/main/res/values/strings.xml`：

```xml
    <string name="aod_full_screen_bridge">全屏 AOD 智能显示修复</string>
    <string name="aod_full_screen_bridge_summary">强开全屏 AOD 后，智能显示熄灭再恢复时不再黑屏：在 OFF→DOZE 恢复边沿临时切 NORMAL 写入亮度再回 DOZE。需授予「系统框架」作用域并重启</string>
    <string name="connect_miplay_delay">妙播音频低延迟</string>
    <string name="connect_miplay_delay_summary">把小米妙播建流时的音频缓冲从 800ms/1000ms 压缩到 50ms，改善连接音箱、耳机流转时看视频的音画不同步</string>
```

`app/src/main/res/values-en/strings.xml`：

```xml
    <string name="aod_full_screen_bridge">Full AOD smart-display fix</string>
    <string name="aod_full_screen_bridge_summary">Fixes the black screen after smart display wakes a forced full-screen AOD: on the OFF to DOZE edge the panel briefly switches to NORMAL to commit brightness, then returns to DOZE. Requires the System Framework scope and a reboot</string>
    <string name="connect_miplay_delay">MiPlay low audio latency</string>
    <string name="connect_miplay_delay_summary">Cut the MiPlay audio buffer from 800ms/1000ms to 50ms to fix A/V desync when casting audio to speakers or earbuds</string>
```

---

## 构建

**必须加 `--max-workers=1`**，否则 Windows 上 Gradle 9.7 并发建 transform 锁文件会报
`...\transforms\.internal\locks\xxx.lock (拒绝访问)`。

```bash
export JAVA_HOME="$PWD/../.tools/jdk/jdk-25.0.4.1+1"
export ANDROID_HOME="$PWD/../.tools/android-sdk"
export GRADLE_USER_HOME="$PWD/../.tools/gradle-home"

gradle assembleDebug --max-workers=1 --no-configuration-cache
# 产物：app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 上机验证

```bash
# 1) Hook 是否装上
adb logcat -s LSPosed | grep -Ei "MiPlayDelayHook|AodDozeBridgeHook|hook success|hook failed"

# 2) 妙播延迟是否真的改到（连音箱放视频，看音画是否对齐）
adb shell dumpsys media.audio_flinger | head -40

# 3) AOD：全屏 AOD 开着的情况下，遮住屏幕让智能显示熄灭，再移开
#    能重新亮起即修复；仍黑屏则抓
adb shell dumpsys display | grep -i doze
```
