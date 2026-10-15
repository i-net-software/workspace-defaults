package de.inetsoftware.workspacedefaults

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

/**
 * Compiler settings of IntelliJ IDEA's own build (Build and run using: IntelliJ IDEA) that differ from
 * Gradle and Eclipse: @NotNull runtime assertions, -deprecation, and annotation processing, which the
 * Gradle import does not set up in a workspace with many builds.
 */
object CompilerDefaults {
    /** Module profile for modules whose own sources contain an annotation processor. */
    const val NO_PROCESSING_PROFILE = "Workspace Defaults: processor modules"

    /** Name of the profiles the Gradle import creates. */
    private const val GRADLE_IMPORTED_PROFILE = "Gradle Imported"

    private const val SETTING_NOT_NULL = "Runtime assertions for @NotNull"
    private const val SETTING_DEPRECATION = "Report use of deprecated features"
    private const val SETTING_PROCESSING = "Annotation processing"
    private const val SETTING_NO_PROCESSING = "No annotation processing"

    fun deviations(project: Project, s: WorkspaceDefaultsState): List<Deviation> {
        val config = CompilerConfiguration.getInstance(project)
        val result = Deviations.of(ProjectDefaults.SCOPE, listOf(
            SettingValue(SETTING_NOT_NULL, onOff(s.notNullAssertions).takeIf { s.manageNotNullAssertions }, onOff(config.isAddNotNullAssertions)),
            SettingValue(SETTING_DEPRECATION, onOff(s.deprecation).takeIf { s.manageDeprecation }, onOff(javacOptions(project).DEPRECATION)),
            SettingValue(SETTING_PROCESSING, PROCESSING_EXPECTED.takeIf { s.manageAnnotationProcessing }, defaultProfileState(config)),
        )).toMutableList()
        if (s.manageAnnotationProcessing && config is CompilerConfigurationImpl) {
            for (module in modulesWithoutProcessing(project)) {
                if (config.getAnnotationProcessingConfiguration(module.key).isEnabled) {
                    result += Deviation(module.value, SETTING_NO_PROCESSING, "processing enabled", "disabled (declares a processor)")
                }
            }
        }
        return result
    }

    /** Overwrites the managed compiler settings; returns the labels of those that differed. Runs on the EDT. */
    fun applyAll(project: Project, s: WorkspaceDefaultsState): List<String> {
        val written = deviations(project, s).map { it.setting }.distinct()
        val config = CompilerConfiguration.getInstance(project)
        if (s.manageNotNullAssertions) config.isAddNotNullAssertions = s.notNullAssertions
        if (s.manageDeprecation) javacOptions(project).DEPRECATION = s.deprecation
        if (s.manageAnnotationProcessing) applyAnnotationProcessing(project)
        return written
    }

    /**
     * Default profile on with processors from the project classpath, so processors come from the workspace
     * modules instead of released jars; a disabled module profile for the modules whose own source folders
     * contain a processor. Modules in a profile someone else made stay there.
     *
     * With several linked builds the Gradle import cannot keep processor profiles: every import removes the
     * profiles of all other builds as orphans (`AnnotationProcessingDataService.computeOrphanData` takes every
     * profile with only Gradle modules that is not part of the current import). What is left is the
     * "Gradle Imported" profile of the build imported last, pointing to the released processor jars, so it is
     * removed too. This also runs after every sync, because the import removes our profile as well.
     */
    fun applyAnnotationProcessing(project: Project) {
        val config = CompilerConfiguration.getInstance(project) as? CompilerConfigurationImpl ?: return
        val default = config.defaultProcessorProfile
        default.isEnabled = true
        default.setObtainProcessorsFromClasspath(true)

        if (GradleDefaults.linkedBuilds(project).size > 1) {
            config.moduleProcessorProfiles.filter { it.name == GRADLE_IMPORTED_PROFILE }.forEach { config.removeModuleProcessorProfile(it) }
        }

        val ownProfile = config.findModuleProcessorProfile(NO_PROCESSING_PROFILE)
        val inOtherProfiles = config.moduleProcessorProfiles.filter { it !== ownProfile }.flatMap { it.moduleNames }.toSet()
        val modules = modulesWithoutProcessing(project).values.filter { it !in inOtherProfiles }
        if (modules.isEmpty()) {
            ownProfile?.let { config.removeModuleProcessorProfile(it) }
            return
        }
        val profile = ownProfile ?: config.addNewProcessorProfile(NO_PROCESSING_PROFILE)
        profile.isEnabled = false
        profile.clearModuleNames()
        profile.addModuleNames(modules)
    }

    private val PROCESSING_EXPECTED = "enabled, processors from classpath"

    private fun defaultProfileState(config: CompilerConfiguration): String {
        val default = (config as? CompilerConfigurationImpl)?.defaultProcessorProfile ?: return "unknown"
        return when {
            !default.isEnabled -> "disabled"
            default.isObtainProcessorsFromClasspath -> PROCESSING_EXPECTED
            else -> "enabled, processor path"
        }
    }

    /** Modules whose processing must be off, keyed by module, valued by name. */
    private fun modulesWithoutProcessing(project: Project): Map<com.intellij.openapi.module.Module, String> {
        val modules = ModuleManager.getInstance(project).modules
        val infos = modules.map { module ->
            ModuleInfo(
                name = module.name,
                declaresProcessor = ModuleRootManager.getInstance(module).sourceRoots.any { it.findFileByRelativePath(ProcessorModules.SERVICE_FILE) != null },
            )
        }
        val names = ProcessorModules.withoutProcessing(infos)
        return modules.filter { it.name in names }.associateWith { it.name }
    }

    private fun javacOptions(project: Project) = JavacConfiguration.getOptions(project, JavacConfiguration::class.java)

    private fun onOff(value: Boolean) = if (value) "on" else "off"
}
