package de.inetsoftware.workspacedefaults

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.TestRunner
import java.nio.file.Path

/** Distribution, Gradle JVM and runners for every linked Gradle build. */
object GradleDefaults {
    fun defaults(s: WorkspaceDefaultsState) = GradleValues(
        distribution = s.distributionType.takeIf { s.manageDistribution },
        gradleHome = s.gradleHome.orEmpty().takeIf { s.manageDistribution },
        gradleJvm = s.gradleJvm.takeIf { s.manageGradleJvm },
        delegatedBuild = s.delegatedBuild.takeIf { s.manageBuildRunner },
        testRunner = s.testRunner.takeIf { s.manageTestRunner },
    )

    fun current(ps: GradleProjectSettings) = GradleValues(
        distribution = ps.distributionType?.name,
        gradleHome = ps.gradleHomePath?.toString(),
        gradleJvm = ps.gradleJvm,
        delegatedBuild = ps.delegatedBuild,
        testRunner = ps.testRunner.name,
    )

    fun linkedBuilds(project: Project): List<GradleProjectSettings> =
        GradleSettings.getInstance(project).linkedProjectsSettings.sortedBy { it.externalProjectPath }

    fun deviations(project: Project, s: WorkspaceDefaultsState): List<Deviation> {
        val defaults = defaults(s)
        return linkedBuilds(project).flatMap { Deviations.gradle(buildName(project, it.externalProjectPath), defaults, current(it)) }
    }

    /** Applies the defaults to [ps] in place; returns whether anything changed. */
    fun applyTo(ps: GradleProjectSettings, d: GradleValues): Boolean {
        val before = current(ps)
        d.distribution?.let { name ->
            val type = runCatching { DistributionType.valueOf(name) }.getOrNull() ?: return@let
            ps.distributionType = type
            if (type == DistributionType.LOCAL) ps.gradleHomePath = d.gradleHome?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        }
        d.gradleJvm?.let { ps.gradleJvm = it }
        d.delegatedBuild?.let { ps.delegatedBuild = it }
        d.testRunner?.let { name -> runCatching { TestRunner.valueOf(name) }.getOrNull()?.let { ps.testRunner = it } }
        return current(ps) != before
    }

    /**
     * Overwrites the settings of all linked builds. Goes through [GradleSettings.setLinkedProjectsSettings]
     * so the Gradle plugin sees the changes like edits on its own settings page.
     */
    fun applyAll(project: Project, s: WorkspaceDefaultsState): Int {
        val defaults = defaults(s)
        val settings = GradleSettings.getInstance(project)
        var changed = 0
        val updated = settings.linkedProjectsSettings.map { ps ->
            ps.clone().also { if (applyTo(it, defaults)) changed++ }
        }
        if (changed > 0) settings.setLinkedProjectsSettings(updated)
        return changed
    }

    /** Short name of a build: its path relative to the project directory, or the folder name. */
    fun buildName(project: Project, path: String): String {
        val build = runCatching { Path.of(path).normalize() }.getOrNull() ?: return path
        val base = project.basePath?.let { runCatching { Path.of(it).normalize() }.getOrNull() }
        val parent = base?.parent
        return when {
            base != null && build.startsWith(base) && build != base -> base.relativize(build).toString()
            // Workspace layout: builds next to the workspace project folder
            parent != null && build.startsWith(parent) && build != parent -> parent.relativize(build).toString()
            else -> build.fileName?.toString() ?: path
        }
    }
}
