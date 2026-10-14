package de.inetsoftware.workspacedefaults

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VfsUtilCore
import java.nio.file.Path

/** Adds missing Git and SVN directory mappings for the folders of the workspace. */
object VcsDefaults {
    const val SUBVERSION_PLUGIN_ID = "Subversion"

    data class Scan(val missing: List<VcsRoot>, val unsupported: List<VcsRoot>)

    fun isAvailable(project: Project, kind: VcsKind): Boolean =
        ProjectLevelVcsManager.getInstance(project).findVcsByName(kind.vcsName) != null

    /**
     * Scans the module content roots, linked Gradle builds and the project folder, optionally only
     * those inside [under]. Roots of a VCS whose plugin is not installed are reported as unsupported.
     */
    fun scan(project: Project, under: Path? = null): Scan {
        val candidates = candidates(project).filter { under == null || it.startsWith(under) }
        val roots = VcsRoots.find(candidates, boundaries())
        val existing = ProjectLevelVcsManager.getInstance(project).getDirectoryMappings().map { Mapping(it.directory, it.vcs) }
        val missing = VcsRoots.missing(existing, roots, project.basePath?.let { Path.of(it) })
        val (supported, unsupported) = missing.partition { isAvailable(project, it.kind) }
        return Scan(supported, unsupported)
    }

    fun deviations(scan: Scan): List<Deviation> = scan.missing.map {
        Deviation(it.path.toString(), "VCS mapping", Deviations.NOT_SET, it.kind.vcsName)
    } + scan.unsupported.map {
        Deviation(it.path.toString(), "VCS mapping", Deviations.NOT_SET, "${it.kind.vcsName} (plugin missing)")
    }

    /** Adds mappings for [roots]; existing mappings stay as they are. Must run on the EDT. */
    fun addMappings(project: Project, roots: Collection<VcsRoot>) {
        if (roots.isEmpty()) return
        val manager = ProjectLevelVcsManager.getInstance(project)
        manager.setDirectoryMappings(manager.getDirectoryMappings() + roots.map { VcsDirectoryMapping(it.path.toString().replace('\\', '/'), it.kind.vcsName) })
    }

    private fun candidates(project: Project): List<Path> {
        val paths = mutableSetOf<Path>()
        ModuleManager.getInstance(project).modules.forEach { module ->
            ModuleRootManager.getInstance(module).contentRoots.forEach { root ->
                if (root.isInLocalFileSystem) paths.add(VfsUtilCore.virtualToIoFile(root).toPath())
            }
        }
        GradleDefaults.linkedBuilds(project).forEach { paths.add(Path.of(it.externalProjectPath)) }
        project.basePath?.let { paths.add(Path.of(it)) }
        return paths.map { it.toAbsolutePath().normalize() }.distinct()
    }

    /** Never map the home directory or anything above it (dotfiles repositories). */
    private fun boundaries(): List<Path> = listOfNotNull(System.getProperty("user.home")?.let { Path.of(it) })
}
