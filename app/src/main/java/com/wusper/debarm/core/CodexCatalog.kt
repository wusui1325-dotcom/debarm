package com.wusper.debarm.core

/**
 * Official OpenAI ChatGPT / Codex Linux desktop app (arm64 .deb).
 *
 * Source of truth is the official Linux install page and the APT index:
 * - https://learn.chatgpt.com/docs/linux/linux-app
 * - https://persistent.oaistatic.com/codex-app-prod/linux/deb/dists/stable/main/binary-arm64/Packages
 *
 * Re-verified 2026-11-24: Packages still lists only 26.1120.41243
 * (SHA-256 72b8b9ff2daa7bc750fb2149cec753f5ca06caef2157592777a45475f4292f27).
 * The convenience "latest" URL is recorded but not used as the only pin;
 * we download the versioned pool file and verify SHA-256.
 *
 * The musl CLI tarball is no longer the product this edition ships.
 */
object CodexCatalog {
    const val packageName = "chatgpt"
    const val version = "26.1120.41243"
    const val architecture = "arm64"
    const val assetName = "chatgpt_26.1120.41243_arm64.deb"
    const val sha256 = "72b8b9ff2daa7bc750fb2149cec753f5ca06caef2157592777a45475f4292f27"
    const val bytes = 486_783_954L
    const val installedSizeKb = 1_568_808L
    const val officialDocs = "https://learn.chatgpt.com/docs/linux/linux-app"
    const val homepage = "https://developers.openai.com/codex/app"
    const val packagesIndex =
        "https://persistent.oaistatic.com/codex-app-prod/linux/deb/dists/stable/main/binary-arm64/Packages"
    const val latestUrl =
        "https://persistent.oaistatic.com/codex-app-prod/linux/deb/latest/chatgpt_arm64.deb"

    /** Versioned official pool URL — this is what we pin and checksum. */
    val primaryUrl: String
        get() = "https://persistent.oaistatic.com/codex-app-prod/linux/deb/pool/main/c/chatgpt/$assetName"

    val urls: List<String> get() = listOf(primaryUrl, latestUrl)

    const val guestBinaryPath = "/usr/bin/chatgpt"
    const val startScriptPath = "/usr/local/bin/debarm-start-chatgpt"
    const val displayPort = 6080
    const val vncPort = 5900
    const val displayNumber = 1
    const val screenWidth = 1280
    const val screenHeight = 800
    const val kernelRelease = "6.1.0-1-aarch64"
    const val guestUser = "user"
    const val guestUidGid = "1000:1000"

    /** Debian / rootfs / desktop stack plus the unpacked official app (~1.5 GB). */
    const val minFreeBytes = 4L * 1024L * 1024L * 1024L

    /** Required to publish an X11 display into the Android WebView. */
    val displayStackRequired = listOf(
        "xvfb",
        "x11-utils",
        "x11vnc",
        "openbox",
        "novnc",
        "websockify",
        "dbus-x11",
        "ca-certificates",
    )

    /** Nice to have; a missing name must not abort the official .deb install. */
    val displayStackOptional = listOf(
        "fonts-dejavu-core",
        "fonts-liberation",
        "libgl1-mesa-dri",
        "mesa-vulkan-drivers",
        "xdg-utils",
    )
}
