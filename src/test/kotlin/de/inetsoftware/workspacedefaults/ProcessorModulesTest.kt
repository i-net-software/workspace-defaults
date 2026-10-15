package de.inetsoftware.workspacedefaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class ProcessorModulesTest {
    private val webApi = Path.of("/ws/WebAPICore")
    private val help = Path.of("/ws/Help")

    @Test
    fun `declaring module and its dependents in the same project`() {
        val modules = listOf(
            ModuleInfo("WebAPICore", webApi, false),
            ModuleInfo("WebAPICore.main", webApi, true),
            ModuleInfo("WebAPICore.test", webApi, false, setOf("WebAPICore.main")),
            ModuleInfo("WebAPICore.mock", webApi, false, setOf("WebAPICore.main")),
            // Other builds run the processor from their classpath
            ModuleInfo("Help.main", help, false, setOf("WebAPICore.main")),
            ModuleInfo("Help.test", help, false, setOf("Help.main")),
        )
        assertEquals(
            setOf("WebAPICore.main", "WebAPICore.mock", "WebAPICore.test"),
            ProcessorModules.withoutProcessing(modules),
        )
    }

    @Test
    fun `processor in a side source set leaves main alone`() {
        val core = Path.of("/ws/InetServerCore")
        val modules = listOf(
            ModuleInfo("InetServerCore.main", core, false),
            ModuleInfo("InetServerCore.test", core, false, setOf("InetServerCore.main")),
            ModuleInfo("InetServerCore.tools", core, true, setOf("InetServerCore.main")),
        )
        assertEquals(setOf("InetServerCore.tools"), ProcessorModules.withoutProcessing(modules))
    }

    @Test
    fun `module without gradle project only itself`() {
        val modules = listOf(ModuleInfo("Plain", null, true), ModuleInfo("Other", null, false))
        assertEquals(setOf("Plain"), ProcessorModules.withoutProcessing(modules))
    }

    @Test
    fun `no processor no modules`() {
        assertTrue(ProcessorModules.withoutProcessing(listOf(ModuleInfo("Help.main", help, false))).isEmpty())
    }
}
