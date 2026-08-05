# S11A EPD 调查记录

最后核对：2026-08-05

这份文档记录 S11A（`EB1004P`）现场固件的实际行为，作为 InkBoard 后续 EPD 代码的依据。属性值是本次设备现场值，不应直接当成所有 Onyx/Yitoa 设备的固定默认值。

## 设备与显示链路

现场设备信息：

| 项目 | 值 |
| --- | --- |
| 型号 | S11A / EB1004P |
| Android SDK | 30 |
| 物理分辨率 | 1404 × 1872 |
| 当前横屏逻辑分辨率 | 1872 × 1404 |
| Android density | 260 |
| EPD 标志 | `ro.vendor.eink=true` |
| Binder 服务 | `eink`，接口 `android.os.IEinkManager` |

系统报告的 60Hz 是 Android SurfaceFlinger 的 VSYNC/合成时序，不是墨水屏波形的真实刷新频率。EPD 模式（DU、A2、DU4 等）决定的是驱动波形和残影/速度取舍，不能用 60Hz 推断墨水屏每秒完成 60 次全屏刷新。

当前系统色彩模式只有 `NATIVE (0)`。它不是普通 LCD 那种可在多个色彩空间之间切换的选项；浅色不清晰时，应优先检查 EPD 波形、对比度、浅色处理和全刷策略。

## 即时全刷

系统顶部导航栏的刷新按钮对应 `android.os.EinkManager.sendOneFullFrame()`，不是修改
`refresh_frequency` 的配置操作。S11A 的 SystemUI `NavigationBarFragment` 在按钮点击时
直接调用这个 Binder 方法，然后让导航栏自身重绘；InkBoard 的
`EpdSettingsRepository.requestFullRefresh()` 复用同一调用。

当 InkBoard 作为默认 HOME 从其它应用回到前台时，`MainActivity` 会等待桌面窗口稳定后
请求一次全刷，避免刷新到刚离开的应用画面。需要手动清残影时，也可以在 `APPS` 中选择
内置工具“全刷屏幕”，把它加入 8 个桌面位置；它与设置页使用同一调用链。

## 系统 EPD Provider

系统自带的“应用优化”对话框使用：

```text
content://com.android.systemui.eink/einksettings
content://com.android.systemui.eink/einksettingsupdate
```

Provider 实现是：

```text
com.android.systemui/.statusbar.phone.EinkSettingsProvider
```

`einksettings` 是普通的每应用表读写接口，表名为 `EinkSettings`。InkBoard 使用的字段如下：

| 字段 | 作用 |
| --- | --- |
| `package_name` | 应用包名 |
| `app_dpi` / `is_dpi_setting` | 应用 DPI 与是否启用覆盖 |
| `refresh_mode` / `is_refresh_setting` | EPD 波形与是否启用应用覆盖 |
| `refresh_frequency` | 自动全刷阈值，单位是局部刷新次数，不是时间 |
| `app_contrast` / `is_contrast_setting` | 对比度与是否启用应用覆盖 |
| `app_bleach_mode` | 应用浅色处理开关 |
| `app_bleach_text_plus` | 浅色处理的文字增强开关 |
| `app_bleach_icon_color` | 图标浅色过滤阈值 |
| `app_bleach_cover_color` | 封面浅色过滤阈值 |
| `app_bleach_bg_color` | 背景浅色过滤阈值 |
| `app_anim_filter` | 固件会读取，但在本机反编译出的执行路径中没有实际应用逻辑，暂不暴露 |

### DPI 固定档位

Android 系统“显示大小”使用 `DisplayDensityUtils` 根据本机原生密度计算档位。本机原生密度为 260，完整系统显示大小值为：

```text
小 220 · 默认 260 · 大 302 · 较大 346 · 最大 390
```

厂家 EPD 应用优化对话框的 DPI 滑杆从原生 260 开始（260–500），因此 InkBoard 的 EPD 页面采用与系统一致、且落在厂家 EPD 范围内的四个固定选项：

| 选项 | DPI | 说明 |
| --- | ---: | --- |
| 默认 | 260 | 系统原生显示大小，推荐起点 |
| 大 | 302 | 系统显示大小“大” |
| 较大 | 346 | 文字与控件更大 |
| 最大 | 390 | 最易读，但同屏内容最少 |

系统的“小（220）”没有放进 EPD 选择器：它低于厂家 EPD 对话框的原生起点，在墨水屏上会让文字和控件过小。已有历史自定义值不会因为升级 InkBoard 被自动改写，用户选择固定档位后才会切换。

### `einksettingsupdate` 不是普通查询

这个 URI 的 `query()` 有厂商特有的副作用：

1. 它从 `selectionArgs[0]` 读取包名；
2. 读取目标应用的完整行；
3. 根据该行更新 EPD 模式、对比度、全刷阈值、浅色处理和相关系统广播；
4. 再返回 Cursor。

