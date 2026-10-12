// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WmRootShellTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun script(content: String): File = temporary.newFile().apply {
        writeText("#!/bin/sh\n$content\n")
        check(setExecutable(true))
    }

    @Test fun interactiveSuUsesStdinAndRunsOneCommand() {
        val counter = temporary.newFile()
        // This su has no -c support: exactly the relevant behavior of the car's binary.
        val su = script("[ \"\$#\" -eq 0 ] || exit 1\nwhile IFS= read -r cmd; do\n" +
            "case \"\$cmd\" in\nid) echo 'uid=0(root) gid=0(root)' ;;\n" +
            "perform) echo action >> '${counter.path}'; echo 'WM_HOME_RESULT={\"ok\":true}' ;;\n" +
            "exit) exit ;;\nesac\ndone")
        val transport = WmRootShell.find(listOf(su.path))
        assertNotNull(transport)
        assertTrue(transport!!.execute("perform", 1000).contains("WM_HOME_RESULT="))
        assertEquals(listOf("action"), counter.readLines())
    }

    @Test fun supportsSuThatRequiresCommandArgument() {
        val su = script("[ \"\$1\" = '-c' ] || exit 1\n" +
            "case \"\$2\" in\nid) echo 'uid=0(root)' ;;\nperform) echo completed ;;\nesac")
        val transport = WmRootShell.find(listOf(su.path))
        assertNotNull(transport)
        assertEquals("completed", transport!!.execute("perform", 1000).trim())
    }

    @Test fun refusesUnprivilegedSu() {
        val su = script("echo 'uid=18054(u0_a8054) gid=18054(u0_a8054)'")
        assertNull(WmRootShell.find(listOf(su.path)))
    }

    @Test fun hungCommandTimesOutWithoutRetry() {
        val counter = temporary.newFile()
        val command = script("echo once >> '${counter.path}'\nexec sleep 4")
        val start = System.nanoTime()
        try {
            WmCommandProcess.run(listOf(command.path), 120)
            fail("Expected a timeout")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("超时"))
        }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
        assertEquals(listOf("once"), counter.readLines())
    }
}
