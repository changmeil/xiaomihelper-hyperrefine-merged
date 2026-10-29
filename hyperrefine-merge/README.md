# 融为一炉：一个 APK

> 说明：本文记录的是「完整功能版」（`HyperRefine-1.14.0-merged-arm64-debug.apk`，18 MB，全部功能）。
> 现在实际使用的是只保留 8 个开关的精简版，见 **`LITE.md`** 与 `HyperRefine-1.14.0-lite-arm64-release.apk`（3.1 MB）。

产物就是这个目录里的 **`HyperRefine-1.14.0-merged-arm64-debug.apk`**（18 MB，arm64-v8a，debug 签名）。

它是 HyperRefine 1.14.0 的完整源码 + 两篇酷安模块的功能，编译通过。

---

## 一、先纠正一件事：你要的 5 条本来就在 HyperRefine 里

那份清单是从 HyperRefine 1.0.0 ~ 1.10.0 的历代更新日志里摘出来的，所以你在 1.14.0 的 Release
说明里当然找不到，但功能一个不少：

| 你要的 | 位置 | 首次引入 |
|---|---|---|
| 隐藏进入快充加速提醒 | 安全服务 → 快充加速通知（「进入提醒」） | 1.0.2 |
| 隐藏退出快充加速提醒 | 同上（「退出提醒」，与进入提醒是**两个独立开关**） | 1.0.2 |
| 加快图标加载 | 系统界面 → 融合设备中心 | 1.10.0 |
| 隐藏省略号 | 同上 | 1.0.1 |
| 缩小设备点击范围 | 同上（HyperOS 4 适配） | 1.5.0 |

这 5 条没改一行代码——原本就在，装完打开开关即可。

---

## 二、这次真正合进来的两个功能

### 1. 妙播音频低延迟（酷安 `feed/71444023` → MiPlayFix）

- 病因：`com.milink.service` 的 `MultiMirrorControl#setAudioPlayDelayTime(long, int)` 按 Wi-Fi 频段
  写死缓冲——5GHz 800ms、2.4GHz 1000ms（第二参数单位微秒）。
- 处理：新增 `MiPlayDelayHook`，执行前把第二参数改成 `50000`（50ms）。
- 位置：设备互联页 → 「妙播音频低延迟」。

### 2. 全屏 AOD 智能显示修复（酷安 `feed/72855373` → `silverpoetry/hyperos-full-aod-bridge`，MIT）

上一篇我没拿到正文，这次你给了仓库，原理就写在仓库的 `ARTICLE.md` 里：

强开全屏 AOD（机型 XML 补 `support_aod_fullscreen`）后，第一次锁屏正常，但智能显示因遮挡 / 暗光 /
无人注视关闭屏幕后再恢复，AOD 一直黑屏。传感器、注视判断、AOD 内容合成全都正常，系统也算出了新亮度，
卡在最后一步「把亮度写进物理面板」：

- 用户态显示库在 OFF→DOZE 时序里以 `power state pending / aod layer 0` 拒绝；
- 就算放行，N2 面板已进 LP1，内核 `is_backlight_set_skip()` 再拒一次。

第一次锁屏之所以正常，是因为它走的是 `ON → 写连续亮度 → DOZE`，亮度在进 LP1 前就生效了。

模块把同一个顺序搬到恢复边沿：`OFF → DOZE → 临时 NORMAL → 写最新亮度 → 等 16ms → DOZE`。
四个 Hook 点全在 system_server：`DozeBrightnessStrategyImpl#updateAodMode`（缓存全屏 AOD 状态）、
`DozeAutoBrightnessController#updateAutoBrightness`（缓存系统算出的亮度）、
`LocalDisplayAdapter$LocalDisplayDevice$N#setDisplayState`（记录真实 OFF→DOZE 边沿）、
同对象的 `setDisplayBrightness`（在边沿做一次修正）。纯事件驱动，事件之间完全空闲。

- 处理：新增 `AodBridgeKeys` / `AodBridgeLoad` / `AodDozeBridgeHook`，注册进 `HookEntryRegistry`。
- 位置：息屏页 → 「全屏 AOD 智能显示修复」。
- 前提：机型 XML 已开 `support_aod_fullscreen`。原模块验证环境是小米 14 Pro / Android 16 /
  `OS3.0.307.0.WNBCNXM`；这套类结构在 HyperOS 4 上没验证过，找不到类就只打日志、不抛异常。
