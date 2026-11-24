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
3. `x11vnc`（明確綁定 IPv4 `127.0.0.1:5900`，並加上 `-noshm`；未聽成功不會繼續）
4. `websockify` + noVNC（`127.0.0.1:6080`）
5. 官方 `/usr/bin/chatgpt`，並加上 Electron 在沒有真實桌面／GPU 時需要的參數（`--no-sandbox`、`--ozone-platform=x11`、`--disable-gpu` 等）

Android 端要等 **5900 與 6080 都在聽**、且 `vnc.html` 可取用後，才用 WebView 打開 `http://127.0.0.1:6080/vnc.html?autoconnect=true&reconnect=true&encrypt=0&host=127.0.0.1&port=6080&path=websockify`。因為 PRoot 與 App 共用網路命名空間，guest 聽的本機埠就是裝置上的本機埠。連線過程由 App 自己顯示簡短狀態，並注入樣式藏掉 noVNC 的「Connecting…」蓋層與左側工具列。官方 ChatGPT／Electron 在 PRoot 裡尚未登入時會寫出 D-Bus UPower、remote control authorization、Workspace routing、HTTP 432 等 stderr，這些只進紀錄，不會當成啟動失敗或擋住登入頁。連上後進入 App 沉浸式全螢幕（WebView 鋪滿，不呼叫瀏覽器 Fullscreen API）；左上角「選單」可退出全螢幕、看紀錄或開終端。Xvfb 解析度依裝置螢幕計算（最長邊最多 1920），openbox 會最大化官方視窗。啟動過程的狀態仍會寫進紀錄。

這是**軟體繪製的 X11**，不是裝置原生視窗，可能較慢或偶有閃爍。第一次進入官方 App 需要用你自己的 OpenAI／ChatGPT 帳號登入。登入（Continue to sign in）會透過 `xdg-open`／`BROWSER` 開啟環境內的瀏覽器。本版會在 rootfs 裡安裝**官方 Google Chrome arm64**（`google-chrome-stable` 158.0.8127.88-1，下載約 136 MB，安裝後約 450 MB，不打進 APK），以 wrapper 加上 `--no-sandbox`、`--disable-dev-shm-usage` 等旗標，並設為預設瀏覽器（避免 chatgpt.desktop 把 http(s) 搶走）。若官方 Chrome 無法安裝，會改用 Debian 的 `chromium`。已裝好官方桌面的環境，覆蓋安裝後第一次進入或點準備時會自動下載安裝，不必刪環境或重下載官方 ChatGPT 套件；請保持 App 在前景。畫面依裝置長寬比計算 Xvfb 解析度，並以等比縮放（contain，不裁切、不變形）鋪滿全螢幕。點到文字欄位時，App 會根據 Chrome／ChatGPT 的焦點（本機 CDP）自動彈出 Android 軟鍵盤，組字與送出的字會經 noVNC Unicode keysym 打進目前的 X11 視窗；點到非輸入區或收起鍵盤時會關掉。左上角「選單」有「鍵盤」可手動開關。觸控當滑鼠、沉浸式全螢幕與等比鋪滿維持不變。瀏覽器 cookies 與登入狀態存在工作區的 `--user-data-dir`：`/workspace/.config/google-chrome`（官方 Chrome）或 `/workspace/.config/chromium`（後備），對應 App 私有目錄 `filesDir/workspace`，殺掉 App 再開官方桌面仍會保留；刪除 Debian 環境但保留工作區時也會留下。結束工作階段時會先對瀏覽器送 SIGTERM，讓它有機會把 cookies 寫盤。官方程式若無法在 PRoot 裡活下來，視窗會停在紀錄頁，可改用「開啟終端（後援）」排查。終端是工作區登入 shell，**不再預設啟動 musl CLI**。

## 工作區與權限

- **工作區**在 App 私有目錄（`filesDir/workspace`），在 guest 裡掛成 `/workspace`，同時是 `HOME`、`XDG_*` 與 `CODEX_HOME` 的所在。官方 Chrome 設定檔與 cookies 在 `/workspace/.config/google-chrome`；後備 Chromium 在 `/workspace/.config/chromium`。
- **裝置儲存**需你自行授予「所有檔案存取」或寫入共用儲存的權限。通過後，`/sdcard` 與 `/storage` 會綁進 guest，讓內外儲存互聯。這是使用者同意的儲存權限，不是 root，也不能靜默安裝其他 App。
- 刪除 Debian 環境時會保留工作區。

## 程式看到的「電腦」

guest 使用乾淨的 Linux 環境變數（不會漏出 `ANDROID_*`），主機名稱為 `debian`，以一般使用者 `user`（uid 1000）執行，並以 PRoot `-k` 讓 `uname -r` 看起來像 Debian 核心，同時提供 `DISPLAY`。網路沿用 Android 的連線（已寫入 `resolv.conf`）。套件由裝置 CPU 原生執行，與通用 DebArm 同一條已修復 exit 139 的 PRoot 路徑。

## 限制

- 仍不是真實 root 或完整 Linux 虛擬機；沒有 systemd。
- 顯示走 Xvfb → VNC → noVNC，受限於軟體渲染與觸控當滑鼠用。
- ChatGPT 能讀寫的範圍僅限本 App 的權限：私有工作區，以及你授予的共用儲存。
- 第一次準備需下載約 34 MB 的 Debian rootfs、顯示堆疊，以及約 464 MB 的官方 .deb；請保持 App 在前景並確認網路穩定。
