# Workspace Defaults

[![CI](https://github.com/i-net-software/workspace-defaults/actions/workflows/ci.yml/badge.svg)](https://github.com/i-net-software/workspace-defaults/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

IntelliJ IDEA plugin that sets Gradle, project and VCS settings once for all builds of a workspace, like the workspace preferences of Eclipse.

## Why

A workspace with many linked Gradle builds has a Gradle distribution, a Gradle JVM and runner settings per build. In IntelliJ IDEA each of them has to be set on its own, and builds that use different Gradle JVMs or distributions cannot share a Gradle daemon. Projects coming from Eclipse also bring `bin` folders with compiled classes that get indexed, and Git or SVN checkouts that IntelliJ IDEA does not map automatically.

## What it manages

Each setting is only touched when its checkbox is on.

| Area | Settings |
|---|---|
| Gradle, for every linked build | Distribution (wrapper, local installation, bundled), Gradle JVM (any JDK, project SDK, `JAVA_HOME`), *Build and run using*, *Run tests using* |
| Workspace project | Project SDK, language level, project and properties files encoding, transparent native-to-ASCII, line separator |
| Indexing | Exclude folders by name (`bin, out`, wildcards `*` `?`) in every module content root and one level below |
| Version control | Add Git and SVN mappings for working copies that have none yet |

## How it works

* **Workspace**: the project opened in IntelliJ IDEA. With the [Multi-Project Workspace](https://plugins.jetbrains.com/plugin/24765-multi-project-workspace) plugin the subprojects are Gradle builds and modules linked into the workspace project, so the plugin works the same with or without it. The defaults are stored in `.idea/workspaceDefaults.xml`.
* **Apply** on the settings page overwrites the managed settings of all builds and of the workspace project. *Apply to All Builds Now* does the same without changing the defaults.
* **Gradle sync** does not undo the defaults: it sets the project SDK and language level only for a project with a single build in its own folder (`ExternalSystemApiUtil.isOneToOneMapping`), and the project SDK only when none is set. Module language levels keep coming from each build.
* **New builds**: when a Gradle build is linked, it gets the Gradle defaults right away, and its Git/SVN roots are mapped after its first sync. Can be turned off.
* **Gradle settings** are changed through `GradleSettings.setLinkedProjectsSettings()`, as the Gradle settings page does. A reload is needed for running Gradle daemons to pick them up; the notification offers *Reload All Gradle Projects*.
* **Excluded folders** come from a `DirectoryIndexExcludePolicy`: nothing is written to the modules, so a Gradle sync cannot undo it. Folders that are or contain source roots are never excluded.
* **VCS roots**: from every module content root and linked build the plugin walks up to the nearest folder with `.git` (folder or worktree file) or `.svn`; for SVN up to the top of the working copy. The home folder and everything above it are ignored. Existing mappings, including explicit *None*, are never changed.
* **Subversion** is not bundled with IntelliJ IDEA since 2026.2. Without the [Subversion](https://plugins.jetbrains.com/plugin/11553-subversion) plugin, SVN working copies are listed on the settings page but not mapped; the page links to the plugin in the Marketplace.
* **Line separator** is part of the code style. If the project uses an IDE-level scheme, it is first copied into a project scheme (`.idea/codeStyles`), so the IDE-wide scheme stays as it is.

## Installation

Download `workspace-defaults-<version>.zip` from the [latest release](https://github.com/i-net-software/workspace-defaults/releases/latest), then in IntelliJ IDEA 2026.2 or newer: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select the ZIP.

## Usage

**Settings → Build, Execution, Deployment → Build Tools → Workspace Defaults**

* Turn on the settings you want to manage and choose their values.
* The table at the bottom lists every build, folder and setting that deviates from the defaults.
* **Apply** / **OK** saves the defaults and overwrites the deviating settings.

## Building

Requires JDK 25.

```bash
./gradlew check buildPlugin    # tests and plugin ZIP in build/distributions
./gradlew runIde               # sandbox IDE without further plugins
./gradlew runIdeWithWorkspace  # sandbox IDE with Multi-Project Workspace and Subversion
```

## Release

Push a tag `v<version>` (e.g. `v1.0.0`, `v1.1.0-beta.1`). The release workflow builds the plugin with that version, creates a GitHub release with the ZIP and, once the `PUBLISH_TOKEN` secret is configured, publishes it to the JetBrains Marketplace.

## License

[Apache License 2.0](LICENSE)
