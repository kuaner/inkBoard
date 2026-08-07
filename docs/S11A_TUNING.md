# 云思智学 S11A / EB1004P 调优

最后更新：2026-08-07

本文记录 **设备级** 操作：开 ADB、手势导航、刷机 / Loader、提取和修改 `vendor`、修复 U-Boot 读取限制、提取 `boot.img` 并 root、修改待机与开关机画面、改 logo 分区。

与 InkBoard **屏幕刷新波形 / EPD Provider** 无关的内容放这里。

| 文档 | 管什么 |
|------|--------|
| **本文** | 刷机、Loader、`vendor` 提取/修改/刷回、U-Boot 读取修复、`boot.img` / root、standby / poweroff 图、logo 分区、ADB / 手势 |
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
| 锁屏 / 待机 | `/vendor/media/standby.png` | `1872×1404` **8-bit** 灰度 PNG |
| 充电待机 | `/vendor/media/standby_charge.png` | 同上 |
| 低电量待机 | `/vendor/media/standby_lowpower.png` | 同上 |
| 关机 | `/vendor/media/poweroff.png` | `1872×1404` RGBA PNG |
| 关机（无电源提示） | `/vendor/media/poweroff_nopower.png` | 同上 |

### 制作注意

- 设备存储方向是 **横向** `1872×1404`。竖屏源图需先转到该方向再导出；按竖屏原图直接写入容易 **左右镜像**。
- 三张待机图可统一成同一张内容（本机曾这样做）。
- 待机图保留 **8-bit 灰度**，不需要降为 1-bit；这样能保留源图的灰度细节，最终墨色仍由设备的 EPD/HWC 灰度处理决定。
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

## `vendor` 的提取、修改与刷回

### 本机的分区事实

本机的 `vendor` **不是 Loader/GPT 中可以按名称访问的独立分区**，而是 `super` 里的动态逻辑分区。当前 S11A / EB1004P 设备实测的 LP 元数据对应关系如下：

| 项目 | 数值 |
|------|------:|
| `super` 物理起点 | LBA `1931264` |
| `vendor` 相对 `super` 起点 | `2325880` 个扇区 |
| `vendor` 物理起点 | LBA `4257144` |
| `vendor` 长度 | `391304` 个扇区 |
| `vendor` 字节数 | `200347648` |
| `vendor` 最后一个物理扇区 | LBA `4648447` |

计算关系：

```text
1931264 + 2325880 = 4257144
391304 × 512 = 200347648
```

因此本机必须使用下面这种按物理 LBA 的方式：

```text
rkdeveloptool wl 4257144 vendor-edit.img
```

不要使用 `wlx vendor`：`ppt` 中没有名为 `vendor` 的物理分区。也不要把只包含 LP 头部的 `super` 备份、`vendor.new.dat.br` 或 transfer list 当作可刷入的 vendor 镜像。

这些 LBA 是**本机当前 LP 布局的实测值**。如果更换 OTA 版本、重新生成 `super` 或在别的设备上操作，必须重新解析该设备的 LP 元数据，不能照抄地址。

### 1. 从原厂 OTA 提取 raw vendor

OTA 中的 `vendor.new.dat.br` 不是 raw ext4 镜像，需要先 Brotli 解压，再用 transfer list 还原。工作区已有脚本可以一次解出各分区：

```bash
YSZX_ROOT="/path/to/yszx"
OTA_DIR="$YSZX_ROOT/OTA_S11A_V1.0.2.6_2022111001"

"$YSZX_ROOT/tools/unpack_ota.sh" "$OTA_DIR"
VENDOR="$YSZX_ROOT/extracted/images/vendor.img"

wc -c "$VENDOR"                 # 应为 200347648
file "$VENDOR"                  # raw ext4 vendor 镜像
shasum -a 256 "$VENDOR"
```

如果只需要解 vendor，也可以手动执行：

```bash
brotli -d -o vendor.new.dat "$OTA_DIR/vendor.new.dat.br"
python3 "$YSZX_ROOT/tools/sdat2img.py" \
  "$OTA_DIR/vendor.transfer.list" \
  vendor.new.dat vendor.img
```

OTA 解出的 `vendor.img` 是该 OTA 的原厂版本，不一定等于设备当前实时内容。做恢复时，优先保留设备实时 dump；只有确认 OTA 版本一致时才用 OTA vendor 恢复。

