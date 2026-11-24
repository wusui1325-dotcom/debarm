# DebArm

在 **Android** 上、**不需 root**，把 **aarch64（arm64）的 `.deb`** 裝進本機 Debian 環境，並在內建終端裡**原生執行**。

這裡的「原生」是認真的：guest 裡的程式是 aarch64 ELF，由裝置的 Cortex / Snapdragon CPU 直接執行。DebArm **不會**用 QEMU 去模擬 x86 或 x86_64 的套件。若你丟進一個 `amd64` 的 `.deb`，App 會拒絕安裝，並明白告訴你原因。

> 第一版以命令列程式為主（`hello`、`ffmpeg`、`python3`、編譯器、伺服器 CLI 等）。帶完整圖形介面的 `.deb`（X11 / Wayland / Electron）目前做不到，請看下面的「限制」。

## 能做什麼

- 第一次開啟時下載並解壓 **Debian 13（trixie）aarch64** 最小環境（約 34 MB 下載，解壓後約 150–250 MB）。內建 `apt` / `dpkg`。
- 用系統檔案選擇器選 `.deb`，在平板上也可以把檔案拖進主畫面；或從其他 App 分享 / 開啟 `.deb`。
- 先讀套件的 `Architecture` 欄，只接受 `arm64` / `aarch64` / `all`。
- 在 rootfs 裡用 `dpkg -i` 安裝，缺依賴時再試 `apt-get install -f`。
- 內建終端（xterm.js + 真正的 PTY）：登入 Debian shell，直接跑剛裝好的指令。
- 列出已安裝套件、查看狀態與錯誤、刪除整個環境重來。

不需要 root，也不會改系統分割區。一切都在 App 自己的私有目錄裡。

## 架構：怎麼在沒有 root 的情況下跑 .deb

```
┌─────────────────────────────────────────────┐
│  DebArm（Kotlin，arm64-v8a）                │
│    檔案選擇 / 拖放 / 套件清單 / 狀態         │
│                      │                      │
│                      ▼                      │
│    內建終端  ←→  PTY（JNI）                 │
│                      │                      │
│                      ▼                      │
│    PRoot（獨立行程，ptrace）                 │
│      --root-id --kill-on-exit               │
│      -r <app filesDir>/rootfs               │
│                      │                      │
│                      ▼                      │
│    Debian trixie aarch64 rootfs             │
│      apt / dpkg / bash / 你裝的套件          │
│      二進制：aarch64 ELF，裝置 CPU 原生執行  │
└─────────────────────────────────────────────┘
```

Android 不讓一般 App 做真正的 `chroot`。DebArm 使用 [PRoot](https://proot-me.github.io/)：它用 `ptrace` 把 guest 行程的路徑翻譯到本機 rootfs，效果接近 chroot，但不需 root。

若未來某台裝置的核心開放了使用者命名空間（user namespace），那會是更接近真正隔離的路；目前多數 Android 廠商仍關掉這項功能，所以第一版以 **PRoot + Debian aarch64 minbase** 為可靠路徑。這不是虛擬機，也不是轉譯層。

PRoot 與 loader 放在 APK 的 `jniLibs`（保證可執行的 `nativeLibraryDir`）。Debian rootfs 在首次使用時下載，校驗 SHA-256 後解到 App 私有目錄。

`targetSdk` 刻意設為 **28**。從 Android 10 起，若 App 的 target 是 29 以上，SELinux（`untrusted_app_29`）會禁止在可寫的 `filesDir` 裡 `exec` 二進制——而 Debian 的 `bash` / `dpkg` 正好住在那裡。這支 App 以側載為主，不是給 Play 上架用的。請看「殘留風險」。

## 在裝置上安裝 APK

1. 用 Android 手機或平板（必須是 **arm64-v8a**，例如 Snapdragon 8 Gen 2）。
2. 允許「安裝未知應用程式」（來源是你用來開啟 APK 的那個 App，例如檔案管理員或瀏覽器）。
3. 安裝本倉 Releases 裡的 APK，或自行建置（見下方）。
4. 開啟 DebArm，點 **安裝 Debian 環境**，等待下載與解壓完成。請盡量保持 App 在前景。
5. 環境顯示「就緒」後，可以：
   - 點 **安裝 .deb** 選檔；或
   - 點 **開啟終端**，執行例如：

```sh
uname -m          # 應為 aarch64
apt-get update
apt-get install -y hello
hello
```

也可以先在電腦或裝置上下載一個官方 arm64 套件再匯入，例如 [hello](https://packages.debian.org/trixie/arm64/hello/download) 的 `_arm64.deb`。

## 限制（請先讀）

- **不是真實的 root。** 終端裡你看起來是 `root`（`--root-id`），但行程仍是 App 的普通 Android UID。不能載入核心模組、不能改系統分割區、不能取得裝置 root。
- **沒有 systemd。** 沒有以 PID 1 跑起來的 systemd，服務腳本多半不會依發行版的方式啟動。我們放了 `policy-rc.d` 避免安裝過程亂開背景服務。
- **特權埠口。** 綁定 1024 以下的埠口通常會失敗。
- **圖形介面。** 第一版不提供 X11 / Wayland / VNC 桌面。裝了帶 GUI 的套件，也不表示能在 Android 上「開視窗」。
- **只跑 arm64。** 不支援 `amd64` / `i386` / `armhf` 等非 aarch64 套件，也沒有內建 QEMU。
- **不是完整 Linux 虛擬機。** 硬體存取、部分 `/proc`、SysV IPC、其他行程看不見的隔離都與真正的 VM 或 kernel namespace 不同。I/O 會比真 chroot 慢（PRoot 的 ptrace 代價）。
- **背景執行。** Android 12 之後可能清理長時間的背景行程。請在前景使用終端；離開後工作階段可能被殺掉。
- **部分廠商的 SELinux / ptrace 政策** 可能讓 PRoot 失敗。若「安裝環境」在解壓後無法執行 `uname`，請把畫面錯誤留下來。
- **惡意套件。** 裝進環境的程式以本 App 的 UID 執行，能存取這個 App 能存取的東西。只安裝你信任的 `.deb`。

## 建置

需求：JDK 17、Android SDK（platform 35、build-tools、NDK 27、CMake 3.22.1）、約 2 GB 磁碟。

```sh
# 設定 SDK 路徑（不要把這份檔案提交進 git）
echo "sdk.dir=/path/to/Android/sdk" > local.properties

export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

建置產物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

可側載。Debug 簽名使用本機自動產生的 Android debug keystore，**本倉不包含任何簽名密鑰**。

未簽名的 release 可用 `./gradlew :app:assembleRelease`（若簽章設定為空，產出可能是 unsigned）。請自行用你的金鑰簽名；不要使用其他專案的金鑰。

重新抓取 PRoot 預先編譯檔（可選）：

```sh
./scripts/fetch-prebuilts.sh
```

Debian rootfs **不會**打進 APK，也請不要把 rootfs 二進制提交進 git。URL 與雜湊釘在 `app/src/main/java/com/wusper/debarm/core/RootfsCatalog.kt`。

## 授權

- DebArm 原始碼：[MIT](LICENSE)
- 隨附的 PRoot 等二進制與前端元件：見 [NOTICE](NOTICE)、[THIRD_PARTY.md](THIRD_PARTY.md) 與 [`licenses/`](licenses/)

## 為什麼會有這個專案

這是一個寫給只有 Android 裝置的人、可以帶著走的開源工具：讓 arm64 的 Debian 套件在手機和平板上真正跑起來，而不是做一個只能「打開」`.deb` 卻執行不了裡面 ELF 的解壓器。
