package de.inetsoftware.workspacedefaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviationsTest {
    private val current = GradleValues("DEFAULT_WRAPPED", null, "corretto-21", true, "GRADLE")

    @Test
    fun `unmanaged settings never deviate`() {
        assertTrue(Deviations.gradle("a", GradleValues(), current).isEmpty())
    }

    @Test
    fun `reports only managed settings that differ`() {
        val defaults = GradleValues(distribution = "DEFAULT_WRAPPED", gradleJvm = "#USE_PROJECT_JDK", delegatedBuild = false)
        val result = Deviations.gradle("a", defaults, current)
        assertEquals(listOf("Gradle JVM", "Build and run using"), result.map { it.setting })
        assertEquals(Deviation("a", "Build and run using", "Gradle", "IntelliJ IDEA"), result[1])
    }

    @Test
    fun `gradle home only counts for a local installation`() {
        val wrapper = GradleValues(distribution = "DEFAULT_WRAPPED", gradleHome = "/opt/gradle")
        assertTrue(Deviations.gradle("a", wrapper, current).isEmpty())

        val local = GradleValues(distribution = "LOCAL", gradleHome = "/opt/gradle/")
        assertEquals(listOf("Gradle distribution", "Gradle home"), Deviations.gradle("a", local, current).map { it.setting })
        // Trailing slashes and backslashes do not count as a difference
        val sameHome = current.copy(distribution = "LOCAL", gradleHome = "\\opt\\gradle")
        assertTrue(Deviations.gradle("a", local, sameHome).isEmpty())
    }

    @Test
    fun `missing current value is shown as not set`() {
        val result = Deviations.of("ws", listOf(SettingValue("Project SDK", "jdk-25", null)))
        assertEquals(Deviations.NOT_SET, result.single().current)
    }
}
