package de.inetsoftware.workspacedefaults

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Excludes folders by name (e.g. Eclipse `bin` with compiled classes) from indexing, in every module
 * content root and one level below it (subproject folders inside the workspace module). Nothing is
 * written to the modules, so a Gradle sync cannot undo it; turning it off restores the folders.
 */
class FolderExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {
    override fun getExcludeUrlsForProject(): Array<String> {
        val state = WorkspaceDefaultsSettings.getInstance(project).state
        if (!state.excludeFolders) return emptyArray()
        val patterns = ExcludePatterns(state.excludePatterns)
        if (patterns.patterns.isEmpty()) return emptyArray()

        val entries = ModuleManager.getInstance(project).modules.flatMap { ModuleRootManager.getInstance(it).contentEntries.toList() }
        val sourceUrls = entries.flatMap { entry -> entry.sourceFolders.map { it.url } }.toSet()
        val fileManager = VirtualFileManager.getInstance()
        val urls = linkedSetOf<String>()
        for (rootUrl in entries.map { it.url }.distinct()) {
            // Literal names directly below a content root are excluded even before the folder exists
            patterns.literals.forEach { urls += "$rootUrl/$it" }
            val root = fileManager.findFileByUrl(rootUrl) ?: continue
            for (child in root.children.filter { it.isDirectory && !it.name.startsWith(".") }) {
                if (patterns.matches(child.name)) urls += child.url
                else child.children.filter { it.isDirectory && patterns.matches(it.name) }.forEach { urls += it.url }
            }
        }
        return urls.filterNot { ExcludePatterns.isProtected(it, sourceUrls) }.toTypedArray()
    }

    companion object {
        /** Rebuilds the index roots after the patterns or the switch changed. */
        fun rescan(project: Project) {
            WriteAction.run<RuntimeException> {
                ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
            }
        }
    }
}
