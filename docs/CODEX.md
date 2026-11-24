# DebArm Codex 專屬版

這是 DebArm 的 **Codex 專屬版**（套件名 `com.wusper.debarm.codex`），介面只用來準備環境並啟動官方 Codex／ChatGPT。沒有通用的 `.deb` 安裝器，也不列出任意套件。通用 DebArm（`com.wusper.debarm`）仍可獨立安裝與使用。

## 官方來源

官方 Linux 桌面版是 [ChatGPT / Codex Linux app](https://learn.chatgpt.com/docs/linux/linux-app) 的 arm64 `.deb`（套件名 `chatgpt`）。目前釘定：

| 項目 | 值 |
|---|---|
| 套件 | `chatgpt` 26.1120.41243 |
| 產物 | `chatgpt_26.1120.41243_arm64.deb` |
| 大小 | 486,783,954 bytes |
| SHA-256 | `72b8b9ff2daa7bc750fb2149cec753f5ca06caef2157592777a45475f4292f27` |
| 版本化 URL | `https://persistent.oaistatic.com/codex-app-prod/linux/deb/pool/main/c/chatgpt/chatgpt_26.1120.41243_arm64.deb` |
| 套件索引 | `https://persistent.oaistatic.com/codex-app-prod/linux/deb/dists/stable/main/binary-arm64/Packages` |

在 Android／PRoot 上一鍵開啟這個桌面視窗（X11／VNC）**還沒出貨**。在此之前，本版改安裝並啟動官方**完整 CLI 套件**（與 [chatgpt.com/codex/install.sh](https://chatgpt.com/codex/install.sh) 同一份 `codex-package-*.tar.gz`，含 `codex-package.json` 與附屬資源），不是只解出單一 musl 執行檔。

本版釘選並校驗的 CLI 套件：

| 項目 | 值 |
|---|---|
| 版本 | 0.172.0（標籤 `rust-v0.172.0`，2026-11-24 發布） |
| 產物 | `codex-package-aarch64-unknown-linux-musl.tar.gz` |
| 大小 | 201,917,755 bytes |
| SHA-256 | `faf17b8071927f33346a805dc8cb45de4e08c0d75cf0e5e68cecddca28b8861e` |
| 優先 URL | `https://releases.openai.com/codex/releases/0.172.0/codex-package-aarch64-unknown-linux-musl.tar.gz` |
| 備用 URL | `https://github.com/openai/codex/releases/download/rust-v0.172.0/codex-package-aarch64-unknown-linux-musl.tar.gz` |
| 授權 | Apache-2.0（上游 [openai/codex](https://github.com/openai/codex)） |
| 安裝位置 | guest `/opt/codex`（`bin/codex` 旁有 `codex-package.json`） |

1.0.7 與更早版本只解出精簡的 `codex-aarch64-unknown-linux-musl.tar.gz` 單一執行檔。從 0.157 起，這種精簡安裝在啟動互動介面時會報「this CLI has no complete local package」。1.0.8 起會下載完整套件；若你還留著舊的精簡安裝，「啟動 Codex」會加上 `--no-daemon` 讓它先能用，並可再升級完整套件。即使已是完整套件，在 PRoot 裡啟動時仍會帶 `--no-daemon`，避免背景 app-server／bwrap 不穩定。這是過渡做法，正式目標仍是官方桌面 `.deb`。

首次啟動時才下載官方壓縮檔並核對 SHA-256，因此 APK 本身保持精簡。

## 工作區與權限

- **工作區**在 App 私有目錄（`filesDir/workspace`），在 guest 裡掛成 `/workspace`，同時是 `HOME` 與 `CODEX_HOME` 的所在。
- **裝置儲存**需你自行授予「所有檔案存取」或寫入共用儲存的權限。通過後，`/sdcard` 與 `/storage` 會綁進 guest，讓內外儲存互聯。這是使用者同意的儲存權限，不是 root，也不能靜默安裝其他 App。
- 刪除 Debian 環境時會保留工作區。

## Codex 看到的「電腦」

guest 使用乾淨的 Linux 環境變數（不會漏出 `ANDROID_*`），主機名稱為 `debian`，以一般使用者 `user`（uid 1000）執行，並以 PRoot `-k` 讓 `uname -r` 看起來像 Debian 核心。網路沿用 Android 的連線（已寫入 `resolv.conf`）。套件由裝置 CPU 原生執行，與通用 DebArm 同一條已修復 exit 139 的 PRoot 路徑。

## 限制

- 仍不是真實 root 或完整 Linux 虛擬機；沒有 systemd。官方桌面視窗尚未出貨。
- Codex 能讀寫的範圍僅限本 App 的權限：私有工作區，以及你授予的共用儲存。
- 第一次需下載約 34 MB 的 Debian rootfs 與約 193 MB 的官方完整 Codex CLI 套件，請保持 App 在前景並確認網路穩定。
- 請只在你信任的工作區使用，並以你自己的 OpenAI / ChatGPT 帳號登入官方 CLI。
