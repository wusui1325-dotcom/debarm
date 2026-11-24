package com.wusper.debarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageQueryTest {
    @Test
    fun parsesInstalledAndSkipsRemoved() {
        val status = """
            Package: bash
            Status: install ok installed
            Version: 5.2.37-1

            Package: leftover
            Status: deinstall ok config-files
            Version: 1.0

            Package: hello
            Status: install ok installed
            Version: 2.10-3
        """.trimIndent()
        val auto = PackageQuery.parseAutoInstalled(
            """
            Package: bash
            Auto-Installed: 0

            Package: hello
            Auto-Installed: 1
            """.trimIndent(),
        )
        assertTrue("hello" in auto)
        assertFalse("bash" in auto)
        val list = PackageQuery.parseStatus(status, auto, hasExtendedStates = true)
        assertEquals(listOf("bash", "hello"), list.map { it.name })
        val bash = list.first { it.name == "bash" }
        assertTrue(bash.manual)
        assertEquals("ii", bash.status)
        assertFalse(list.first { it.name == "hello" }.manual)
    }

    @Test
    fun withoutExtendedStatesTreatsInstalledAsManual() {
        val status = "Package: coreutils\nStatus: install ok installed\nVersion: 9.4\n"
        val list = PackageQuery.parseStatus(status, emptySet(), hasExtendedStates = false)
        assertEquals(1, list.size)
        assertTrue(list[0].manual)
    }
}
