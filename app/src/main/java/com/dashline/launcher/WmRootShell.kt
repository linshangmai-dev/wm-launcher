// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import java.io.File

/** Detect the installed su dialect with a read-only command before selecting a transport. */
internal class WmRootShell private constructor(private val path: String, private val stdinMode: Boolean) {
    fun execute(command: String, timeout: Long): String {
        require(!command.contains('\n') && !command.contains('\u0000'))
        return if (stdinMode) WmCommandProcess.run(listOf(path), timeout, "$command\nexit\n")
        else WmCommandProcess.run(listOf(path, "-c", command), timeout)
    }

    companion object {
        fun find(paths: List<String> = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su")): WmRootShell? {
            for (path in paths) {
                if (!File(path).canExecute()) continue
                // The WM su binary ignores -c and starts an interactive shell. Sending the
                // command through stdin also works with conventional su implementations.
                for (stdinMode in listOf(true, false)) {
                    val candidate = WmRootShell(path, stdinMode)
                    val probe = runCatching { candidate.execute("id", 2500) }.getOrNull() ?: continue
                    if (Regex("(?:^|\\s)uid=0\\(").containsMatchIn(probe)) return candidate
                }
            }
            return null
        }
    }
}

/** Finite child processes with closed input, bounded output and joined reader threads. */
internal object WmCommandProcess {
    fun run(args: List<String>, timeout: Long, stdin: String? = null): String {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread({
            try {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.length < 65_536) output.append(buffer, 0, count.coerceAtMost(65_536 - output.length))
                    }
                }
            } catch (_: Exception) { }
        }, "WM-RootResult").apply { start() }
        try {
            process.outputStream.bufferedWriter().use { stream -> if (stdin != null) stream.write(stdin) }
            val deadline = System.nanoTime() + timeout * 1_000_000
            var code: Int
            while (true) {
                val exitCode = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exitCode != null) { code = exitCode; break }
                check(System.nanoTime() < deadline) { "系统操作超时，请重新检查开关状态" }
                Thread.sleep(40)
            }
            reader.join(1000)
            check(!reader.isAlive) { "系统操作结果未返回完整内容" }
            check(code == 0) { "系统命令未完成：${output.takeLast(240)}" }
            return output.toString()
        } finally {
            process.destroy()
            try { process.inputStream.close() } catch (_: Exception) { }
            reader.join(1000)
        }
    }
}
