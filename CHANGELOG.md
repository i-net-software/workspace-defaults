# Changelog

## [Unreleased]

### Added

- Workspace-wide defaults for all linked Gradle builds: Gradle distribution (wrapper, local installation, bundled), Gradle JVM, *Build and run using* and *Run tests using*.
- Workspace project defaults: project SDK, language level, project and properties files encoding, line separator.
- Exclude folders by name (e.g. Eclipse `bin`) from indexing without changing the modules.
- Map Git and SVN working copies without a VCS mapping; SVN requires the optional Subversion plugin.
- Settings page listing every build and setting that deviates from the defaults, with *Apply to All Builds Now*.
- Newly linked Gradle builds get the defaults and their VCS mappings automatically (can be turned off).
