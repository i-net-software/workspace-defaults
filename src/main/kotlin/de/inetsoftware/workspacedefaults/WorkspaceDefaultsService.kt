package de.inetsoftware.workspacedefaults

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Everything that differs from the workspace defaults, for the settings page. */
data class Report(val builds: Int, val deviations: List<Deviation>, val vcs: VcsDefaults.Scan?)

@Service(Service.Level.PROJECT)
class WorkspaceDefaultsService(private val project: Project) : Disposable {
    /** Builds linked in this session that still wait for their first import (to map their VCS roots). */
    private val newBuilds = ConcurrentHashMap.newKeySet<String>()

    private val state get() = WorkspaceDefaultsSettings.getInstance(project).state

    /** Collects the deviations; reads the module model, so call it in a read action or on the EDT. */
    fun report(): Report {
        val s = state
        val vcs = if (s.assignVcsRoots) VcsDefaults.scan(project) else null
        return Report(
            builds = GradleDefaults.linkedBuilds(project).size,
            deviations = GradleDefaults.deviations(project, s) + ProjectDefaults.deviations(project, s) + vcs?.let(VcsDefaults::deviations).orEmpty(),
            vcs = vcs,
        )
    }

    /** Overwrites all managed settings of all builds and the workspace project. Runs on the EDT. */
    fun applyAll(rescanIndex: Boolean) {
        val s = state
        val builds = GradleDefaults.applyAll(project, s)
        val projectSettings = ProjectDefaults.applyAll(project, s)
        val vcs = if (s.assignVcsRoots) VcsDefaults.scan(project) else null
        vcs?.let { VcsDefaults.addMappings(project, it.missing) }
        if (rescanIndex) FolderExcludePolicy.rescan(project)
        notifyApplied(builds, projectSettings, vcs)
    }

    internal fun onProjectsLinked(settings: Collection<GradleProjectSettings>) {
        val s = state
        if (!s.applyToNewBuilds) return
        val defaults = GradleDefaults.defaults(s)
        settings.forEach { ps ->
            if (GradleDefaults.applyTo(ps, defaults)) thisLogger().info("Workspace defaults applied to new build ${ps.externalProjectPath}")
            if (s.assignVcsRoots) newBuilds += ps.externalProjectPath
        }
    }

    internal fun onImportFinished(projectPath: String?) {
        if (projectPath == null || !newBuilds.remove(projectPath) || !state.assignVcsRoots) return
        ReadAction.nonBlocking<VcsDefaults.Scan> { VcsDefaults.scan(project, Path.of(projectPath).toAbsolutePath().normalize()) }
            .expireWith(this)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.nonModal()) { scan ->
                VcsDefaults.addMappings(project, scan.missing)
                if (scan.missing.isNotEmpty() || scan.unsupported.isNotEmpty()) notifyVcs(scan)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun notifyApplied(builds: Int, projectSettings: List<String>, vcs: VcsDefaults.Scan?) {
        val lines = buildList {
            val total = GradleDefaults.linkedBuilds(project).size
            add(if (builds == 0) "All $total Gradle builds already use the defaults" else "Gradle settings changed in $builds of $total builds")
            if (projectSettings.isNotEmpty()) add("Workspace: ${projectSettings.joinToString()}")
            vcs?.let { if (it.missing.isNotEmpty()) add("${it.missing.size} VCS roots mapped") }
            vcs?.let(::unsupportedHint)?.let(::add)
        }
        val notification = group().createNotification("Workspace defaults applied", lines.joinToString("<br>"), NotificationType.INFORMATION)
        // Changed Gradle settings take effect with the next sync
        notification.addAction(NotificationAction.createSimpleExpiring("Reload All Gradle Projects") {
            ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
        })
        notification.notify(project)
    }

    private fun notifyVcs(scan: VcsDefaults.Scan) {
        val text = listOfNotNull(
            "${scan.missing.size} VCS roots mapped".takeIf { scan.missing.isNotEmpty() },
            unsupportedHint(scan),
        ).joinToString("<br>")
        group().createNotification("Workspace defaults: new build", text, NotificationType.INFORMATION).notify(project)
    }

    private fun unsupportedHint(scan: VcsDefaults.Scan): String? {
        val svn = scan.unsupported.count { it.kind == VcsKind.SVN }
        return if (svn == 0) null
        else "$svn SVN working copies not mapped: install the Subversion plugin (Settings | Plugins | Marketplace)"
    }

    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Workspace Defaults")

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): WorkspaceDefaultsService = project.getService(WorkspaceDefaultsService::class.java)
    }
}

/** Gives newly linked Gradle builds the workspace defaults and maps their VCS roots after the first import. */
class WorkspaceDefaultsStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = WorkspaceDefaultsService.getInstance(project)
        GradleSettings.getInstance(project).subscribe(object : ExternalSystemSettingsListener<GradleProjectSettings> {
            override fun onProjectsLinked(settings: Collection<GradleProjectSettings>) = service.onProjectsLinked(settings)
        }, service)
        project.messageBus.connect(service).subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
            override fun onImportFinished(projectPath: String?) {
                ApplicationManager.getApplication().invokeLater({ service.onImportFinished(projectPath) }, project.disposed)
            }
        })
    }
}
