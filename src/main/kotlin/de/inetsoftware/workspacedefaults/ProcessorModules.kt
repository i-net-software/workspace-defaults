package de.inetsoftware.workspacedefaults

/** A module and whether its own source folders register an annotation processor. */
data class ModuleInfo(val name: String, val declaresProcessor: Boolean)

object ProcessorModules {
    const val SERVICE_FILE = "META-INF/services/javax.annotation.processing.Processor"

    /**
     * Modules that must not run annotation processing: only those whose own source folders register the
     * processor, because running a processor while compiling it fails with "Provider … not found". Every
     * other module keeps processing, including the test module of the same project, which runs the processor
     * from the main output (Gradle: `testAnnotationProcessor files(sourceSets.main.output)`).
     */
    fun withoutProcessing(modules: Collection<ModuleInfo>): Set<String> =
        modules.filter { it.declaresProcessor }.map { it.name }.toSortedSet()
}