因此 InkBoard 的调用必须保留：

```kotlin
resolver.query(
    SETTINGS_UPDATE_URI,
    null,
    "package_name = ?",
    arrayOf(packageName),
    null
)
```

这里的 `null projection` 是故意的：S11A 的 Provider 会按列名读取整行，如果只传 `package_name` 等少量列，反而会缺列。`adb shell content query` 在这个 Android 版本不能方便地传入 `selectionArgs`，直接查询 `einksettingsupdate` 出现 `NullPointerException: Attempt to read from null array`，是 shell 调用方式不完整，不代表 InkBoard 这段调用错误。

系统框架的 `RootWindowContainer` 会在前台应用进入稳定状态后自动查询这个 URI；如果应用没有记录，还会先在 `einksettings` 中插入包名。因此应用级 EPD 的正常链路是：

```text
InkBoard 编辑
    → 写入 SystemUI EinkSettings 行
    → 应用切到前台
    → Android framework 查询 einksettingsupdate
    → 厂商 EPD 参数落到当前应用
```

InkBoard 对自己（桌面）的设置在离开 EPD 页面后再主动执行一次 update 查询；这样调节过程中不会不断重建桌面窗口。

## InkBoard 中的“默认”与“应用独立”

这套固件的 Provider 本质上是“每应用一行”，没有一个可以由普通应用直接写入的全局 EPD 配置 API。因此 InkBoard 的 `DEFAULT` 是一层清晰的默认策略管理，不是假造一个系统服务：

| 入口 | 实际作用 | 是否影响其它应用 |
| --- | --- | --- |
| 主页 `DEFAULT`，或 `MENU → 显示 → 系统默认` | 编辑全局缺省刷新策略 | 只同步到标记为跟随默认的应用 |
| `MENU → 显示 → InkBoard` | 编辑 `ai.openduo.inkboard` 自己的 EPD 行 | 不影响其它应用 |
| `APPS` 中某个应用右侧的 `EPD` | 编辑该应用的独立 EPD 行 | 只影响该应用 |

InkBoard 用 DataStore 保存“哪些应用跟随默认”的名单；同时用 Provider 的内部控制行
`ai.openduo.inkboard.system-default` 保存当前默认策略。由于本固件没有可直接持久化的全局写入接口，切换默认策略时，InkBoard 会把刷新模式和全刷阈值同步到名单中的每个应用行。应用选择 `APP` 后从名单移除，之后不再被默认策略覆盖；选择 `DEFAULT` 则重新加入名单。DPI、对比度和浅色处理仍可以独立保存，不会因为刷新策略跟随默认而丢失。

这个实现的边界很重要：`DEFAULT` 是 InkBoard 对系统 Provider 能力的可靠封装，不是 `InkPolicy`，也不是一个后台常驻服务。应用真正进入前台时，仍由 Android framework / SystemUI 的原生链路应用对应 Provider 行。

## 刷新策略预置

界面不让用户直接组合“波形 × 全刷次数”，而提供五个可理解的档位。全刷阈值的准确含义是：每完成 N 次局部刷新后，自动执行 1 次全屏刷新；它是次数，不是秒数：

| 预置 | Provider 波形 | 全刷阈值 | 适合场景 | 取舍 |
| --- | --- | ---: | --- | --- |
| 自动 | `AUTO (0)` | 20 | 不想调参 | 交给系统平衡速度与残影 |
| 清晰 | `COMMON (7)` | 20 | 桌面、静态界面 | 边缘稳，残影少 |
| 灰阶 | `A2 抖动 (13)` | 30 | 阅读、含灰阶内容 | 保留灰阶，残影略多 |
| 快速 | `A2 (12)` | 50 | 滑动、频繁操作 | 响应快，残影明显 |
| 黑白极速 | `DU4 (15)` | 70 | 纯黑白快速操作 | 最激进，不适合灰阶 |

这些是当前 S11A 固件上的保守起始值，不宣称适用于所有 Onyx/Yitoa 机型。选择策略后仍要以实际阅读、翻页和残影为准；全刷阈值越小越容易清除残影，但全刷也越频繁。

## 当前固件的波形模式

SystemUI 当前“刷新设置”对话框使用的单值映射为：

| `refresh_mode` | 名称 | 说明 |
| ---: | --- | --- |
| 0 | AUTO | 系统自动策略 |
| 7 | COMMON | 普通/稳定模式 |
| 12 | A2 | 快速更新，残影更多 |
| 13 | A2 抖动 | 快速更新并保留抖动灰阶 |
| 14 | DU | 快速黑白更新 |
| 15 | DU4 | 更激进的快速黑白更新 |

### A2 抖动与 DU4 抖动能否同时开启

不能把它们作为两个同时选择的刷新模式。`refresh_mode` 是单值字段，所以：

- `13` 表示当前选择的是 A2 抖动；
- `15` 表示当前选择的是 DU4；
- 两者不是两个可叠加的 checkbox。

