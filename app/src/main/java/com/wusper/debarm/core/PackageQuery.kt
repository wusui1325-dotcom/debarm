package com.wusper.debarm.core

data class DebPackage(
    val name: String,
    val version: String,
    val status: String,
    val manual: Boolean,
)

object PackageQuery {
    fun list(paths: DebArmPaths, cancelled: () -> Boolean = { false }): List<DebPackage> {
        Proot.assertRunnable(paths)
        val installed = HostProcess.run(
            argv = Proot.argv(
                paths,
                listOf(
                    "dpkg-query -W -f='\${db:Status-Abbrev}|\${Package}|\${Version}\\n'",
                ),
            ),
            env = Proot.hostEnv(paths),
            timeoutMs = 60_000,
            cancelled = cancelled,
        )
        val manual = HostProcess.run(
            argv = Proot.argv(paths, listOf("apt-mark showmanual 2>/dev/null || true")),
            env = Proot.hostEnv(paths),
            timeoutMs = 60_000,
            cancelled = cancelled,
        )
        val manualSet = manual.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return installed.output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 3) return@mapNotNull null
            val status = parts[0].trim()
            val name = parts[1].trim()
            val version = parts[2].trim()
            if (name.isEmpty()) return@mapNotNull null
            DebPackage(
                name = name,
                version = version,
                status = status,
                manual = name in manualSet,
            )
        }.sortedWith(compareByDescending<DebPackage> { it.manual }.thenBy { it.name }).toList()
    }
}
