# InkBoard

[![Android Release](https://github.com/kuaner/inkBoard/actions/workflows/android-release.yml/badge.svg)](https://github.com/kuaner/inkBoard/actions/workflows/android-release.yml)
[![GitHub Release](https://img.shields.io/github/v/release/kuaner/inkBoard)](https://github.com/kuaner/inkBoard/releases)

面向 Android 墨水屏平板的主屏幕。黑白、少动画、大触控区域，把常用应用和显示策略放在桌面上，而不是藏进层层菜单。

> 主要在 **云思智学 RK3566 墨水屏平板** 上开发与验证；其它墨水屏平板也可当作普通启动器使用（无 EPD 专项设置）。

包名：`ai.openduo.inkboard`

![InkBoard 主屏幕：座右铭、时间、系统状态与 8 个快捷方式](docs/images/01-home.png)

---

## 安装（给 AI Agent）

把下面整段复制给你的 Agent。不需要本机编译：从本仓库 **[GitHub Releases](../../releases)** 取最新 APK（CI 自动构建），用 `adb` 安装并完成首次配置。

电脑需已安装 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)（`adb`）。

```text
请为 InkBoard（Android 墨水屏主屏幕，包名 ai.openduo.inkboard）完成安装与首次配置。

总原则：
- 不要本地编译项目；使用 GitHub Release 的 APK（用户会提供 apk 路径，或请你从本仓库最新 Release 下载 assets 里的 .apk）
- 禁止 ./gradlew assemble / installDebug 等，除非用户明确要求
- 先引导用户打开 ADB，再执行 adb 命令；不要假设用户已经开过开发者选项

────────────────────────────────
A. 引导用户打开 USB 调试（ADB）
────────────────────────────────
云思智学平板不是「设置 → 关于本机 → 点版本号 → 开发者选项」那套通用路径。
请用中文逐步引导用户在平板上操作（每步等用户确认后再继续）：

1. 用数据线把平板接到电脑，并确认电脑已装好 adb
2. 打开系统「设置」
3. 进入「我的设备」
4. 连续点击「序列号」七次
5. 连续点击页面上的 logo 七次
6. 再连续点击「我的设备」三次
7. 完成后 ADB 会自动开启（一般无需再进开发者选项里手动开 USB 调试）

然后你在电脑执行：
   adb devices
应出现 device。若是 unauthorized，请用户在平板上点「允许 USB 调试」。
若仍 offline / 无设备，回到上面步骤核对，或换线 / 口后再试。

（装好 InkBoard 之后，也可在 App 内 MENU → USB 调试 开关 ADB，依赖云思/Yitoa 系统服务；其它机器可能无效。首次安装仍按上面手势打开。）

────────────────────────────────
B. 安装与首次配置（adb 命令）
────────────────────────────────
依次执行并核对输出：

1) 确认设备在线
   adb devices
   （应出现 device，而不是 unauthorized / offline）

2) 安装 APK（将 <APK> 换成实际文件路径）
   adb install -r <APK>
   （失败则根据报错处理签名冲突/卸载旧包后重试，并说明原因）

3) 允许修改系统设置（屏幕旋转依赖 WRITE_SETTINGS）
   adb shell appops set ai.openduo.inkboard WRITE_SETTINGS allow
   adb shell appops get ai.openduo.inkboard WRITE_SETTINGS
   （期望含 allow）
   说明：MENU 改方向会写 Settings.System.USER_ROTATION，并关闭 ACCELEROMETER_ROTATION。
   也可用平板：设置 → 应用 → InkBoard → 高级 → 允许修改系统设置。

4) 若为云思智学 RK3566 / S11A / EB1004P 一类设备，开启每应用 EPD 波形切换总开关
   （persist.modify.eink.mode 出厂多为 false；不开启则配置可写入但切应用不会真正换波形）
   adb shell setprop persist.modify.eink.mode true
   adb shell getprop persist.modify.eink.mode
   （期望 true）
   非云思墨水屏可跳过本步；App 内 EPD 入口会自动隐藏。
   InkBoard 不会在 App 内偷偷 setprop，必须用 adb 开一次。

5) 启动桌面
   adb shell am start -n ai.openduo.inkboard/.MainActivity
   默认主屏幕若无法用命令设置，请提示用户：系统设置 → 默认应用 → 主屏幕 → 选择 InkBoard。

6) 简要回报：adb 是否已引导开通、adb devices、install 结果、WRITE_SETTINGS、persist.modify.eink.mode。

注意：
- 不要用 setprop 伪造其它策略；EPD 业务配置由 App 写入 SystemUI Provider。
- 旋转依赖 WRITE_SETTINGS（USER_ROTATION + 关闭 ACCELEROMETER_ROTATION）。
```

---

## 它做什么

InkBoard 是一个 **HOME 启动器**：设为默认主屏幕后，按电源键或 Home 键回到这里。

桌面刻意做得很克制：

- 左侧是一句可编辑的读书座右铭  
- 右侧是大号时间、日期，以及内存 / 负载摘要  
- 下方是 **8 个固定快捷方式**，一点即开  

没有滚动列表、没有浮层弹窗、没有花哨动效，减少墨水屏残影和无效全刷。

## 界面速览

下面的截图按实际使用路径排列：从 HOME 进入 APPS 管理桌面，再从 MENU 调整通用选项；云思 S11A / EB1004P 设备会额外显示 SYSTEM EPD 页面。所有页面都采用手动翻页和大触控区域，适合墨水屏操作。

| 页面 | 入口 | 主要内容 |
|------|------|------|
| HOME | 默认主屏 | 座右铭、时间/日期、内存负载、8 个固定快捷方式 |
| APPS | 主页点 **APPS** 或长按槽位 | 管理槽位、添加应用、换图标、单独设置 EPD |
| MENU | 主页点 **MENU** | 屏幕旋转、桌面文字、快捷方式、ADB 与系统设置 |
| SYSTEM EPD | 主页点 **SYSTEM EPD**（仅云思系列） | 全局刷新策略与全刷阈值 |
| SENDER | 主页点 **SENDER** | 通过局域网网页传文件到平板 |

---

## 设备与兼容

### 参考机型：云思智学 RK3566 平板

本项目对照机大致信息如下（可随固件略有差异）：

| 项目 | 值 |
|------|-----|
| 品牌定位 | 云思智学墨水屏学习/办公平板 |
| 产品型号 | S11A |
| 内部代号 | EB1004P |
| SoC | Rockchip **RK3566**（平台 `rk356x`） |
| 系统 | Android 11 |
| 分辨率 | 1404 × 1872（竖屏物理像素；桌面以横屏为主） |
| 密度 | 260 dpi |
| 墨水栈 | `ro.vendor.eink=true`，SystemUI EPD Provider |

### 其它墨水屏平板

**可以用。** 安装后仍可当主屏幕：8 个快捷方式、座右铭、时间、SENDER 传文件、旋转/ADB 等通用能力都在。

**没有的是「EPD 专项设置」**（`SYSTEM EPD`、每个应用旁的 `EPD`、MENU 里的显示策略等）。  
这些入口只在识别为 **云思智学该系列平板**（机型 S11A / 设备 EB1004P 等，且存在系统 EPD Provider）时出现；其它机器会自动隐藏，避免点了却写不进系统。

EPD 能力是为云思固件里的「每应用刷新配置」做的，不是通用 Android API。

---

## 功能一览

### 主屏幕

| 入口 | 作用 |
|------|------|
| 点击快捷方式 | 启动应用或内置动作（清理后台、全刷屏幕、锁屏） |
| 长按快捷方式 / 点 **APPS** | 管理 8 个位置 |
| 点座右铭 | 修改主屏那句话 |
| **SYSTEM EPD** | 系统默认刷新策略（仅云思系列） |
| **SENDER** | 临时局域网传文件 |
| **MENU** | 旋转、显示、系统开关等 |

时间与内存/负载大约每分钟更新一次，不会高频刷新屏幕。

---

### 管理快捷方式（APPS）

![APPS 应用管理：左侧桌面槽位，右侧应用列表，底部可换图标或设置 EPD](docs/images/02-apps.png)

- 左侧：当前 8 个桌面位置
- 右侧：本机已安装应用，**手动翻页**，不用滑动列表
- 选中一个桌面位置后，可执行：**添加 / 移除 / 换图标 / 单独设置 EPD**（EPD 仅云思系列）
- 点底部 **图标** 可以只修改这个桌面槽位的图标；图标来自内置 Koboyo 单色素材库，不会改动原应用的系统图标
- 内置工具“全刷屏幕”也可以加入桌面快捷方式；普通应用则通过 **EPD** 进入该应用的独立显示设置
- Koboyo 图标素材已内置，离线可选，无需联网下载

---

### 菜单（MENU）

![MENU 设置：旋转、显示策略、桌面与系统操作](docs/images/03-menu.png)

- **屏幕旋转**：0° / 90° / 180° / 270° 一键切换
- **显示**（云思系列）：系统默认 EPD、InkBoard 自身显示策略
- **桌面**：改座右铭、管理快捷方式
- **系统**：USB 调试（ADB）、跳转 Android 系统设置

“全刷屏幕”不再放在 MENU 中：它会出现在 **APPS** 的内置工具中，可以像普通应用一样加入 8 个桌面位置。设为默认主屏后，从其它应用按 Home 回到 InkBoard，桌面会在显示稳定后自动执行一次全刷。

---

### 系统 EPD（仅云思智学系列）

![SYSTEM EPD：全局刷新策略与应用切换前的全刷阈值](docs/images/04-system-epd.png)

在云思固件上，InkBoard 会把刷新策略写入系统自带的每应用 EPD 表。应用进入前台后，由系统框架切换波形与全刷频率。

预置策略示例：

| 策略 | 适合 |
|------|------|
| 自动 | 交给系统平衡速度与残影 |
| 清晰 | 静态阅读、桌面，边缘更干净 |
| 灰阶 | 需要保留灰阶层次时 |
| 快速 | 滚动、操作更跟手，残影会多一些 |
| 黑白极速 | 纯黑白场景，响应最快 |

也可为 **单个应用** 单独设置刷新、DPI、对比度、浅色处理等（APPS → 应用旁 **EPD**）。

#### 为什么还要开一个系统开关？

云思固件里，**允许应用切换 EPD 波形** 受系统属性控制：

```text
persist.modify.eink.mode
```

- **出厂默认一般是 `false`**  
- 为 `false` 时：InkBoard 仍可把配置写进系统表，但应用切前台时 **不会真正改波形**  
- 为 `true` 时：前台应用切换才会按表里的配置生效  

**InkBoard 不会在 App 里偷偷 `setprop`。** 需要用 ADB 开一次（见上方安装说明）：

```bash
adb shell setprop persist.modify.eink.mode true
```

只做一次即可；和 InkBoard 的本地配置不是一回事。

---

### SENDER（局域网传文件）

![SENDER：在同一局域网内通过浏览器上传文件](docs/images/05-sender.png)

1. 主屏幕点 **SENDER**  
2. 电脑与平板同一网络，浏览器打开页上的地址（或扫码）  
3. 选择保存目录（默认 `Download/InkBoard`）  
4. 传文件或文件夹；大文件分片，目录结构可保留  

**返回 InkBoard 后服务立刻关闭**，不常驻后台。

---

## 自动构建与发布

[Android Release](.github/workflows/android-release.yml) 会在以下情况构建**签名** release APK：

- 推送 `v*` 版本标签：构建 APK、创建 GitHub Release，并附上 APK 与自动生成的发行说明
- 在 Actions 页面手动运行（`workflow_dispatch`）：只生成 release artifact，不创建 GitHub Release

推送到 `main` 不会触发发布构建。版本号以 `app/build.gradle.kts` 的 `versionName` 为准；打标签时必须形如 `v${versionName}`（例如 `v1.0.0`）。

仓库 Secrets（与 InkFlow 同一套 release 证书）：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 文档

| 文档 | 内容 |
|------|------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、状态流、如何加功能 |
| [docs/INKBOARD_UI.md](docs/INKBOARD_UI.md) | 黑白 UI 与墨水屏交互约定 |
| [docs/EPD_S11A.md](docs/EPD_S11A.md) | 云思 EPD Provider、波形与 `persist.modify.eink.mode` 实测 |

截图在 **`docs/images/`**。Release APK 由 GitHub Actions 自动构建。
