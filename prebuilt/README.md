# prebuilt

這裡放的是 **PRoot 主程式與其 loader、libtalloc、libandroid-shmem** 的 aarch64 Android 二進制，供 App 在執行時 `exec` 進入 Debian rootfs。

這些檔案不是 DebArm 自己編譯的核心邏輯；它們來自 Termux 套件庫，並經 `patchelf` 調整依賴名稱，才能從本 App 的 `nativeLibraryDir` 載入（不再寫死 Termux 的 `/data/data/com.termux/...` 路徑）。

| 檔案 | 來源套件 | 授權 |
|---|---|---|
| `arm64-v8a/libproot.so` | Termux `proot` 5.1.107.102 | GPL-2.0 |
| `arm64-v8a/libproot-loader.so` | 同上（`libexec/proot/loader`，靜態） | GPL-2.0 |
| `arm64-v8a/libtalloc.so` | Termux `libtalloc` 2.5.0 | LGPL-3.0 |
| `arm64-v8a/libandroid-shmem.so` | Termux `libandroid-shmem` 0.7 | BSD-3-Clause |
| `arm64-v8a/libdebarm-pty.so` | 本專案 `app/src/main/cpp/pty_bridge.cpp` | MIT |

原始 `.deb` 放在 `upstream-debs/`，SHA-256 見 `SHA256SUMS`。重新取得請跑 `scripts/fetch-prebuilts.sh`。

PRoot 是以**獨立行程**被 `exec` 的，與以 MIT 授權的 DebArm 原始碼屬聚合發行。完整授權文本在 [`licenses/`](../licenses/)，源碼請向上游取得：

- https://github.com/termux/proot
- https://talloc.samba.org/
- https://github.com/termux/libandroid-shmem

自行重編 PTY 函式庫：`./scripts/build-pty.sh`（需要 NDK）。
