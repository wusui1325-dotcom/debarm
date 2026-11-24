package com.wusper.debarm.core

/**
 * Official OpenAI Codex / ChatGPT Linux artifacts.
 *
 * The long-term target for this edition is the official ChatGPT / Codex
 * Linux desktop app (arm64 .deb), documented at
 * https://learn.chatgpt.com/docs/linux/linux-app and indexed at
 * https://persistent.oaistatic.com/codex-app-prod/linux/deb/
 * (currently pinned as [desktopPackage] below). One-tap X11/VNC launch
 * of that GUI is not shipped in this release.
 *
 * Until the desktop window is reliable under PRoot, we install the
 * official *complete packaged CLI* (`codex-package-*.tar.gz`) — the
 * same layout the standalone installer at chatgpt.com/codex/install.sh
 * uses — not the bare musl binary. The bare binary (what 1.0.7 and
 * earlier extracted) cannot start the interactive UI without
 * `--no-daemon` because it has no `codex-package.json` or bundled
 * resources.
 *
 * License of the CLI: Apache-2.0 (upstream openai/codex).
 */
object CodexCatalog {
    const val version = "0.172.0"
    const val gitTag = "rust-v0.172.0"
    const val assetName = "codex-package-aarch64-unknown-linux-musl.tar.gz"
    const val sha256 = "faf17b8071927f33346a805dc8cb45de4e08c0d75cf0e5e68cecddca28b8861e"
    const val bytes = 201_917_755L
    const val license = "Apache-2.0"
    const val officialInstallSh = "https://chatgpt.com/codex/install.sh"
    const val projectUrl = "https://github.com/openai/codex"
    const val officialDocs = "https://learn.chatgpt.com/docs/linux/linux-app"

    /** Preferred official CDN, same path the official installer uses. */
    val primaryUrl: String
        get() = "https://releases.openai.com/codex/releases/$version/$assetName"

    /** Official GitHub Releases fallback. */
    val githubUrl: String
        get() = "https://github.com/openai/codex/releases/download/$gitTag/$assetName"

    val urls: List<String> get() = listOf(primaryUrl, githubUrl)

    /** Guest path of the packaged entrypoint (bin/codex next to codex-package.json). */
    const val packageRoot = "/opt/codex"
    const val guestBinaryPath = "/opt/codex/bin/codex"
    const val legacyBinaryPath = "/usr/local/bin/codex"

    const val kernelRelease = "6.1.0-1-aarch64"
    const val guestUser = "user"
    const val guestUidGid = "1000:1000"

    /**
     * Official ChatGPT / Codex Linux desktop .deb (GUI). Not downloaded
     * or launched in this release; recorded so the UI and docs do not
     * claim OpenAI ships no official .deb.
     */
    object DesktopDeb {
        const val packageName = "chatgpt"
        const val version = "26.1120.41243"
        const val assetName = "chatgpt_26.1120.41243_arm64.deb"
        const val sha256 = "72b8b9ff2daa7bc750fb2149cec753f5ca06caef2157592777a45475f4292f27"
        const val bytes = 486_783_954L
        const val packagesIndex =
            "https://persistent.oaistatic.com/codex-app-prod/linux/deb/dists/stable/main/binary-arm64/Packages"
        const val latestUrl =
            "https://persistent.oaistatic.com/codex-app-prod/linux/deb/latest/chatgpt_arm64.deb"
        const val versionedUrl =
            "https://persistent.oaistatic.com/codex-app-prod/linux/deb/pool/main/c/chatgpt/$assetName"
        const val homepage = "https://developers.openai.com/codex/app"
    }
}