设备另有独立属性：

```text
persist.eink.du4.dither=true
```

它表示驱动侧的 DU4 dithering 参数处于开启状态，不等于当前波形同时选择了 DU4。现场状态是“`persist.sys.eink.mode=13`（A2 抖动）+ `persist.eink.du4.dither=true`（DU4 相关驱动参数开启）”，不能描述成 A2 抖动和 DU4 模式同时运行。

如果“同时开启”指的是这两个底层值同时存在，设备可以接受这种组合；但它仍然只有一个当前波形，即 `13`。`persist.eink.du4.dither` 是否参与当前非 DU4 波形，由厂商驱动决定，InkBoard 不把它当成第二个可叠加模式，也不在界面上伪造“ A2 + DU4 ”组合选项。

旧 InkPolicy 中的 `AUTO_DU4=23` 不是这套 SystemUI Provider 对话框展示的模式值；InkBoard 不再沿用那套旧映射，以本表为准。

## 全局属性与现场状态

本次调查读取到的关键属性（本次验证完成后）：

```text
ro.vendor.eink=true
persist.sys.eink.mode=13
persist.eink.du4.dither=true
persist.sys.refresh_skip_count=5
persist.vendor.fullmode_cnt=20
persist.modify.eink.mode=true
persist.vendor.app.bleach.enabled=0
persist.vendor.app.bleach.bg_color=0
persist.vendor.app.bleach.cover_color=0
persist.vendor.app.bleach.icon_color=0
```

`persist.vendor.fullmode_cnt` 才是本固件中与“多少次局部刷新后插入一次全刷”直接对应的阈值。它是次数，不是秒数；数值越小，残影更容易被清掉，但全刷更频繁。`persist.sys.refresh_skip_count` 是驱动/刷新调度的另一个参数，不能直接当作全刷间隔显示给用户。

亮度设置 `screen_brightness=37` 不是这块墨水屏的主要黑度/对比度旋钮。应用对比度最终走的是厂商 `persist.vendor.hwc.contrast_key`；系统默认时现场该属性为空。

## 为什么当前感觉“写了 EPD 但不生效”

S11A 的 Provider 对刷新模式有一个额外的系统开关：

```text
persist.modify.eink.mode
```

本机初始为 `false`。反编译的 `EinkSettingsProvider.query(einksettingsupdate)` 表明：当应用有 `is_refresh_setting=1` 时，只有这个开关为 `true` 才会调用 `EinkSettingsManager.setEinkMode()` 和更新 `persist.vendor.fullmode_cnt`。

现场用同一个 `ai.openduo.inkflow` 配置做了可恢复测试：

- 开关为 `false`，应用行是 `refresh_mode=0 / refresh_frequency=90`，切换后仍保持全局 mode 13；
- 临时改为 `true`，切换后变为 mode 0、fullmode count 90；
- 随后已将设备配置为 `true`，并保留全局默认的 `mode=13 / fullmode_cnt=20`；之后由系统框架负责在应用进入前台时应用每应用配置。

最终复核时，`ai.openduo.inkflow` 的 `AUTO/90` 档案实际切换为 `mode=0 / fullmode_cnt=90`；切回 InkBoard 的 `A2 抖动/20` 档案后实际恢复为 `mode=13 / fullmode_cnt=20`。这确认了 InkBoard 保存的配置已经由系统框架真正应用到驱动。

所以要区分两件事：

1. InkBoard 是否能写入每应用 EPD 配置：能，写的是系统原生 `einksettings` 表；
2. 当前固件是否允许应用切换刷新波形：这台设备现在允许，因为已开启上述全局开关。

换到其他同类设备时，需要由设备管理员执行一次系统开关：

```bash
adb shell setprop persist.modify.eink.mode true
```

这不是 InkPolicy，也不是 InkBoard 的本地配置；它是厂商系统的总开关。InkBoard 不在每次点击或每次应用启动时用 `setprop` 伪造策略，而是把配置写入原生 Provider，交给系统框架按前台应用应用。

## InkPolicy 的结束与新架构

旧 `inkpolicy/` 做了三件现在不需要的事：

- 常驻前台服务和左下角悬浮手势；
- 自己的 profile store；
- 通过 WindowManager 扩展和系统属性在后台恢复策略。

这些逻辑会制造第二套配置来源，也可能在应用切换时重复触发刷新。现在的唯一配置来源是 InkBoard → SystemUI Provider：

- EPD 编辑页直接读写系统表；
- InkBoard 自身在页面退出后执行一次系统 update 查询；
- 普通应用交给 Android framework 的前台应用切换流程；
- 不再安装、启动或恢复 `com.kuaner.inkpolicy`。

本次设备上的 `com.kuaner.inkpolicy` 已卸载，对应的旧 Provider 数据行也已清除；以后即使系统保留历史行，也不会再有 InkPolicy 进程读取它。
