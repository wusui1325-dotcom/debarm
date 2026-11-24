#!/usr/bin/env bash
# Re-fetch Termux PRoot binaries for DebArm's jniLibs layout.
# PRoot is copied unpatched: NEEDED stays libtalloc.so.2, which the App
# stages at runtime via LD_LIBRARY_PATH. Do not patchelf PRoot — rewriting
# program headers previously caused a SIGSEGV (exit 139) on first guest exec.
# Requires: curl, sha256sum, dpkg-deb.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKDIR="${TMPDIR:-/tmp}/debarm-prebuilts-fetch"
BASE="https://packages.termux.dev/apt/termux-main"
mkdir -p "$WORKDIR" "$ROOT/prebuilt/arm64-v8a" "$ROOT/prebuilt/upstream-debs"
cd "$WORKDIR"

fetch() {
  local file="$1" sha="$2" path="$3"
  curl -fL --retry 3 -o "$file" "$BASE/$path"
  local got
  got="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$got" != "$sha" ]]; then
    echo "SHA-256 mismatch for $file: $got != $sha" >&2
    exit 1
  fi
}

fetch proot_5.1.107.102_aarch64.deb 10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030 \
  pool/main/p/proot/proot_5.1.107.102_aarch64.deb
fetch libtalloc_2.5.0_aarch64.deb 556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7 \
  pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb
fetch libandroid-shmem_0.7_aarch64.deb 0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6 \
  pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb

cp -f ./*.deb "$ROOT/prebuilt/upstream-debs/"
extract() { mkdir -p "$1"; dpkg-deb -x "$2" "$1"; }
extract e_proot proot_5.1.107.102_aarch64.deb
extract e_talloc libtalloc_2.5.0_aarch64.deb
extract e_shmem libandroid-shmem_0.7_aarch64.deb
P=e_proot/data/data/com.termux/files/usr
T=e_talloc/data/data/com.termux/files/usr
S=e_shmem/data/data/com.termux/files/usr
cp -f "$P/bin/proot" "$ROOT/prebuilt/arm64-v8a/libproot.so"
cp -f "$P/libexec/proot/loader" "$ROOT/prebuilt/arm64-v8a/libproot-loader.so"
cp -f "$T/lib/libtalloc.so.2.5.0" "$ROOT/prebuilt/arm64-v8a/libtalloc.so"
cp -f "$S/lib/libandroid-shmem.so" "$ROOT/prebuilt/arm64-v8a/libandroid-shmem.so"
(cd "$ROOT/prebuilt/arm64-v8a" && sha256sum libproot.so libproot-loader.so libtalloc.so libandroid-shmem.so)
echo "OK. See prebuilt/README.md"
