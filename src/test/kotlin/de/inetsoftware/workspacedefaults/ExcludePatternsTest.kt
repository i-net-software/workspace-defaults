package de.inetsoftware.workspacedefaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludePatternsTest {
    @Test
    fun `parses lists and wildcards`() {
        val p = ExcludePatterns(" bin, out;build-*  tmp?")
        assertEquals(listOf("bin", "out", "build-*", "tmp?"), p.patterns)
        assertEquals(listOf("bin", "out"), p.literals)
        assertTrue(p.matches("bin"))
        assertTrue(p.matches("build-eclipse"))
        assertTrue(p.matches("tmp1"))
        assertFalse(p.matches("binary"))
        assertFalse(p.matches("tmp12"))
        assertFalse(p.matches("Bin"))
    }

    @Test
    fun `regex characters in names are literal`() {
        val p = ExcludePatterns("a.b")
        assertTrue(p.matches("a.b"))
        assertFalse(p.matches("axb"))
    }

    @Test
    fun `empty text has no patterns`() {
        assertTrue(ExcludePatterns(null).patterns.isEmpty())
        assertTrue(ExcludePatterns(" , ").patterns.isEmpty())
    }

    @Test
    fun `folders with source roots are protected`() {
        val sources = listOf("file:///ws/a/bin/src", "file:///ws/b/src")
        assertTrue(ExcludePatterns.isProtected("file:///ws/a/bin", sources))
        assertTrue(ExcludePatterns.isProtected("file:///ws/b/src", sources))
        assertFalse(ExcludePatterns.isProtected("file:///ws/b/bin", sources))
        assertFalse(ExcludePatterns.isProtected("file:///ws/a/bi", sources))
    }
}
