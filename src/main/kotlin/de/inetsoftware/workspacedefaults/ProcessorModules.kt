package de.inetsoftware.workspacedefaults

import java.nio.file.Path

/**
 * A module with the folder of its Gradle project, whether its own sources register an annotation processor,
 * and the modules it depends on.
 */
data class ModuleInfo(val name: String, val projectDir: Path?, val declaresProcessor: Boolean, val dependsOn: Set<String> = emptySet())

object ProcessorModules {
    const val SERVICE_FILE = "META-INF/services/javax.annotation.processing.Processor"

    /**
     * Modules that must not run annotation processing with processors from their classpath: those whose
     * sources declare a processor, plus the modules of the same Gradle project that depend on them (test,
     * mock, … see the processor through the main output). Running the processor while compiling it fails
     * with "Provider … not found". Modules of other builds keep processing, they are meant to run it.
     */
    fun withoutProcessing(modules: Collection<ModuleInfo>): Set<String> {
        val declaring = modules.filter { it.declaresProcessor }
        val dependents = modules.filter { module ->
            module.projectDir != null && declaring.any { it.projectDir == module.projectDir && it.name in module.dependsOn }
        }
        return (declaring + dependents).map { it.name }.toSortedSet()
    }
}
