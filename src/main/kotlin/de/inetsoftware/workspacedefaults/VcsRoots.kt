package de.inetsoftware.workspacedefaults

import java.nio.file.Files
import java.nio.file.Path

enum class VcsKind(val marker: String, val vcsName: String) {
    GIT(".git", "Git"),
    SVN(".svn", "svn"),
}

data class VcsRoot(val path: Path, val kind: VcsKind)

/** A directory mapping as stored by the IDE; an empty [directory] stands for the project base directory. */
data class Mapping(val directory: String, val vcs: String)

object VcsRoots {
    /**
     * Finds the working copy root for each candidate directory by walking up to the nearest directory
     * with a `.git` or `.svn` marker. For SVN the walk continues while the parent is part of the same
     * working copy (pre-1.7 checkouts have `.svn` in every directory). [boundaries] and everything above
     * them are never reported, so a dotfiles repository in the home directory is not picked up.
     */
    fun find(
        candidates: Collection<Path>,
        boundaries: Collection<Path>,
        hasMarker: (Path, VcsKind) -> Boolean = { dir, kind -> Files.exists(dir.resolve(kind.marker)) },
    ): List<VcsRoot> {
        val stops = boundaries.map { it.toAbsolutePath().normalize() }.toSet()
        return candidates.mapNotNull { candidate ->
            var dir: Path? = candidate.toAbsolutePath().normalize()
            var found: VcsRoot? = null
            while (dir != null && dir !in stops && dir.parent != null && found == null) {
                found = when {
                    hasMarker(dir, VcsKind.GIT) -> VcsRoot(dir, VcsKind.GIT)
                    hasMarker(dir, VcsKind.SVN) -> VcsRoot(topmostSvn(dir, stops, hasMarker), VcsKind.SVN)
                    else -> null
                }
                dir = dir.parent
            }
            found
        }.distinct()
    }

    private fun topmostSvn(start: Path, stops: Set<Path>, hasMarker: (Path, VcsKind) -> Boolean): Path {
        var dir = start
        while (true) {
            val parent = dir.parent ?: return dir
            if (parent in stops || parent.parent == null || !hasMarker(parent, VcsKind.SVN)) return dir
            dir = parent
        }
    }

    /**
     * Returns the roots that have no mapping yet. A mapping for the same directory counts as present
     * whatever its VCS (also "none"), so roots the user has mapped differently are left alone.
     */
    fun missing(existing: Collection<Mapping>, roots: Collection<VcsRoot>, projectBase: Path?): List<VcsRoot> {
        val mapped = existing.mapNotNull { mapping ->
            if (mapping.directory.isEmpty()) projectBase?.toAbsolutePath()?.normalize()
            else runCatching { Path.of(mapping.directory).toAbsolutePath().normalize() }.getOrNull()
        }.toSet()
        return roots.filter { it.path !in mapped }
    }
}
