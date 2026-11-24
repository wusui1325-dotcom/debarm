#!/usr/bin/env bash
# Cross-compile the PTY JNI (libdebarm-pty.so) with the Android NDK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/ndk/27.0.12077973}"
# Fallback: pick any installed NDK
if [[ ! -d "$NDK" ]]; then
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    NDK="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | tail -n 1 || true)"
  fi
fi
if [[ ! -d "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME or install NDK r27 under \$ANDROID_HOME/ndk/" >&2
  exit 1
fi
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TC/bin/aarch64-linux-android26-clang++"
OUT="$ROOT/prebuilt/arm64-v8a/libdebarm-pty.so"
"$CC" -shared -fPIC -O2 -fvisibility=hidden -Wall -Wextra -DANDROID \
  -I"$TC/sysroot/usr/include" \
  "$ROOT/app/src/main/cpp/pty_bridge.cpp" \
  -o "$OUT" \
  -L"$TC/sysroot/usr/lib/aarch64-linux-android/26" \
  -llog -lc -ldl -static-libstdc++ \
  -Wl,-soname,libdebarm-pty.so \
  -Wl,--no-undefined \
  -Wl,-z,max-page-size=16384 \
  -Wl,-z,common-page-size=16384
"$TC/bin/llvm-strip" --strip-unneeded "$OUT" || true
sha256sum "$OUT"
