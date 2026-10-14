package de.inetsoftware.workspacedefaults

/** Folder name patterns like `bin, out, *.tmp` (`*` and `?` wildcards, case-sensitive). */
class ExcludePatterns(text: String?) {
    val patterns: List<String> = text.orEmpty().split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private val regexes = patterns.map { pattern ->
        Regex(pattern.split('*').joinToString(".*") { part -> part.split('?').joinToString(".") { Regex.escape(it) } })
    }

    /** Patterns without wildcards, which can be excluded before the folder exists. */
    val literals: List<String> = patterns.filter { '*' !in it && '?' !in it }

    val hasWildcards: Boolean get() = literals.size != patterns.size

    fun matches(name: String): Boolean = regexes.any { it.matches(name) }

    companion object {
        /**
         * A folder must not be excluded when it is a source/resource root or contains one,
         * e.g. a Gradle source set that happens to live in `bin`.
         */
        fun isProtected(url: String, sourceUrls: Collection<String>): Boolean =
            sourceUrls.any { it == url || it.startsWith("$url/") }
    }
}
