package com.wusper.debarm.core

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val output: String,
)

object HostProcess {
    fun run(
        argv: Array<String>,
        env: Map<String, String>,
        cwd: File? = null,
        timeoutMs: Long = 0,
        cancelled: () -> Boolean = { false },
        onChunk: (String) -> Unit = {},
    ): CommandResult {
        val pb = ProcessBuilder(argv.toList())
            .redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(env)
        if (cwd != null) pb.directory(cwd)
        val proc = pb.start()
        val buf = StringBuilder()
        val reader = Thread {
            val bytes = ByteArray(4096)
            try {
                val input = proc.inputStream
                while (true) {
                    val n = input.read(bytes)
                    if (n < 0) break
                    val chunk = String(bytes, 0, n, StandardCharsets.UTF_8)
                    buf.append(chunk)
                    onChunk(chunk)
                }
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true; it.start() }

        try {
            val start = System.currentTimeMillis()
            while (proc.isAlive) {
                if (cancelled()) {
                    proc.destroy()
                    proc.waitFor(2, TimeUnit.SECONDS)
                    if (proc.isAlive) proc.destroyForcibly()
                    reader.join(1000)
                    return CommandResult(143, buf.toString() + "\n（已取消）")
                }
                if (timeoutMs > 0 && System.currentTimeMillis() - start > timeoutMs) {
                    proc.destroyForcibly()
                    reader.join(1000)
                    return CommandResult(124, buf.toString() + "\n（逾時）")
                }
                proc.waitFor(200, TimeUnit.MILLISECONDS)
            }
            reader.join(5000)
            return CommandResult(proc.exitValue(), buf.toString())
        } finally {
            if (proc.isAlive) proc.destroyForcibly()
        }
    }
}
