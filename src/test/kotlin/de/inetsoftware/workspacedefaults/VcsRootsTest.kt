package de.inetsoftware.workspacedefaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class VcsRootsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(vararg parts: String): Path = Files.createDirectories(tmp.root.toPath().resolve(parts.joinToString("/")))

    @Test
    fun `finds the nearest git root and the topmost svn directory`() {
        val ws = dir("ws")
        dir("ws/gitBuild/.git")
        val gitModule = dir("ws/gitBuild/sub/module")
        // Pre-1.7 SVN working copy: .svn in every directory
        dir("ws/svnBuild/.svn")
        dir("ws/svnBuild/src/.svn")
        val svnModule = dir("ws/svnBuild/src")
        val plain = dir("ws/plain")

        val roots = VcsRoots.find(listOf(gitModule, svnModule, plain), boundaries = listOf(ws))

        assertEquals(
            listOf(VcsRoot(ws.resolve("gitBuild"), VcsKind.GIT), VcsRoot(ws.resolve("svnBuild"), VcsKind.SVN)),
            roots,
        )
    }

    @Test
    fun `git worktree file counts and duplicates are merged`() {
        val ws = dir("ws")
        val build = dir("ws/build")
        Files.writeString(build.resolve(".git"), "gitdir: /elsewhere")
        val roots = VcsRoots.find(listOf(build, dir("ws/build/a"), dir("ws/build/b")), listOf(ws))
        assertEquals(listOf(VcsRoot(build, VcsKind.GIT)), roots)
    }

    @Test
    fun `boundary and everything above are ignored`() {
        dir("home/.git")
        val home = tmp.root.toPath().resolve("home")
        assertTrue(VcsRoots.find(listOf(dir("home/projects/x")), listOf(home)).isEmpty())
    }

    @Test
    fun `only roots without any mapping are missing`() {
        val base = Path.of("/ws/project")
        val roots = listOf(
            VcsRoot(Path.of("/ws/a"), VcsKind.GIT),
            VcsRoot(Path.of("/ws/b"), VcsKind.SVN),
            VcsRoot(Path.of("/ws/c"), VcsKind.GIT),
            VcsRoot(base, VcsKind.GIT),
        )
        val existing = listOf(
            Mapping("/ws/a", "Git"),
            Mapping("/ws/b/", ""),   // explicitly "none": left alone
            Mapping("", "Git"),      // the project folder
        )
        assertEquals(listOf(VcsRoot(Path.of("/ws/c"), VcsKind.GIT)), VcsRoots.missing(existing, roots, base))
    }
}
