# 第三方元件

DebArm 自己的程式碼（Kotlin / JNI PTY / 說明文件）是 MIT。下列元件另行授權。

## PRoot 5.1.107.102（GPL-2.0）

- 來源：Termux 套件 `proot`（基於 https://github.com/termux/proot，上游 https://proot-me.github.io/）
- 檔案：`prebuilt/arm64-v8a/libproot.so`、`libproot-loader.so`
- 授權全文：`licenses/proot-GPL-2.0.txt`
- 說明：以獨立行程執行，用來在沒有 root 的情況下，把行程的檔案系統根目錄切換到本機 Debian aarch64 rootfs。這不是 QEMU，也不翻譯指令集。

## libtalloc 2.5.0（LGPL-3.0）

- 來源：Termux 套件 `libtalloc`（https://talloc.samba.org/）
- 檔案：`prebuilt/arm64-v8a/libtalloc.so`
- 授權全文：`licenses/talloc-LGPL-3.0.txt`

## libandroid-shmem 0.7（BSD-3-Clause）

- 來源：https://github.com/termux/libandroid-shmem
- 檔案：`prebuilt/arm64-v8a/libandroid-shmem.so`
- 授權全文：`licenses/libandroid-shmem-BSD-3-Clause.txt`

## xterm.js 5.5.0 與 @xterm/addon-fit 0.10.0（MIT）

- 來源：https://github.com/xtermjs/xterm.js
- 檔案：`app/src/main/assets/terminal/`
- 授權全文：`licenses/xterm.js-MIT.txt`

## 執行期下載的 Debian rootfs

預設來自 Termux proot-distro v4.29.0 的 `debian-trixie-aarch64` 映像；備用為 linuxcontainers.org 的 Debian 13 arm64 映像。URL 與 SHA-256 釘在 `RootfsCatalog.kt`。rootfs 內的套件各自適用 Debian 的授權（大量為 GPL / DFSG 自由軟體）。

## 建置期依賴

Gradle / AndroidX / Kotlin / Apache Commons Compress / XZ / zstd-jni 等依各套件授權，由 Maven 解析，不直接放入本倉的二進制樹。
