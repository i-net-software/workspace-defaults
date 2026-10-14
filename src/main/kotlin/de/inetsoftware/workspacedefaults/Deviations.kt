package de.inetsoftware.workspacedefaults

/** One setting of a build or of the workspace that differs from the workspace default. */
data class Deviation(val scope: String, val setting: String, val current: String, val expected: String)

/** A managed setting: [expected] is null when the workspace does not manage it. */
data class SettingValue(val label: String, val expected: String?, val current: String?)

/** Gradle settings of one linked build as plain values; in the defaults, null means "not managed". */
data class GradleValues(
    val distribution: String? = null,
    val gradleHome: String? = null,
    val gradleJvm: String? = null,
    val delegatedBuild: Boolean? = null,
    val testRunner: String? = null,
)

object Deviations {
    const val LOCAL_DISTRIBUTION = "LOCAL"
    const val NOT_SET = "(not set)"

    fun of(scope: String, settings: List<SettingValue>): List<Deviation> = settings
        .filter { it.expected != null && normalize(it.expected) != normalize(it.current) }
        .map { Deviation(scope, it.label, it.current?.takeIf { v -> v.isNotBlank() } ?: NOT_SET, it.expected!!) }

    fun gradle(build: String, defaults: GradleValues, current: GradleValues): List<Deviation> = of(build, listOf(
        SettingValue("Gradle distribution", defaults.distribution, current.distribution),
        // The Gradle home only matters for a local installation
        SettingValue(
            "Gradle home",
            defaults.gradleHome.takeIf { defaults.distribution == LOCAL_DISTRIBUTION },
            current.gradleHome,
        ),
        SettingValue("Gradle JVM", defaults.gradleJvm, current.gradleJvm),
        SettingValue("Build and run using", defaults.delegatedBuild?.let(::runner), current.delegatedBuild?.let(::runner)),
        SettingValue("Run tests using", defaults.testRunner, current.testRunner),
    ))

    fun runner(delegated: Boolean) = if (delegated) "Gradle" else "IntelliJ IDEA"

    private fun normalize(value: String?): String = value.orEmpty().trim().replace('\\', '/').trimEnd('/')
}
