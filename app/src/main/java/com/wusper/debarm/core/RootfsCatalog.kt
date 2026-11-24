package com.wusper.debarm.core

/**
 * Pinned Debian aarch64 rootfs images. Primary tarball is the last
 * official proot-distro minbase-style image (already contains apt/dpkg).
 * The LXC image is a larger fallback with the same architecture.
 */
object RootfsCatalog {
    data class Image(
        val name: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
        val stripComponents: Int,
        val label: String,
    )

    val primary = Image(
        name = "debian-trixie-aarch64-pd-v4.29.0",
        url = "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-aarch64-pd-v4.29.0.tar.xz",
        sha256 = "3834a11cbc6496935760bdc20cca7e2c25724d0cd8f5e4926da8fd5ca1857918",
        bytes = 35_409_704L,
        stripComponents = 1,
        label = "Debian 13 (trixie) aarch64 · proot-distro v4.29.0",
    )

    val fallback = Image(
        name = "debian-trixie-arm64-lxc-20261118",
        url = "https://images.linuxcontainers.org/images/debian/trixie/arm64/default/20261118_05:24/rootfs.tar.xz",
        sha256 = "a9ae0ee8503cfa8787d21d35892ec3f74f1c457f570367d92ba090e8f263628c",
        bytes = 90_411_532L,
        stripComponents = 0,
        label = "Debian 13 (trixie) arm64 · LXC 2026-11-18",
    )

    val all = listOf(primary, fallback)
}
