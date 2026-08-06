# 云思智学 S11A / EB1004P 调优

最后更新：2026-08-06

本文记录 **设备级** 操作：开 ADB、手势导航、刷机 / Loader、修复 U-Boot 读取限制、提取 `boot.img` 并 root、修改待机与开关机画面、改 logo 分区。

与 InkBoard **屏幕刷新波形 / EPD Provider** 无关的内容放这里。

| 文档 | 管什么 |
|------|--------|
| **本文** | 刷机、Loader、U-Boot 读取修复、`boot.img` / root、standby / poweroff 图、logo 分区、ADB / 手势 |
| [EPD_S11A.md](EPD_S11A.md) | 刷新模式、SystemUI EPD Provider、`persist.modify.eink.mode` |

验证机：云思智学 **S11A**（内部代号 **EB1004P**）、RK3566、Android 11、物理分辨率 1404×1872。

多数步骤需要 **root / adb 可写**；写入分区前务必先备份。

---

## 设备速查

| 项目 | 值 |
|------|-----|
| 型号 | S11A |
| 内部代号 | EB1004P |
| SoC | Rockchip RK3566（`rk356x`） |
| 系统 | Android 11（SDK 30） |
| 分辨率 | 1404 × 1872（竖屏物理像素） |
| density | 260 dpi |
| Loader USB | Vid=`0x2207` Pid=`0x350a`（Rockchip，不是 Google Fastboot） |

---

## 开启 ADB

云思 **不是**「关于本机 → 连点版本号」那套通用路径。

1. 数据线连接电脑，电脑已装 `adb`
2. 设置 → **我的设备**
3. 连续点击 **序列号** 七次
4. 连续点击页面上的 **logo** 七次
5. 再连续点击 **我的设备** 三次
6. 完成后 ADB 一般自动开启

```bash
adb devices -l
```

状态应为 `device`。若为 `unauthorized`，在平板上点允许 USB 调试。

---

## EPD 波形总开关（与 InkBoard 配合）

仅影响「切应用是否换刷新波形」，细节见 [EPD_S11A.md](EPD_S11A.md)。出厂多为 `false`：

```bash
adb shell setprop persist.modify.eink.mode true
adb shell getprop persist.modify.eink.mode   # 期望 true
```

InkBoard **不会**在 App 内执行 `setprop`。

---

## 手势导航

```bash
adb shell cmd overlay enable-exclusive --user 0 --category com.android.internal.systemui.navbar.gestural
adb shell settings put secure navigation_mode 2
adb shell settings get secure navigation_mode   # 期望 2
```

之后：左右缘向内滑返回，底缘上滑回主页。

---

## 锁屏 / 待机 / 关机画面

固件从以下路径读图（本次实测）：

| 画面 | 路径 | 格式说明 |
|------|------|----------|
| 锁屏 / 待机 | `/vendor/media/standby.png` | `1872×1404` **1-bit** 灰度 PNG |
| 充电待机 | `/vendor/media/standby_charge.png` | 同上 |
| 低电量待机 | `/vendor/media/standby_lowpower.png` | 同上 |
| 关机 | `/vendor/media/poweroff.png` | `1872×1404` RGBA PNG |
| 关机（无电源提示） | `/vendor/media/poweroff_nopower.png` | 同上 |

### 制作注意

- 设备存储方向是 **横向** `1872×1404`。竖屏源图需先转到该方向再导出；按竖屏原图直接写入容易 **左右镜像**。
- 三张待机图可统一成同一张内容（本机曾这样做）。
- 本机 `/vendor` 常已满，**不能**简单 `cp` 覆盖大文件；需有可用写入手段（root 挂载、临时文件再替换等），写完务必回读校验。

### 导出（需 root）

```bash
adb exec-out su -c 'cat /vendor/media/standby.png' > standby.png
adb exec-out su -c 'cat /vendor/media/standby_charge.png' > standby_charge.png
adb exec-out su -c 'cat /vendor/media/standby_lowpower.png' > standby_lowpower.png
adb exec-out su -c 'cat /vendor/media/poweroff.png' > poweroff.png
adb exec-out su -c 'cat /vendor/media/poweroff_nopower.png' > poweroff_nopower.png
```

本仓库本地备份示例（勿提交密钥/私有素材到公开仓库时请自检）：

