# 第三方组件与权利归属

本仓库的**主体**是 XiaomiHelper（GPL-3.0）与 HyperRefine（AGPL-3.0）的合并派生代码。
此外，`build-xh/app/src/main/assets/modules/` 下有两个**并非本仓库作者编写**的 dex，
它们是从公开发布的第三方 Xposed 模块 APK 中提取的编译产物，原样内置以便在运行时装载：

| 文件 | 来源 | 原包名 | 版本 | 作用 |
|---|---|---|---|---|
| `wxcompat.dex` | 微信小程序兼容 | `com.xici.wxcompat` | 1.0.6 | 伪装微信客户端版本，解决小程序提示版本过低 |
| `alipayspeed.dex` | 支付宝极速模式 | `xiaojw.alipayspeed` | 1.0.0 | 向支付宝伪造低端机信号，走降级省流逻辑 |

**声明：**

- 这两个 dex 的著作权归各自原作者所有，本仓库**不对其主张任何权利**，也未获得原作者书面授权。
- 收录目的仅为「在同一 APK 内运行时装载」的技术演示与个人备份，不含任何破解、去除授权或绕过付费的设计。
- 相应地，这两个 dex 与其打包进成品 APK 的部分，**不适用**本仓库的 GPL-3.0 许可。
- 若原作者认为不妥，提 issue 或联系仓库所有者，会立即移除。

`hyperrefine-merge/MERGE-XH.md` 里记录的、被**明确拒绝**并入的内容：HyperOS 主题破解模块
（伪造已购状态以免费使用付费主题）。该功能属绕过授权，本仓库不包含、也不打算包含。

## 其他依赖

| 项目 | 许可 | 用途 |
|---|---|---|
| [HowieHChen/XiaomiHelper](https://github.com/HowieHChen/XiaomiHelper) | GPL-3.0 | 底座 |
| [Ianzb/HyperRefine](https://github.com/Ianzb/HyperRefine) | AGPL-3.0 | 7 个功能的来源 |
| [libxposed/api](https://github.com/libxposed/api) | Apache-2.0 | Xposed 模块 API |
| [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) | Apache-2.0 | 运行时定位混淆方法 |
| [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) | Apache-2.0 | 界面组件 |
