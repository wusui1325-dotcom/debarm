package com.wusper.debarm.core

/**
 * Official OpenAI Codex CLI (not a .deb — OpenAI does not ship an official
 * Codex CLI .deb). The documented Linux ARM64 artifact is the musl tarball
 * published with each `rust-v*` GitHub release, also served from
 * https://releases.openai.com/codex (the same source used by
 * https://chatgpt.com/codex/install.sh).
 *
 * License of the CLI: Apache-2.0 (upstream openai/codex).
 * ChatGPT desktop `chatgpt_arm64.deb` is a different product (GUI) and is
 * not used here.
 */
object CodexCatalog {
    const val version = "0.172.0"
    const val gitTag = "rust-v0.172.0"
    const val assetName = "codex-aarch64-unknown-linux-musl.tar.gz"
    const val sha256 = "0b99526c185a4425fe96a2214fb7b6a6a7f7c1471883b7afbebe97e15e83e79e"
    const val bytes = 127_043_934L
    const val license = "Apache-2.0"
    const val officialInstallSh = "https://chatgpt.com/codex/install.sh"
    const val projectUrl = "https://github.com/openai/codex"

    /** Preferred official CDN, same path the official installer uses. */
    val primaryUrl: String
        get() = "https://releases.openai.com/codex/releases/$version/$assetName"

    /** Official GitHub Releases fallback. */
    val githubUrl: String
        get() = "https://github.com/openai/codex/releases/download/$gitTag/$assetName"

    val urls: List<String> get() = listOf(primaryUrl, githubUrl)

    const val guestBinaryPath = "/usr/local/bin/codex"
    const val kernelRelease = "6.1.0-1-aarch64"
    const val guestUser = "user"
    const val guestUidGid = "1000:1000"
}