```text
firmware-dump/display-assets/
  standby_book_quote.png                         # 当前三张待机用的设备格式参考
  device-backup-before-book-standby/standby.png  # 原厂备份
  device-backup-before-book-standby/standby_charge.png
  device-backup-before-book-standby/standby_lowpower.png
```

---

## 开机 Logo（`logo` 分区）

最早开机画面走 **Rockchip logo 分区**，不是 `/vendor/media` 里的 PNG。

| 项目 | 值 |
|------|-----|
| 符号链接 | `/dev/block/by-name/logo` |
| 本机块设备 | `/dev/block/mmcblk2p14`（以机内 `by-name` 为准） |
| 体积 | **16 MiB** 完整镜像 |
| 格式 | 头 `RKEL`；条目 `GR04`；每份 `1872×1404` **4-bit**；共 **11** 份图像 |

### 规则

- **不能**把普通 PNG 直接 `dd` 进分区。
- 替换时应用 **完整 16 MiB** 的 `RKEL/GR04` 镜像，11 份图都要替换。
- 写前备份，写后回读比对 checksum。

### 备份 / 写入 / 恢复（需 root）

```bash
# 备份
adb exec-out su -c 'cat /dev/block/by-name/logo' > logo-original.img

# 写入已编码镜像（示例文件名）
adb push logo_apple_crisp_bold.img /data/local/tmp/logo.img
adb shell su -c 'dd if=/data/local/tmp/logo.img of=/dev/block/mmcblk2p14 bs=1M; sync'

# 回读核对
adb exec-out su -c 'cat /dev/block/by-name/logo' > logo-after.img
shasum -a 256 logo_apple_crisp_bold.img logo-after.img

# 恢复原厂
adb push logo-original.img /data/local/tmp/logo-original.img
adb shell su -c 'dd if=/data/local/tmp/logo-original.img of=/dev/block/mmcblk2p14 bs=1M; sync'
```

本地参考：

```text
firmware-dump/display-assets/
  device-backup-before-logo-apple/logo.img   # 原厂 16 MiB
  logo_apple_crisp_bold.img                  # 已验证可写版本（若你本地有）
```

---

## 进入 Rockchip Loader（刷机相关）

这是 **Rockchip Loader**，不是 Google Fastboot。不要用 `fastboot flash` 代替。

系统能进 adb 时：

```bash
adb reboot loader
rkdeveloptool ld
```

期望看到 `Vid=0x2207,Pid=0x350a` 与 `Loader`。

系统起不来时：关机 → 插数据线 → **按住物理 Home** 再上电 / 连接，尝试进 Loader。

先确认设备状态和分区表；`ppt` 的起始扇区、结束扇区才是后续读写依据：

```bash
rkdeveloptool ld
rkdeveloptool ppt
```

## 修改启动图前：刷入已经准备好的 `boot`

锁屏、待机、充电、低电量和关机画面都在系统启动后由 `/vendor/media` 等位置读取。要直接修改这些文件，先让 Android 获得 root；本项目已经准备好当前设备对应的原版 `boot` 和 Magisk 补丁版 `boot`。

在仓库根目录执行：

| 文件 | 用途 | 大小 | SHA-256 |
|------|------|------|---------|
| `firmware/s11a/boot.img` | 当前设备原版 `boot`，失败时恢复 | 32 MiB | `1c1f570651751550969ca8eeb14bd2c401b077898bd6d0d44913a6b6d7db5783` |
| `firmware/s11a/boot_patch.img` | 已打好 root 补丁的 `boot` | 32 MiB | `4a2658394569816e99374b707027e29acdcae69c6dac4f28cd53820775b14990` |

先核对文件，再写入 `boot` 分区：

```bash
shasum -a 256 firmware/s11a/boot.img firmware/s11a/boot_patch.img
wc -c firmware/s11a/boot.img firmware/s11a/boot_patch.img
file firmware/s11a/boot.img firmware/s11a/boot_patch.img

rkdeveloptool ld
rkdeveloptool ppt                 # 确认存在 boot，且分区足够容纳 32 MiB
rkdeveloptool wlx boot firmware/s11a/boot_patch.img
rkdeveloptool rd
```

第一次启动后验证：

```bash
adb wait-for-device
adb shell su -c id                 # 期望 uid=0(root)
```

如果刷入后无法启动，重新进入 Loader，刷回项目中的原版镜像：

```bash
rkdeveloptool ld
rkdeveloptool wlx boot firmware/s11a/boot.img
rkdeveloptool rd
```