### 2. 从设备提取当前实时 vendor

这是刷写前最重要的备份。先进入 Loader，并确认设备真的被识别：

```bash
adb reboot loader

TOOL="$HOME/bin/rkdeveloptool"
"$TOOL" ld
"$TOOL" ppt
```

确认 `ld` 显示 Rockchip `Loader` 后，按本机已确认的范围读取：

```bash
VENDOR_LBA=4257144
VENDOR_SECTORS=391304
VENDOR_BYTES=200347648
BACKUP="$PWD/vendor.device.before-edit.img"

"$TOOL" rl "$VENDOR_LBA" "$VENDOR_SECTORS" "$BACKUP"
test "$(wc -c < "$BACKUP" | tr -d ' ')" -eq "$VENDOR_BYTES"
shasum -a 256 "$BACKUP"
```

如果设备还能启动且已有 root，也可以从动态映射设备读取；这条路径不需要进入 Loader：

```bash
adb exec-out su -c 'cat /dev/block/mapper/vendor' > vendor.device.adb.img
test "$(wc -c < vendor.device.adb.img | tr -d ' ')" -eq 200347648
shasum -a 256 vendor.device.adb.img
```

两种方式都应得到完整的 `200347648` 字节。若 Loader 读取结果出现大段 `0xCC`，或者文件后半段明显不是 ext4 数据，先按本文后面的 [U-Boot 读取限制与修复](#u-boot-读取限制与修复可选) 处理，不能拿损坏的 dump 继续修改或刷回。

### 3. 离线修改待机与关机图

设备上的 vendor 是 raw ext4 镜像。macOS 默认不能直接把 raw ext4 镜像挂载为可写文件系统，当前工作区使用 `e2fsprogs` 的 `debugfs` 离线修改；Linux 也可以把镜像 loop 挂载后修改，原理相同。

先复制一份工作镜像，不要在唯一备份上操作：

```bash
BASE="/path/to/vendor.device.before-edit.img"
EDITED="$PWD/vendor.edit.img"
cp "$BASE" "$EDITED"

# macOS Homebrew；Linux 如果 debugfs/e2fsck 已在 PATH 中，可直接写命令名
E2FS="/opt/homebrew/opt/e2fsprogs/sbin"
DEBUGFS="$E2FS/debugfs"
E2FSCK="$E2FS/e2fsck"
STANDBY_PNG="/path/to/standby.1872x1404.gray8.png"
POWEROFF_PNG="/path/to/poweroff.1872x1404.rgba.png"
POWEROFF_NOPOWER_PNG="/path/to/poweroff_nopower.1872x1404.rgba.png"
```

待机图是 `1872×1404` 的 8-bit 灰度 PNG；两张关机图是 `1872×1404` 的 RGBA PNG。设备存储方向仍是横向，若源素材按竖屏设计，需要先按本文前面的方向规则排版。想让文字位于竖屏画面底部时，要在旋转/排版后的横向图片中确定位置，不能直接把文字放在竖屏源图的像素底边后原样写入。

先检查原文件的大小、权限和 SELinux 扩展属性：

```bash
"$DEBUGFS" -R 'stat /media/standby.png' "$EDITED"
"$DEBUGFS" -R 'ea_list /media/standby.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/poweroff.png' "$EDITED"
"$DEBUGFS" -R 'ea_list /media/poweroff.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/poweroff_nopower.png' "$EDITED"
"$DEBUGFS" -R 'ea_list /media/poweroff_nopower.png' "$EDITED"
```

把三张旧待机图和两张旧关机图删除后再写入新图。删除旧文件是为了先释放原有 ext4 数据块；三张内容相同的待机图只保存一个 inode，再用硬链接指向它，避免 vendor 空间不足。两张关机图的语义不同，必须分别写入，不能互相硬链接：

```bash
"$DEBUGFS" -w -R 'rm /media/standby.png' "$EDITED"
"$DEBUGFS" -w -R 'rm /media/standby_charge.png' "$EDITED"
"$DEBUGFS" -w -R 'rm /media/standby_lowpower.png' "$EDITED"
"$DEBUGFS" -w -R 'rm /media/poweroff.png' "$EDITED"
"$DEBUGFS" -w -R 'rm /media/poweroff_nopower.png' "$EDITED"

"$DEBUGFS" -w -R "write $STANDBY_PNG /media/standby.png" "$EDITED"
"$DEBUGFS" -w -R \
  'ea_set /media/standby.png security.selinux u:object_r:vendor_file:s0' \
  "$EDITED"
"$DEBUGFS" -w -R \
  'link /media/standby.png /media/standby_charge.png' "$EDITED"
"$DEBUGFS" -w -R \
  'link /media/standby.png /media/standby_lowpower.png' "$EDITED"

"$DEBUGFS" -w -R "write $POWEROFF_PNG /media/poweroff.png" "$EDITED"
"$DEBUGFS" -w -R \
  'ea_set /media/poweroff.png security.selinux u:object_r:vendor_file:s0' \
  "$EDITED"
"$DEBUGFS" -w -R \
  "write $POWEROFF_NOPOWER_PNG /media/poweroff_nopower.png" "$EDITED"
"$DEBUGFS" -w -R \
  'ea_set /media/poweroff_nopower.png security.selinux u:object_r:vendor_file:s0' \
  "$EDITED"
```

检查结果应满足：

```bash
"$DEBUGFS" -R 'stat /media/standby.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/standby_charge.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/standby_lowpower.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/poweroff.png' "$EDITED"
"$DEBUGFS" -R 'stat /media/poweroff_nopower.png' "$EDITED"
"$E2FSCK" -fn "$EDITED"
```

五个文件应为 `0644`、`User: 0`、`Group: 0`，SELinux 属性为 `u:object_r:vendor_file:s0`。三张待机图的 `stat` 结果应使用同一个 inode、`Links: 3`；两张关机图应各自拥有独立 inode。`e2fsck -fn` 必须没有错误；如果报错，丢弃该工作镜像，回到备份重新制作。

用 `debugfs dump` 回读五个文件并逐字节比较：

```bash
"$DEBUGFS" -R \
  'dump /media/standby.png /tmp/standby.device-image.png' "$EDITED"
cmp "$STANDBY_PNG" /tmp/standby.device-image.png

"$DEBUGFS" -R \
  'dump /media/poweroff.png /tmp/poweroff.device-image.png' "$EDITED"
cmp "$POWEROFF_PNG" /tmp/poweroff.device-image.png

"$DEBUGFS" -R \
  'dump /media/poweroff_nopower.png /tmp/poweroff_nopower.device-image.png' \
  "$EDITED"
cmp "$POWEROFF_NOPOWER_PNG" /tmp/poweroff_nopower.device-image.png

test "$(wc -c < "$EDITED" | tr -d ' ')" -eq 200347648
shasum -a 256 "$EDITED"
```

`EDITED` 必须保持原 vendor 的完整长度，不能因为修改文件而 resize、重建文件系统或改变 `super` 的 LP 元数据。

### 4. 修改 HWC 二进制时的规则

如果修改的是 `/vendor/lib64/hw/hwcomposer.rk30board.so`，应优先做**同长度的原位替换**，不要删除后重新创建 HWC 文件。原厂 V1.0.2.6 vendor 中，该文件的已确认数据位置是：

```text
vendor 文件块：36175（每块 4096 字节）
镜像字节偏移：148172800
文件长度：132048 字节
```

只有在 `debugfs stat` 确认这是同一份 vendor 布局、文件长度也相同，并且替换前后都校验 HWC SHA-256 后，才可以在镜像副本上原位写入。偏移不是通用值，换版本必须重新定位，不能直接套用。

当前已验证的原厂 / 修改版 HWC 校验值：

```text
原厂：9187f252e870a1d80fead94321d6a6cc53e8704c719f823645c67b115f4eaca4
修改版：7c772ada5e306c30a52b9d25bf8ab36f5378585d28356134f7a0f10f7a5cf4d8
```

对这份 V1.0.2.6 原厂 vendor，可以用下面的脚本在**镜像副本**中做带前置校验的原位替换。它会拒绝长度不符、原厂 HWC hash 不符或 patch hash 不符的输入：

```bash
HWC_PATCH="/path/to/hwcomposer.rk30board.patched.so"

python3 - "$EDITED" "$HWC_PATCH" <<'PY'
import hashlib
import sys
from pathlib import Path

image = Path(sys.argv[1])
patch = Path(sys.argv[2]).read_bytes()
offset = 148172800
size = 132048
stock_sha256 = "9187f252e870a1d80fead94321d6a6cc53e8704c719f823645c67b115f4eaca4"
patch_sha256 = "7c772ada5e306c30a52b9d25bf8ab36f5378585d28356134f7a0f10f7a5cf4d8"

sha256 = lambda value: hashlib.sha256(value).hexdigest()
if len(patch) != size or sha256(patch) != patch_sha256:
    raise SystemExit("patched HWC size or SHA-256 does not match")

with image.open("r+b") as stream:
    stream.seek(offset)
    old = stream.read(size)
    if sha256(old) != stock_sha256:
        raise SystemExit("image does not contain the expected stock HWC")
    stream.seek(offset)
    stream.write(patch)
    stream.flush()

print("HWC replacement verified:", patch_sha256)
PY
```

如果使用的不是这份原厂 vendor，先用 `debugfs dump` 导出 HWC、确认文件 extent 和长度，再按实际位置改脚本中的 `offset`、`size` 和期望 hash；不要只因为文件路径相同就套用 `148172800`。

修改 HWC 后仍要执行 `e2fsck -fn`、完整镜像长度检查和刷后回读；HWC 修改和待机图修改可以放在同一个 vendor 镜像中，但任何一项验证失败都不要刷。

### 5. 刷入修改后的 vendor

刷写前先用原厂同版本 vendor 的首扇区验证物理地址。首扇区比较成功只证明地址/版本的基本一致性，不能替代完整备份：

```bash
TOOL="$HOME/bin/rkdeveloptool"
VENDOR_LBA=4257144
VENDOR_SECTORS=391304
VENDOR_BYTES=200347648
STOCK="/path/to/vendor.img"          # 同版本原厂 raw vendor
EDITED="/path/to/vendor.edit.img"
BACKUP="$PWD/vendor.device.before-edit.img"

test "$(wc -c < "$STOCK" | tr -d ' ')" -eq "$VENDOR_BYTES"
test "$(wc -c < "$EDITED" | tr -d ' ')" -eq "$VENDOR_BYTES"

"$TOOL" rl "$VENDOR_LBA" 1 /tmp/vendor.start.device.bin
dd if="$STOCK" of=/tmp/vendor.start.stock.bin bs=512 count=1
cmp /tmp/vendor.start.device.bin /tmp/vendor.start.stock.bin
```

`cmp` 没有输出且返回成功才继续。随后写入完整 vendor，并立即读回相同的 391304 个扇区：

```bash
shasum -a 256 "$EDITED"
"$TOOL" wl "$VENDOR_LBA" "$EDITED"

READBACK=/tmp/vendor.edit.readback.img
"$TOOL" rl "$VENDOR_LBA" "$VENDOR_SECTORS" "$READBACK"
cmp "$EDITED" "$READBACK"
```

整段 `cmp` 成功后才能让设备退出 Loader：

```bash
"$TOOL" rd
adb wait-for-device
adb shell getprop sys.boot_completed
```

如果写入失败、回读不一致或设备无法启动，先不要重复写同一个可疑镜像；重新进入 Loader，使用刷写前保存的 `BACKUP` 回滚。

### 6. 刷后验证与回滚

系统启动后检查三个待机文件的 inode、大小和 hash：

```bash
adb shell ls -li \
  /vendor/media/standby.png \
  /vendor/media/standby_charge.png \
  /vendor/media/standby_lowpower.png

adb shell sha256sum \
  /vendor/media/standby.png \
  /vendor/media/standby_charge.png \
  /vendor/media/standby_lowpower.png
```

有 root 时，还可以把设备文件回读到主机，与源 PNG 比较：

```bash
adb exec-out su -c 'cat /vendor/media/standby.png' > /tmp/standby.device.png
cmp "$PNG" /tmp/standby.device.png
```

回滚优先使用刷写前的设备实时备份：

```bash
"$TOOL" ld
"$TOOL" ppt
"$TOOL" wl 4257144 "$BACKUP"
"$TOOL" rl 4257144 391304 /tmp/vendor.rollback.readback.img
cmp "$BACKUP" /tmp/vendor.rollback.readback.img
"$TOOL" rd
```

没有设备实时备份时，只有在版本确认一致的情况下才使用 OTA 原厂 `vendor.img`。这个流程只写 `vendor` 对应的物理范围，不碰 `system`、`boot`、`odm`、`product`、`system_ext` 或 `userdata`。

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
