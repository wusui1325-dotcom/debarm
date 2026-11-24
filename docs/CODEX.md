# DebArm Codex 專屬版

這是 DebArm 的 **Codex 專屬版**（套件名 `com.wusper.debarm.codex`），介面只用來準備環境並啟動官方 Codex CLI。沒有通用的 `.deb` 安裝器，也不列出任意套件。通用 DebArm（`com.wusper.debarm`）仍可獨立安裝與使用。

## 官方來源（不是 .deb）

OpenAI **沒有**發行官方的 Codex CLI `.deb`。官方安裝方式是 [chatgpt.com/codex/install.sh](https://chatgpt.com/codex/install.sh)，預設從 [releases.openai.com/codex](https://releases.openai.com/codex) 取得 Linux ARM64 的 musl 產物，若無法使用再退回 GitHub Releases。

本版釘選並校驗：

| 項目 | 值 |
|---|---|
| 版本 | 0.172.0（標籤 `rust-v0.172.0`，2026-11-24 發布） |
| 產物 | `codex-aarch64-unknown-linux-musl.tar.gz` |
| 大小 | 127,043,934 bytes |
| SHA-256 | `0b99526c185a4425fe96a2214fb7b6a6a7f7c1471883b7afbebe97e15e83e79e` |
| 優先 URL | `https://releases.openai.com/codex/releases/0.172.0/codex-aarch64-unknown-linux-musl.tar.gz` |
| 備用 URL | `https://github.com/openai/codex/releases/download/rust-v0.172.0/codex-aarch64-unknown-linux-musl.tar.gz` |
| 授權 | Apache-2.0（上游 [openai/codex](https://github.com/openai/codex)） |

ChatGPT 桌面版 `chatgpt_arm64.deb` 是另一個 GUI 產品，體積約 500 MB，沒有 X11 無法在本 App 使用，**不會**被下載或安裝。

首次啟動時才下載官方壓縮檔並核對 SHA-256，因此 APK 本身保持精簡。

## 工作區與權限

- **工作區**在 App 私有目錄（`filesDir/workspace`），在 guest 裡掛成 `/workspace`，同時是 `HOME` 與 `CODEX_HOME` 的所在。
- **裝置儲存**需你自行授予「所有檔案存取」或寫入共用儲存的權限。通過後，`/sdcard` 與 `/storage` 會綁進 guest，讓內外儲存互聯。這是使用者同意的儲存權限，不是 root，也不能靜默安裝其他 App。
- 刪除 Debian 環境時會保留工作區。

## Codex 看到的「電腦」

guest 使用乾淨的 Linux 環境變數（不會漏出 `ANDROID_*`），主機名稱為 `debian`，以一般使用者 `user`（uid 1000）執行，並以 PRoot `-k` 讓 `uname -r` 看起來像 Debian 核心。網路沿用 Android 的連線（已寫入 `resolv.conf`）。套件由裝置 CPU 原生執行，與通用 DebArm 同一條已修復 exit 139 的 PRoot 路徑。

## 限制

- 仍不是真實 root 或完整 Linux 虛擬機；沒有 systemd，也沒有圖形桌面。
- Codex 能讀寫的範圍僅限本 App 的權限：私有工作區，以及你授予的共用儲存。
- 第一次需下載約 34 MB 的 Debian rootfs 與約 121 MB 的官方 Codex CLI，請保持 App 在前景並確認網路穩定。
- 請只在你信任的工作區使用，並以你自己的 OpenAI / ChatGPT 帳號登入官方 CLI。