如果当前 `rkdeveloptool` 不支持 `wlx`，使用 `ppt` 得到 `boot` 的起始扇区后执行 `wl 0x<boot_start_sector> firmware/s11a/boot_patch.img`；不要猜偏移，也不要把这两个 Android boot 镜像写入 `uboot` 分区。

root 成功后，再按本文的 `/vendor/media` 路径替换启动相关图片；开机 logo 仍是独立的 `logo` 分区。

---

## 与 InkBoard 的关系

| 操作 | 是否必须装 InkBoard |
|------|---------------------|
| 开 ADB / 手势 / 换待机图 / logo / Loader | **否**，纯设备侧 |
| SYSTEM EPD / 每应用 EPD UI | 是，且需 `persist.modify.eink.mode=true` |

InkBoard 只消费系统 EPD Provider；不负责改 `/vendor/media` 或 `logo` 分区。

---

## U-Boot 读取限制与修复（可选）

这一节只在需要重新提取分区、制作完整 dump 或验证旧备份时使用。当前项目已经带有可直接刷入的 `boot.img` 和 `boot_patch.img`，修改启动图不需要先修复 U-Boot。

### 问题来源

开发版原厂 U-Boot 的 RockUSB 读取代码包含一个 32 MB 读取上限：

```c
#define RKUSB_READ_LIMIT_ADDR (32 * 2048) /* 32MB */
```

在 `cmd/rockusb.c` 的 `rkusb_read_sector()` 中，命中上限时会直接返回填满 `0xCC` 的缓冲区：

```c
if ((blkstart + blkcnt) > RKUSB_READ_LIMIT_ADDR) {
    memset(buf, 0xcc, blkcnt * SECTOR_SIZE);
    return blkcnt;
}
```

所以 `rkdeveloptool rl` 可能显示成功，但读出的镜像后半段已经被 `0xCC` 替代，导致提取的 `boot.img` 损坏或无法启动。

### 修复方法

修复是对**匹配设备的 U-Boot 源码**做一个很小的改动，禁用这段人为读取限制。例如社区补丁把条件改成：

```diff
-if ((blkstart + blkcnt) > RKUSB_READ_LIMIT_ADDR) {
+if ((blkstart + blkcnt) > RKUSB_READ_LIMIT_ADDR && 0) {
```

然后使用 S11A / EB1004P 对应的厂商 SDK、原板级配置、ATF / OP-TEE / DTB 和原有 FIT / 签名流程重新构建 `uboot.img`。不要直接拿 PineNote 的 U-Boot 二进制替换 S11A 的 U-Boot；两者即使 SoC / 屏幕相近，板级配置和启动链仍可能不同。

构建出确认匹配的 U-Boot 分区镜像后，才通过 Loader 写入 `uboot`：

```bash
rkdeveloptool ld
rkdeveloptool ppt                 # 确认机型和 uboot 分区
rkdeveloptool wlx uboot uboot-patched.img
rkdeveloptool rd
```

写入后重新进入 Loader，再按 `ppt` 得到的实际 LBA 使用 `rkdeveloptool rl` 读取 `boot` 或其它分区，并检查文件大小、校验值和是否仍出现连续 `0xCC`。`uboot-patched.img` 不是 Android `boot.img`，也不是 Magisk 的 `boot_patch.img`；不要使用 `db` / `ul` 把它当作临时 Loader 下载。

### 修复出处

- [xboot/xrock README：RKUSB_READ_LIMIT_ADDR 与最小补丁](https://github.com/xboot/xrock/blob/master/README.md)：说明 32 MB dump 限制及 `&& 0` 的修改方式。
- [no_read_limit.patch](https://github.com/ilyakurdyukov/rk3528-tvbox/blob/main/armbian-patch/patch/u-boot/legacy/board_rk3528-tvbox/no_read_limit.patch)：可直接查看 `cmd/rockusb.c` 的完整补丁。
- [WhyCan 社区讨论](https://whycan.com/t_6979.html)：给出 `RKUSB_READ_LIMIT_ADDR`、`32MB` 和 `memset(buf, 0xcc, ...)` 的来源说明。
- [PINE64 PineNote Releases](https://pine64.org/documentation/PineNote/Releases/)：PineNote Android 11 SDK / 平台背景；它不是 S11A U-Boot 的直接刷机包。
