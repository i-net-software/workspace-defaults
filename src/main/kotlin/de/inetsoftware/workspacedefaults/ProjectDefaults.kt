package de.inetsoftware.workspacedefaults

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import java.nio.charset.Charset

/** Project SDK, language level, encodings and line separator of the workspace project. */
object ProjectDefaults {
    const val SCOPE = "(workspace)"

    fun deviations(project: Project, s: WorkspaceDefaultsState): List<Deviation> {
        val encodings = EncodingProjectManager.getInstance(project)
        val languageLevel = LanguageLevelProjectExtension.getInstance(project)
        return Deviations.of(SCOPE, listOf(
            SettingValue("Project SDK", s.projectSdk.takeIf { s.manageProjectSdk }, ProjectRootManager.getInstance(project).projectSdkName),
            SettingValue(
                "Language level",
                s.languageLevel?.let(::languageLevelLabel).takeIf { s.manageLanguageLevel },
                languageLevelLabel(if (languageLevel.isDefault) WorkspaceDefaultsState.SDK_DEFAULT else languageLevel.languageLevel.name),
            ),
            SettingValue("Project encoding", s.encoding.takeIf { s.manageEncoding }, encodings.defaultCharsetName),
            SettingValue(
                "Properties files encoding",
                s.propertiesEncoding.takeIf { s.manageEncoding },
                encodings.getDefaultCharsetForPropertiesFiles(null)?.name(),
            ),
            SettingValue(
                "Transparent native-to-ASCII",
                s.native2Ascii.toString().takeIf { s.manageEncoding },
                encodings.isNative2AsciiForPropertiesFiles.toString(),
            ),
            SettingValue(
                "Line separator",
                LineSeparator.entries.firstOrNull { it.name == s.lineSeparator }?.label.takeIf { s.manageLineSeparator },
                currentLineSeparator(project)?.label ?: "System-dependent",
            ),
        ))
    }

    /** Overwrites the managed project settings; returns the labels of the settings that differed before. */
    fun applyAll(project: Project, s: WorkspaceDefaultsState): List<String> {
        val written = deviations(project, s).map { it.setting }
        applySdkAndLanguageLevel(project, s)
        if (s.manageEncoding) {
            val encodings = EncodingProjectManager.getInstance(project)
            s.encoding?.let { encodings.defaultCharsetName = it }
            s.propertiesEncoding?.let { name -> charset(name)?.let { encodings.setDefaultCharsetForPropertiesFiles(null, it) } }
            encodings.setNative2AsciiForPropertiesFiles(null, s.native2Ascii)
        }
        if (s.manageLineSeparator) {
            LineSeparator.entries.firstOrNull { it.name == s.lineSeparator }?.let { setLineSeparator(project, it) }
        }
        return written
    }

    /**
     * Sets project SDK and language level, but only where they differ, because every change of the
     * language level triggers a rescan. Returns whether anything changed.
     */
    fun applySdkAndLanguageLevel(project: Project, s: WorkspaceDefaultsState): Boolean {
        var changed = false
        if (s.manageProjectSdk) {
            val sdk = s.projectSdk?.let { ProjectJdkTable.getInstance().findJdk(it) }
            val roots = ProjectRootManager.getInstance(project)
            if (sdk != null && roots.projectSdk != sdk) {
                WriteAction.run<RuntimeException> { roots.projectSdk = sdk }
                changed = true
            }
        }
        if (s.manageLanguageLevel) {
            val ext = LanguageLevelProjectExtension.getInstance(project)
            val level = s.languageLevel?.takeIf { it != WorkspaceDefaultsState.SDK_DEFAULT }
                ?.let { runCatching { LanguageLevel.valueOf(it) }.getOrNull() }
            val differs = if (level == null) !ext.isDefault else ext.isDefault || ext.languageLevel != level
            if (differs) {
                WriteAction.run<RuntimeException> {
                    if (level == null) ext.default = true
                    else {
                        ext.languageLevel = level
                        ext.default = false
                    }
                }
                changed = true
            }
        }
        return changed
    }

    fun languageLevelLabel(name: String): String =
        if (name == WorkspaceDefaultsState.SDK_DEFAULT) "SDK default"
        else runCatching { LanguageLevel.valueOf(name).presentableText }.getOrDefault(name)

    private fun charset(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun currentLineSeparator(project: Project): LineSeparator? =
        LineSeparator.of(CodeStyle.getSettings(project).LINE_SEPARATOR)

    /**
     * The line separator is part of the code style. To keep it per workspace, an IDE-level scheme is
     * first copied into a project scheme (`.idea/codeStyles`) instead of changing the IDE-wide scheme.
     */
    private fun setLineSeparator(project: Project, separator: LineSeparator) {
        val manager = CodeStyleSettingsManager.getInstance(project)
        if (!manager.USE_PER_PROJECT_SETTINGS) {
            val copy = manager.cloneSettings(CodeStyle.getSettings(project))
            copy.LINE_SEPARATOR = separator.separator
            manager.setMainProjectCodeStyle(copy)
            manager.USE_PER_PROJECT_SETTINGS = true
        } else {
            CodeStyle.getSettings(project).LINE_SEPARATOR = separator.separator
        }
        manager.notifyCodeStyleSettingsChanged()
    }
}
