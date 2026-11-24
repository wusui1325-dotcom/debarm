package com.wusper.debarm.core

import java.io.File

data class DebPackage(
    val name: String,
    val version: String,
    val status: String,
    val manual: Boolean,
)

/**
 * Reads package status from the extracted rootfs on the host.
 * Avoids launching PRoot just to list packages (much faster on refresh).
 */
object PackageQuery {
    fun list(paths: DebArmPaths, cancelled: () -> Boolean = { false }): List<DebPackage> {
        val statusFile = File(paths.rootfsDir, "var/lib/dpkg/status")
        if (!statusFile.isFile) return emptyList()
        if (cancelled()) return emptyList()
        val statesFile = File(paths.rootfsDir, "var/lib/apt/extended_states")
        val auto = if (statesFile.isFile) parseAutoInstalled(statesFile.readText()) else emptySet()
        val hasStates = statesFile.isFile
        return parseStatus(statusFile.readText(), auto, hasStates)
    }

    fun parseAutoInstalled(text: String): Set<String> {
        val auto = mutableSetOf<String>()
        var pkg = ""
        var isAuto = false
        fun flush() {
            if (pkg.isNotEmpty() && isAuto) auto += pkg
            pkg = ""
            isAuto = false
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isEmpty()) {
                flush()
                continue
            }
            when {
                line.startsWith("Package:", ignoreCase = true) -> pkg = line.substringAfter(':').trim()
                line.startsWith("Auto-Installed:", ignoreCase = true) ->
                    isAuto = line.substringAfter(':').trim() == "1"
            }
        }
        flush()
        return auto
    }

    fun parseStatus(
        text: String,
        autoInstalled: Set<String>,
        hasExtendedStates: Boolean,
    ): List<DebPackage> {
        val out = mutableListOf<DebPackage>()
        var name = ""
        var version = ""
        var status = ""
        fun flush() {
            if (name.isNotEmpty() && isInstalled(status)) {
                val manual = if (hasExtendedStates) name !in autoInstalled else true
                out += DebPackage(
                    name = name,
                    version = version,
                    status = abbreviate(status),
                    manual = manual,
                )
            }
            name = ""
            version = ""
            status = ""
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isEmpty()) {
                flush()
                continue
            }
            when {
                line.startsWith("Package:", ignoreCase = true) -> name = line.substringAfter(':').trim()
                line.startsWith("Version:", ignoreCase = true) -> version = line.substringAfter(':').trim()
                line.startsWith("Status:", ignoreCase = true) -> status = line.substringAfter(':').trim()
            }
        }
        flush()
        return out.sortedWith(compareByDescending<DebPackage> { it.manual }.thenBy { it.name })
    }

    private fun isInstalled(status: String): Boolean {
        val parts = status.split(Regex("\\s+"))
        return parts.size >= 3 && parts[0] == "install" && parts[2] == "installed"
    }

    private fun abbreviate(status: String): String {
        val parts = status.split(Regex("\\s+"))
        if (parts.size < 3) return status
        val sel = when (parts[0]) { "install" -> "i"; "deinstall" -> "r"; "purge" -> "p"; "hold" -> "h"; else -> "?" }
        val inst = when (parts[2]) { "installed" -> "i"; "config-files" -> "c"; "not-installed" -> "n"; else -> "?" }
        return "$sel$inst"
    }
}
