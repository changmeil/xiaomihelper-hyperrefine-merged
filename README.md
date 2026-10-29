# XiaomiHelper 26.05 + HyperRefine（合并版）

把 [XiaomiHelper](https://github.com/HowieHChen/XiaomiHelper) 当底座，把
[HyperRefine](https://github.com/Ianzb/HyperRefine) 的 7 个功能作为**第二个 Xposed 入口**挂进去，
再以「assets 内置 dex + 运行时装载」的方式并入两个第三方 Xposed 模块。

- 源码：`build-xh/`
- 成品与说明：`hyperrefine-merge/`（详细合并记录见 [`MERGE-XH.md`](hyperrefine-merge/MERGE-XH.md)）
- 成品：`hyperrefine-merge/XiaomiHelper-26.05-merged-arm64-release.apk`

> 这是一个**个人自用**的合并派生工程，不是上游官方发行版。上游两个项目各自独立，本仓库只是把它们拼在一起。

## 功能

| 分组 | 内容 |
|---|---|
| 双排信号图标 | 单卡/双卡风格（MIUI / iOS / iOS27 / 自定义 SVG）、前景背景错误色透明度、缩放、间距、网络类型字体 |
| 图标顺序 | 排序模式（默认 / 互换移动网络与 WLAN / 自定义）+ 拖拽排序 + 自动重排 |
| 桌面文件夹 | 大文件夹内图标大小自适应（3x3 / 2x2 九宫格容器）、禁用文件夹点击缩放动画、禁用图标点击遮罩 |
| 通知 | 移除焦点通知白名单（任意应用都能以焦点通知 / 超级岛样式展示） |
| 融合设备中心 | 加快图标加载、隐藏省略号、缩小设备点击范围 |
| 安全服务 | 隐藏进入 / 退出快充加速提醒 |
| 设备互联 | 妙播音频低延迟 |
| 息屏 | 全屏 AOD 智能显示修复 |
| 第三方应用 | 微信小程序兼容（客户端版本伪装）、支付宝极速模式（伪造低端机信号） |

`scope.list`：`system`、`com.android.systemui`、`com.miui.securitycenter`、`com.milink.service`、
`com.miui.home`、`com.tencent.mm`、`com.eg.android.AlipayGphone`。

## 构建

需要 JDK 21、Android SDK（compileSdk 37）。

```bash
export JAVA_HOME=/path/to/jdk-21
export KEYSTORE_PATH=/path/to/your.keystore
export KEYSTORE_PWD=***        # 你自己的口令，源码里没有、也不该有
export KEY_ALIAS=***
export KEY_PWD=***

cd build-xh
./gradlew assembleRelease
```

`local.properties` 里的 `sdk.dir` 需要按本机改。

## 已验证 / 未验证

- 编译：`assembleRelease` 通过，R8 混淆 + 资源压缩开启，v2 签名校验通过。
- 真机（小米 14 Pro / HyperOS，LSPosed 2.2.1 / libxposed api 102，微信 8.0.49）：
  模块注入、双排信号、fast-charge 提醒隐藏、微信小程序兼容的 hook 安装均已实测。
- **未做**：各 HyperOS 版本的兼容性矩阵、支付宝极速模式的实机验证。

## 踩过的坑

`MERGE-XH.md` 里逐条记了，挑两个最值得看的：

1. **libxposed api 101 与 102 不兼容**：`attachFramework(XposedInterface)` 在 api 102 被删除，
   按 101 编译的调用会抛 `NoSuchMethodError`，表现为「模块完全不生效」。本工程改成反射按结构接入。
2. **Xposed 模块「静默不生效」的排查顺序**：先看 api 版本差，再看混淆类名是否随目标 App 升级变了。

## 许可

- 底座 XiaomiHelper：GPL-3.0
- 功能来源 HyperRefine：AGPL-3.0
- 本仓库是二者的合并派生作品，整体按 **GPL-3.0** 分发，源码随仓库提供。

第三方模块的来源与权利归属见 [`NOTICE.md`](NOTICE.md)。
