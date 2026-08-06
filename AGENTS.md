# InkBoard 项目协作说明

这份文件是项目根目录的 Agent / 协作者工作指引。改代码前先读本文，并保留工作区里已有的用户修改。

更细的架构与厂商细节见：

| 文档 | 内容 |
|------|------|
| [README.md](README.md) | 产品说明 + **给 Agent 的安装整段** |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块边界、状态流、如何加功能 |
| [docs/INKBOARD_UI.md](docs/INKBOARD_UI.md) | 黑白 UI / 墨水屏交互约定 |
| [docs/EPD_S11A.md](docs/EPD_S11A.md) | EPD 刷新模式、Provider、`persist.modify.eink.mode` |
| [docs/S11A_TUNING.md](docs/S11A_TUNING.md) | S11A 调优：ADB、Loader、待机/关机图、logo 分区 |

---

## 项目定位

InkBoard 是面向 **Android 墨水屏平板** 的 **HOME 启动器**（默认主屏幕）。

- 正式包名：`ai.openduo.inkboard`
- 仓库：https://github.com/kuaner/inkBoard
- 主要验证机：**云思智学 RK3566** 平板（型号 **S11A**，内部代号 **EB1004P**）
- 其它墨水屏也可当普通启动器用；**EPD 专项入口只在云思该系列 + 存在系统 EPD Provider 时出现**

桌面形态：左侧可编辑座右铭、右侧大号时间/日期与内存负载、下方 **8 个固定快捷方式**；黑白、少动画、大触控、手动翻页，避免残影与无效全刷。

---

## 本机 Android 工具链（这台 Mac）

执行 Gradle / adb 时直接用下列路径，不要反复搜索：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
```

`local.properties` 已被 `.gitignore` 忽略；本机可写：

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

**不要提交** `local.properties`、keystore、密码、签名材料或任意 `*.jks` / `*.keystore`。

---

## 包名与版本

| 项 | 值 |
|----|-----|
| `applicationId` / `namespace` | `ai.openduo.inkboard` |
| 版本字段位置 | `app/build.gradle.kts` 的 `versionCode` / `versionName` |
| 旧包名（已废弃） | `com.kuaner.inkboard` — 升级时需卸载旧包；若启用了设备管理员，须先停用再卸载 |
| 启动 Activity | `ai.openduo.inkboard/.MainActivity` |
| EPD 系统默认伪包名 | `ai.openduo.inkboard.system-default`（仅 Provider 行，不是真实应用） |

Kotlin 源码根目录：

```text
app/src/main/java/ai/openduo/inkboard/
```

---

## 常用构建与安装

在项目根目录 `/Users/kuaner/Documents/code/inkBoard`：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# Debug（日常改 UI / 功能）
./gradlew --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动
adb shell am start -n ai.openduo.inkboard/.MainActivity
```

**默认不要** 为「装设备」而本地编译。用户路径是 **GitHub Releases 的签名 APK**；只有用户明确要求改代码/调试时才用 `./gradlew`。

安装后建议核对：

```bash
adb shell appops set ai.openduo.inkboard WRITE_SETTINGS allow
adb shell setprop persist.modify.eink.mode true   # 仅云思系列需要
adb shell am start -n ai.openduo.inkboard/.MainActivity
```

默认主屏幕若无法用命令设置，提示用户：系统设置 → 默认应用 → 主屏幕 → InkBoard。

---

## Release 签名与发布

对齐 InkFlow 的发布方式。

### 证书（本机，勿提交）

| 项 | 值 |
|----|-----|
| Keystore 文件 | `~/Library/Application Support/InkFlow/release.jks`（与 InkFlow **共用** OpenDuo release 证书） |
| Keychain 服务名 | `InkFlow Android Release` |
| Keychain 账号 | `keystore-password`、`key-password` |
| key alias | `inkflow` |

读取密码示例（**不要打印到日志/对话**）：

```bash
security find-generic-password -s 'InkFlow Android Release' -a 'keystore-password' -w
security find-generic-password -s 'InkFlow Android Release' -a 'key-password' -w
```

### 本地签名构建环境变量

`app/build.gradle.kts` 仅在设置了 `INKBOARD_KEYSTORE_PATH` 时启用 release 签名：

