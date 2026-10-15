package de.inetsoftware.workspacedefaults

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Workspace defaults, stored in the project's `.idea/workspaceDefaults.xml`. With the Multi-Project Workspace
 * plugin this is the workspace project, whose linked Gradle builds and modules are its subprojects.
 * Every `manage…` flag that is off leaves the corresponding setting alone.
 */
class WorkspaceDefaultsState : BaseState() {
    var manageDistribution by property(false)
    var distributionType by string("DEFAULT_WRAPPED")
    var gradleHome by string()

    var manageGradleJvm by property(false)
    var gradleJvm by string("#USE_PROJECT_JDK")

    var manageBuildRunner by property(false)
    var delegatedBuild by property(true)

    var manageTestRunner by property(false)
    var testRunner by string("GRADLE")

    /** Give newly linked Gradle builds the defaults and map their Git/SVN roots right away. */
    var applyToNewBuilds by property(true)

    var manageProjectSdk by property(false)
    var projectSdk by string()

    var manageLanguageLevel by property(false)
    /** A [com.intellij.pom.java.LanguageLevel] name or [SDK_DEFAULT]. */
    var languageLevel by string(SDK_DEFAULT)

    var manageEncoding by property(false)
    var encoding by string("UTF-8")
    var propertiesEncoding by string("UTF-8")
    var native2Ascii by property(false)

    var manageLineSeparator by property(false)
    /** A [LineSeparator] name; stored by name because XML attributes lose raw line breaks. */
    var lineSeparator by string(LineSeparator.LF.name)

    var excludeFolders by property(false)
    var excludePatterns by string("bin, out")

    var assignVcsRoots by property(false)

    // IntelliJ IDEA's own build; on by default with the values Gradle and Eclipse effectively use
    var manageNotNullAssertions by property(true)
    var notNullAssertions by property(false)
    var manageDeprecation by property(true)
    var deprecation by property(false)
    /** Default processor profile from the classpath, processing off for modules that contain a processor. */
    var manageAnnotationProcessing by property(true)

    companion object {
        const val SDK_DEFAULT = "SDK_DEFAULT"
    }
}

enum class LineSeparator(val separator: String, val label: String) {
    LF("\n", "LF (Unix and macOS)"),
    CRLF("\r\n", "CRLF (Windows)"),
    CR("\r", "CR (classic Mac OS)");

    companion object {
        fun of(separator: String?): LineSeparator? = entries.firstOrNull { it.separator == separator }
    }
}

@Service(Service.Level.PROJECT)
@State(name = "WorkspaceDefaults", storages = [Storage("workspaceDefaults.xml")])
class WorkspaceDefaultsSettings : SimplePersistentStateComponent<WorkspaceDefaultsState>(WorkspaceDefaultsState()) {
    companion object {
        fun getInstance(project: Project): WorkspaceDefaultsSettings = project.getService(WorkspaceDefaultsSettings::class.java)
    }
}
