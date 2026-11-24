# DebArm Codex 專屬版

這是 DebArm 的 **Codex 專屬版**（套件名 `com.wusper.debarm.codex`），介面只用來準備環境並啟動**官方 ChatGPT／Codex Linux 桌面版**。沒有通用的 `.deb` 安裝器，也不列出任意套件。通用 DebArm（`com.wusper.debarm`）仍可獨立安裝與使用。

## 官方來源

官方 Linux 桌面版見 [ChatGPT / Codex Linux app](https://learn.chatgpt.com/docs/linux/linux-app)。套件索引在 2026-11-24 仍只有下列版本（沒有更新的 SHA 可釘）：

| 項目 | 值 |
|---|---|
| 套件 | `chatgpt` 26.1120.41243 |
| 產物 | `chatgpt_26.1120.41243_arm64.deb` |
| 大小 | 486,783,954 bytes（約 464 MiB） |
| 解壓後 | 約 1,568,808 KB（約 1.5 GB） |
| SHA-256 | `72b8b9ff2daa7bc750fb2149cec753f5ca06caef2157592777a45475f4292f27` |
| 版本化 URL | `https://persistent.oaistatic.com/codex-app-prod/linux/deb/pool/main/c/chatgpt/chatgpt_26.1120.41243_arm64.deb` |
| 套件索引 | `https://persistent.oaistatic.com/codex-app-prod/linux/deb/dists/stable/main/binary-arm64/Packages` |

官方套件的啟動入口是 `/usr/bin/chatgpt`（符號連結到 `/usr/lib/chatgpt/codex-launcher`），真正的桌面執行檔是 `/usr/lib/chatgpt/ChatGPT`。桌面檔的 `Exec=chatgpt %U`。

首次準備時才下載並核對 SHA-256，因此 APK 本身保持精簡。請預留約 **4 GB** 可用空間（Debian 環境、Xvfb／VNC 顯示堆疊，以及官方套件本身）。

## 一鍵桌面視窗

「啟動官方桌面」會在 PRoot 的 Debian 環境裡啟動：

1. `Xvfb` 虛擬顯示器（`:1`，1280×800）
2. `openbox` 視窗管理員
3. `x11vnc`（只聽 `127.0.0.1:5900`）
4. `websockify` + noVNC（`127.0.0.1:6080`）
5. 官方 `/usr/bin/chatgpt`，並加上 Electron 在沒有真實桌面／GPU 時需要的參數（`--no-sandbox`、`--ozone-platform=x11`、`--disable-gpu` 等）

Android 端用 WebView 打開 `http://127.0.0.1:6080`。因為 PRoot 與 App 共用網路命名空間，guest 聽的本機埠就是裝置上的本機埠。頂列會回報顯示伺服器與 chatgpt 的啟動狀態；必要時可展開紀錄。

這是**軟體繪製的 X11**，不是裝置原生視窗，可能較慢或偶有閃爍。第一次進入官方 App 需要用你自己的 OpenAI／ChatGPT 帳號登入。官方程式若無法在 PRoot 裡活下來，視窗會停在紀錄頁，可改用「開啟終端（後援）」排查。終端是工作區登入 shell，**不再預設啟動 musl CLI**。

## 工作區與權限

- **工作區**在 App 私有目錄（`filesDir/workspace`），在 guest 裡掛成 `/workspace`，同時是 `HOME`、`XDG_*` 與 `CODEX_HOME` 的所在。
- **裝置儲存**需你自行授予「所有檔案存取」或寫入共用儲存的權限。通過後，`/sdcard` 與 `/storage` 會綁進 guest，讓內外儲存互聯。這是使用者同意的儲存權限，不是 root，也不能靜默安裝其他 App。
- 刪除 Debian 環境時會保留工作區。

## 程式看到的「電腦」

guest 使用乾淨的 Linux 環境變數（不會漏出 `ANDROID_*`），主機名稱為 `debian`，以一般使用者 `user`（uid 1000）執行，並以 PRoot `-k` 讓 `uname -r` 看起來像 Debian 核心，同時提供 `DISPLAY`。網路沿用 Android 的連線（已寫入 `resolv.conf`）。套件由裝置 CPU 原生執行，與通用 DebArm 同一條已修復 exit 139 的 PRoot 路徑。

## 限制

- 仍不是真實 root 或完整 Linux 虛擬機；沒有 systemd。
- 顯示走 Xvfb → VNC → noVNC，受限於軟體渲染與觸控當滑鼠用。
- ChatGPT 能讀寫的範圍僅限本 App 的權限：私有工作區，以及你授予的共用儲存。
- 第一次準備需下載約 34 MB 的 Debian rootfs、顯示堆疊，以及約 464 MB 的官方 .deb；請保持 App 在前景並確認網路穩定。