```bash
export INKBOARD_KEYSTORE_PATH="$HOME/Library/Application Support/InkFlow/release.jks"
export INKBOARD_KEYSTORE_PASSWORD="$(security find-generic-password -s 'InkFlow Android Release' -a 'keystore-password' -w)"
export INKBOARD_KEY_ALIAS=inkflow
export INKBOARD_KEY_PASSWORD="$(security find-generic-password -s 'InkFlow Android Release' -a 'key-password' -w)"
./gradlew --no-daemon :app:assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

### GitHub Actions

工作流：[.github/workflows/android-release.yml](.github/workflows/android-release.yml)

| 触发 | 行为 |
|------|------|
| 推送标签 `v*` | 签名构建 + 上传 artifact + **创建 GitHub Release**（APK 名 `InkBoard-vX.Y.Z.apk`） |
| `workflow_dispatch` | 仅签名构建 + artifact，不发 Release |
| 推送 `main` | **不**触发发布构建 |

标签必须等于 `v` + `app/build.gradle.kts` 里的 `versionName`（例如版本 `1.0.0` → 标签 `v1.0.0`）。

仓库 Secrets（已配置，与 InkFlow 同名）：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

发布示例：

```bash
# 先改 app/build.gradle.kts 的 versionName / versionCode 并提交
git tag v1.0.1
git push origin v1.0.1
```

---

## 代码结构（速查）

```text
ai.openduo.inkboard
├── MainActivity.kt              入口，Compose 挂载
├── InkBoardApp.kt               Application：冷启动 bootstrap
├── LauncherViewModel.kt         唯一状态协调者
├── LauncherUiState.kt           只读 UI 状态
├── data/
│   ├── AppRepository.kt         应用发现 / 启动
│   ├── PreferencesRepository.kt DataStore（快捷方式、座右铭、EPD 能力缓存等）
│   ├── EpdCapability.kt         云思机型 + Provider 探测（一次探测，DataStore 缓存）
│   ├── EpdSettingsRepository.kt SystemUI EPD Provider 读写（未启用则 no-op）
│   ├── KoboyoIconRepository.kt  离线图标 catalog + assets SVG
│   ├── MonoIconCache.kt         单色图标缓存
│   └── HomeOrnaments.kt         主页装饰动物（assets）
├── ui/
│   ├── LauncherActions.kt       页面向 ViewModel 的意图
│   ├── home/                    主页、路由、座右铭编辑
│   ├── apps/                    8 槽位 + 应用列表
│   ├── icons/                   Koboyo 图标选择
│   ├── controls/                MENU：旋转、ADB、系统设置
│   ├── epd/                     SYSTEM EPD / 每应用 EPD
│   ├── sender/                  局域网传文件 UI
│   ├── components/              纸面框架、分页、无涟漪点击
│   └── theme/                   InkPaper / InkBlack 等
├── util/
│   ├── SystemControls.kt        旋转、ADB、锁屏、清理后台
│   ├── SenderServer.kt          临时 HTTP 上传服务
│   └── SystemMetrics.kt         内存 / 负载
└── admin/                       设备管理员（锁屏）；卸载前须先停用
```

依赖方向（不要逆流）：

- 页面 **只读** `LauncherUiState`，经 `LauncherActions` 发意图  
- 页面 **不** 直接碰 DataStore / PackageManager / EPD Provider  
- EPD 配置唯一链路：`EpdSettingsRepository → SystemUI Provider → framework`  
- 公共组件不依赖具体业务页  

---

## EPD 与设备能力（必记）

1. **产品线识别**：`S11A` / `EB1004P` 等 + 系统存在 EPD content provider；结果经 `EpdCapability` 探测一次并缓存（有 `PROBE_VERSION`）。  
2. **不要用**「装了某包名」或「泛 e-ink 属性」作为唯一开关。  
3. **`EpdSettingsRepository` 未启用时全部 no-op**；UI 入口收敛在 `EpdEntryPoints`，避免到处 `if`。  
4. 系统属性 **`persist.modify.eink.mode`**：出厂常为 `false`；为 `false` 时配置能写入但切应用 **不会换波形**。  
   - **App 内禁止偷偷 `setprop`**；用 adb 设一次即可。  
5. 旋转写 `Settings.System.USER_ROTATION` 并关 `ACCELEROMETER_ROTATION`，依赖 **`WRITE_SETTINGS`**。  

---

## UI / 墨水屏约束

- 纯黑白：`InkPaper` / `InkBlack` 为主，**避免灰色大块填充**（残影难看）。  
- 标题风格：`SYSTEM EPD.`、`MENU.` 等，不用含糊的 DEFAULT/CONTROL。  
- **无涟漪**、少动画、列表用 **手动翻页** 不靠惯性滑动。  
- 冷启动：快捷方式 key / 图标要尽早就绪（bootstrap + `MonoIconCache`），避免图标晚弹出。  
- Koboyo 图标与动物装饰已 **预置 assets**，运行时不要再 HTTP 下载。  
- 截图等产品图放在 **`docs/images/`**，不要堆仓库根目录。  

---

## 资产与脚本

| 路径 | 说明 |
|------|------|
| `app/src/main/assets/koboyo/` | 离线图标 catalog + SVG |
| `app/src/main/assets/home_ornaments/` | 主页装饰 SVG |
| `scripts/download_koboyo_icons.py` | 维护用：重新拉取图标素材（非常规运行路径） |
| `scripts/download_home_ornaments.py` | 维护用：装饰素材 |

日常改功能 **不要** 默认重跑下载脚本。

---

## 修改与验证规则

1. 改文件用补丁式编辑，避免覆盖用户未提交改动。  
2. Kotlin / Compose 改完至少：`./gradlew :app:assembleDebug`；涉及签名发布再 `:app:assembleRelease`。  
3. **没有**可靠的模拟器替代 S11A 真机 EPD；EPD 行为以真机为准。  
4. 真机安装优先用 **Release APK**；debug 与 release 包名相同，覆盖安装注意签名冲突。  
5. 旧包 `com.kuaner.inkboard` 若仍在且启用了设备管理员：  
   设置 → 应用信息 → 卸载 → **停用并卸载**，不能只靠 `adb uninstall`。  
6. 除非用户明确要求，**不要** `git commit` / `push` / 打 tag / 改 GitHub Secrets / 发 Release。  
7. 给「只会装 APK」的用户或 Agent 时：用 Release APK + `adb install` → `WRITE_SETTINGS` →（云思）`setprop`，**禁止**默认教 `./gradlew`。  

---

## 相关仓库

| 项目 | 说明 |
|------|------|
| [kuaner/inkBoard](https://github.com/kuaner/inkBoard) | 本仓库 |
| [kuaner/inkflow](https://github.com/kuaner/inkflow) | 同品牌墨水屏快讯摆件；**共用** release keystore 与 Secret 命名 |

---

## 快速检查清单

```bash
adb devices
adb shell pm path ai.openduo.inkboard
adb shell dumpsys package ai.openduo.inkboard | grep versionName
adb shell appops get ai.openduo.inkboard WRITE_SETTINGS
adb shell getprop persist.modify.eink.mode
adb shell am start -n ai.openduo.inkboard/.MainActivity
```
