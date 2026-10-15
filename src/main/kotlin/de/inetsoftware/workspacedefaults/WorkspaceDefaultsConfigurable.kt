package de.inetsoftware.workspacedefaults

import com.intellij.ide.DataManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import java.nio.charset.Charset
import javax.swing.table.DefaultTableModel

class WorkspaceDefaultsConfigurable(private val project: Project) :
    BoundSearchableConfigurable("Workspace Defaults", "workspace.defaults", ID) {

    private val state get() = WorkspaceDefaultsSettings.getInstance(project).state
    private val tableModel = object : DefaultTableModel(arrayOf("Build / folder", "Setting", "Current", "Workspace default"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val summary = JBLabel()
    private val svnHint = JBLabel()

    override fun createPanel(): DialogPanel {
        val s = state
        val javaSdks = ProjectJdkTable.getInstance().allJdks.filter { it.sdkType is JavaSdkType }.map { it.name }
        // The combo box needs a stored value: a value shown only as a fallback is never saved,
        // because the UI sees no change. Preselect the current project SDK (no effect while unmanaged).
        if (s.projectSdk == null) s.projectSdk = ProjectRootManager.getInstance(project).projectSdkName ?: javaSdks.firstOrNull()
        return panel {
            group("Gradle (All Linked Builds)") {
                lateinit var distribution: com.intellij.ui.dsl.builder.Cell<com.intellij.openapi.ui.ComboBox<String>>
                row {
                    val manage = checkBox("Gradle distribution:").bindSelected(s::manageDistribution)
                    distribution = comboBox(DISTRIBUTIONS.keys.toList(), labelRenderer(DISTRIBUTIONS))
                        .bindItem({ s.distributionType ?: "DEFAULT_WRAPPED" }, { s.distributionType = it })
                        .enabledIf(manage.component.selected)
                    val local = distribution.component.selectedValueMatches { it == Deviations.LOCAL_DISTRIBUTION }
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Gradle Home"), project)
                        .bindText({ s.gradleHome.orEmpty() }, { s.gradleHome = it.ifBlank { null } })
                        .columns(30)
                        .visibleIf(local)
                        .enabledIf(manage.component.selected)
                }
                row {
                    val manage = checkBox("Gradle JVM:").bindSelected(s::manageGradleJvm)
                    val jvms = linkedMapOf(
                        ExternalSystemJdkUtil.USE_PROJECT_JDK to "Project SDK",
                        ExternalSystemJdkUtil.USE_JAVA_HOME to "JAVA_HOME",
                    ) + javaSdks.associateWith { it } + listOfNotNull(s.gradleJvm).associateWith { it }.filterKeys { it !in javaSdks }
                    comboBox(jvms.keys.toList(), labelRenderer(jvms))
                        .bindItem({ s.gradleJvm ?: ExternalSystemJdkUtil.USE_PROJECT_JDK }, { s.gradleJvm = it })
                        .enabledIf(manage.component.selected)
                }
                row {
                    val manage = checkBox("Build and run using:").bindSelected(s::manageBuildRunner)
                    comboBox(listOf(true, false), textListCellRenderer { it?.let(Deviations::runner).orEmpty() })
                        .bindItem({ s.delegatedBuild }, { s.delegatedBuild = it ?: true })
                        .enabledIf(manage.component.selected)
                }
                row {
                    val manage = checkBox("Run tests using:").bindSelected(s::manageTestRunner)
                    comboBox(TEST_RUNNERS.keys.toList(), labelRenderer(TEST_RUNNERS))
                        .bindItem({ s.testRunner ?: "GRADLE" }, { s.testRunner = it })
                        .enabledIf(manage.component.selected)
                }
                row {
                    checkBox("Apply to newly linked builds automatically").bindSelected(s::applyToNewBuilds)
                }.rowComment("Existing builds are only overwritten when you click Apply.")
            }

            group("Workspace Project") {
                row {
                    val manage = checkBox("Project SDK:").bindSelected(s::manageProjectSdk)
                    val sdks = (javaSdks + listOfNotNull(s.projectSdk)).distinct()
                    comboBox(sdks, textListCellRenderer { it.orEmpty() })
                        .bindItem(s::projectSdk)
                        .enabledIf(manage.component.selected)
                }
                row {
                    val manage = checkBox("Language level:").bindSelected(s::manageLanguageLevel)
                    val levels = listOf(WorkspaceDefaultsState.SDK_DEFAULT) + LanguageLevel.entries.filter { !it.isPreview }.reversed().map { it.name }
                    comboBox(levels, textListCellRenderer { it?.let(ProjectDefaults::languageLevelLabel).orEmpty() })
                        .bindItem({ s.languageLevel ?: WorkspaceDefaultsState.SDK_DEFAULT }, { s.languageLevel = it })
                        .enabledIf(manage.component.selected)
                }
                lateinit var manageEncoding: com.intellij.ui.dsl.builder.Cell<com.intellij.ui.components.JBCheckBox>
                row {
                    manageEncoding = checkBox("Project encoding:").bindSelected(s::manageEncoding)
                    comboBox(CHARSETS, textListCellRenderer { it.orEmpty() })
                        .bindItem({ s.encoding ?: "UTF-8" }, { s.encoding = it })
                        .enabledIf(manageEncoding.component.selected)
                    label("Properties files:")
                    comboBox(CHARSETS, textListCellRenderer { it.orEmpty() })
                        .bindItem({ s.propertiesEncoding ?: "UTF-8" }, { s.propertiesEncoding = it })
                        .enabledIf(manageEncoding.component.selected)
                    checkBox("Transparent native-to-ASCII").bindSelected(s::native2Ascii).enabledIf(manageEncoding.component.selected)
                }
                row {
                    val manage = checkBox("Line separator:").bindSelected(s::manageLineSeparator)
                    comboBox(LineSeparator.entries.map { it.name }, textListCellRenderer { it?.let { v -> LineSeparator.valueOf(v).label }.orEmpty() })
                        .bindItem({ s.lineSeparator ?: LineSeparator.LF.name }, { s.lineSeparator = it })
                        .enabledIf(manage.component.selected)
                }.rowComment("Stored in the project code style; an IDE-level scheme is copied into a project scheme first.")
            }

            group("Indexing") {
                row {
                    val manage = checkBox("Exclude folders named:").bindSelected(s::excludeFolders)
                    textField().bindText({ s.excludePatterns.orEmpty() }, { s.excludePatterns = it })
                        .columns(30)
                        .enabledIf(manage.component.selected)
                }.rowComment(
                    "Comma-separated, wildcards * and ?. Applies in every module content root and one level below; " +
                        "folders that contain source roots are kept. Takes effect immediately, nothing is written to the modules.",
                )
            }

            group("Compiler (IntelliJ IDEA Build)") {
                row {
                    val manage = checkBox("Runtime assertions for @NotNull:").bindSelected(s::manageNotNullAssertions)
                    comboBox(listOf(false, true), textListCellRenderer { if (it == true) "On" else "Off" })
                        .bindItem({ s.notNullAssertions }, { s.notNullAssertions = it ?: false })
                        .enabledIf(manage.component.selected)
                }.rowComment("Off like Gradle and Eclipse; on, a null argument for a @Nonnull parameter fails at run time.")
                row {
                    val manage = checkBox("Report use of deprecated features:").bindSelected(s::manageDeprecation)
                    comboBox(listOf(false, true), textListCellRenderer { if (it == true) "On" else "Off" })
                        .bindItem({ s.deprecation }, { s.deprecation = it ?: false })
                        .enabledIf(manage.component.selected)
                }.rowComment("javac -deprecation can fail on libraries whose annotation classes are missing from the classpath.")
                row {
                    checkBox("Annotation processing: processors from the classpath, off for modules that contain a processor")
                        .bindSelected(s::manageAnnotationProcessing)
                }.rowComment(
                    "Enables the default profile and adds the modules whose sources contain <code>${ProcessorModules.SERVICE_FILE}</code>, " +
                        "with the modules of the same project that depend on them, to the disabled profile \"${CompilerDefaults.NO_PROCESSING_PROFILE}\". " +
                        "Kept up to date after every Gradle sync.",
                )
            }

            group("Version Control") {
                row {
                    checkBox("Map Git and SVN working copies that have no VCS mapping yet").bindSelected(s::assignVcsRoots)
                }.rowComment("Existing mappings are never changed.")
                row { cell(svnHint) }
                row {
                    link("Install Subversion plugin…") { openSubversionInMarketplace(it.source as java.awt.Component) }
                }.visible(!VcsDefaults.isAvailable(project, VcsKind.SVN))
            }

            group("Deviations from the Workspace Defaults") {
                row {
                    cell(summary)
                    button("Refresh") { refreshReport() }
                    button("Apply to All Builds Now") { apply(); refreshReport() }
                }
                row {
                    val table = JBTable(tableModel).apply { setShowGrid(false); emptyText.text = "No deviations" }
                    cell(JBScrollPane(table).apply { preferredSize = Dimension(700, 220) })
                        .align(Align.FILL)
                }.resizableRow()
            }
        }.also { refreshReport() }
    }

    override fun apply() {
        val excludeBefore = state.excludeFolders to state.excludePatterns
        super.apply()
        WorkspaceDefaultsService.getInstance(project).applyAll(rescanIndex = excludeBefore != (state.excludeFolders to state.excludePatterns))
        refreshReport()
    }

    private fun refreshReport() {
        val report = WorkspaceDefaultsService.getInstance(project).report()
        tableModel.rowCount = 0
        report.deviations.forEach { tableModel.addRow(arrayOf(it.scope, it.setting, label(it.setting, it.current), label(it.setting, it.expected))) }
        val deviatingBuilds = report.deviations.map { it.scope }.distinct().size
        summary.text = "${report.builds} linked Gradle builds, ${report.deviations.size} deviations in $deviatingBuilds builds or folders"
        val svnAvailable = VcsDefaults.isAvailable(project, VcsKind.SVN)
        val unsupported = report.vcs?.unsupported.orEmpty().size
        svnHint.text = when {
            svnAvailable -> "Subversion plugin installed: SVN working copies are mapped too."
            unsupported > 0 -> "Subversion plugin not installed: $unsupported SVN working copies cannot be mapped."
            else -> "Subversion plugin not installed: only Git working copies are mapped."
        }
    }

    private fun label(setting: String, value: String): String = when (setting) {
        "Gradle distribution" -> DISTRIBUTIONS[value] ?: value
        "Run tests using" -> TEST_RUNNERS[value] ?: value
        "Gradle JVM" -> when (value) {
            ExternalSystemJdkUtil.USE_PROJECT_JDK -> "Project SDK"
            ExternalSystemJdkUtil.USE_JAVA_HOME -> "JAVA_HOME"
            else -> value
        }
        else -> value
    }

    /** Opens the Plugins page with "Subversion" in its search field. */
    private fun openSubversionInMarketplace(component: java.awt.Component) {
        val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(component))
        val plugins = settings?.find(PLUGINS_CONFIGURABLE_ID)
        if (settings != null && plugins != null) settings.select(plugins, VcsDefaults.SUBVERSION_PLUGIN_ID)
        else ShowSettingsUtil.getInstance().showSettingsDialog(project, "Plugins")
    }

    private fun <T> labelRenderer(labels: Map<T, String>): javax.swing.ListCellRenderer<T?> = textListCellRenderer<T?> { it?.let { v -> labels[v] ?: v.toString() }.orEmpty() }

    companion object {
        const val ID = "de.inetsoftware.workspacedefaults"
        private const val PLUGINS_CONFIGURABLE_ID = "preferences.pluginManager"

        private val DISTRIBUTIONS = linkedMapOf(
            "DEFAULT_WRAPPED" to "Wrapper (gradle-wrapper.properties)",
            "LOCAL" to "Local installation",
            "BUNDLED" to "Bundled with IntelliJ IDEA",
            "WRAPPED" to "Wrapper task",
        )
        private val TEST_RUNNERS = linkedMapOf(
            "GRADLE" to "Gradle",
            "PLATFORM" to "IntelliJ IDEA",
            "CHOOSE_PER_TEST" to "Choose per test",
        )
        private val CHARSETS: List<String> =
            (listOf("UTF-8", "ISO-8859-1", "windows-1252", "US-ASCII", "UTF-16") + Charset.availableCharsets().keys).distinct()
    }
}