- 许可：MIT，作者 silverpoetry。改动只有接入 HyperRefine 的 Hook 框架与开关。

### 3. 小米主题破解：仍然不做

付费主题 / 试用限制的绕过是破解付费内容，这条不参与，GitHub 上有也不并进来。

---

## 三、构建环境（都装在项目内，没动系统）

| 组件 | 版本 | 路径 |
|---|---|---|
| JDK | Temurin 25.0.4.1+1 | `.tools/jdk/jdk-25.0.4.1+1` |
| Gradle | 9.7.1 | `.tools/gradle/gradle-9.7.1` |
| Android SDK | platform `android-37.0`、build-tools 36.0.0 / 37.0.0、platform-tools | `.tools/android-sdk` |
| 源码树 | HyperRefine 1.14.0（已打补丁） | `build-hyperrefine/` |

HyperRefine 1.14.0 的真实要求是 **JDK 25 + Gradle 9.7.1 + AGP 9.4.1 + compileSdk 37**，
`minSdk 35`、`targetSdk 37`。

踩到的两个坑，记下来免得再撞：

1. **`platforms;android-37` 不存在**。Android 37 的 platform 现在按小版本发：`platforms;android-37.0`
   / `37.1` / `37.2`。装 `android-37` 会报 `Failed to find package`。
2. **Windows + Gradle 9.7 并发建 transform 锁文件会「拒绝访问」**。报错形如
   `...transforms\.internal\locks\xxx.lock (拒绝访问)`，配置阶段和 `checkDebugDuplicateClasses`
   阶段都可能炸。加 `--max-workers=1` 后一次过。

另外：JDK 用清华 Adoptium 镜像（2MB/s）、Gradle 用腾讯镜像拿的；Maven Central / Google Maven
直连可用，全量依赖约 600 MB。

可复现命令：

```bash
export JAVA_HOME="$PWD/.tools/jdk/jdk-25.0.4.1+1"
export ANDROID_HOME="$PWD/.tools/android-sdk"
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
cd build-hyperrefine
../.tools/gradle/gradle-9.7.1/bin/gradle assembleDebug --max-workers=1 --no-configuration-cache
```

---

## 四、安装

1. 需要 **LSPosed（libxposed API 102）+ Root**，按源码要求是 HyperOS 4 / Android 17。
2. **签名是 Android Debug**，和官方 Release 不同。之前装过官方 HyperRefine 的话必须先卸载
   （配置会丢），或者用你自己的 keystore 重签这个包。
3. LSPosed 作用域勾选：`系统框架(system)`（AOD 修复**必须**）、`com.android.systemui`、
   `com.miui.securitycenter`、`com.milink.service`、`com.xiaomi.mirror`。
4. 重启。AOD 那条还要确认机型 XML 里有 `support_aod_fullscreen`，并在设置里开着全屏 AOD。

---

## 五、这版验证到哪一步（别当成「已测可用」）

已确认：

- 编译成功：`BUILD SUCCESSFUL`，67 个 task；
- APK 里确实有新增内容：`MiPlayDelayHook` / `AodDozeBridgeHook` / `AodBridgeLoad` 三个类；
  `aod_full_screen_bridge`、`connect_miplay_delay` 等四个字符串资源；
  `MultiMirrorControl`、`setAudioPlayDelayTime`、`DozeBrightnessStrategyImpl`、`setDisplayPowerMode`
  这些目标符号；
- `META-INF/xposed/module.prop`：`minApiVersion=102 targetApiVersion=102`；
  `scope.list` 含 `system`；签名有效（Android Debug）。

**没验证**：真机运行。这里没有设备，也没有对应机型的 system_server 镜像，所以只能说
「能编译、能装、符号齐全」，不能说「功能真的生效」。上机后先看日志：

```bash
adb logcat -s LSPosed | grep -Ei "MiPlayDelayHook|AodDozeBridgeHook|hook success|hook failed"
```

---

## 六、文件

```
hyperrefine-merge/
├── HyperRefine-1.14.0-merged-arm64-debug.apk   ← 成品
├── README.md                                   ← 本文件
├── PATCH.md                                    ← 相对 1.14.0 的全部改动点
└── hook/                                       ← 新增的 4 个源文件
    ├── MiPlayDelayHook.kt
    ├── AodBridgeKeys.kt
    ├── AodBridgeLoad.kt
    └── AodDozeBridgeHook.kt
```

完整可构建的源码树在 `build-hyperrefine/`（已打补丁，可直接 `gradle assembleDebug` 复现）。
