package de.inetsoftware.workspacedefaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessorModulesTest {
    @Test
    fun `only modules whose own sources declare a processor`() {
        val modules = listOf(
            ModuleInfo("WebAPICore", false),
            ModuleInfo("WebAPICore.main", true),
            // Runs the processor from the main output (testAnnotationProcessor files(sourceSets.main.output))
            ModuleInfo("WebAPICore.test", false),
            ModuleInfo("WebAPICore.mock", false),
            ModuleInfo("InetServerCore.main", false),
            ModuleInfo("InetServerCore.tools", true),
            ModuleInfo("Help.main", false),
        )
        assertEquals(setOf("InetServerCore.tools", "WebAPICore.main"), ProcessorModules.withoutProcessing(modules))
    }

    @Test
    fun `no processor no modules`() {
        assertTrue(ProcessorModules.withoutProcessing(listOf(ModuleInfo("Help.main", false))).isEmpty())
    }
}
