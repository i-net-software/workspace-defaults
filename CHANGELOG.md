# Changelog

## [Unreleased]

### Fixed

- Only modules whose own source folders contain an annotation processor get processing disabled; test and mock modules of the same project keep it, so they generate their metadata.

## [1.1.0]

### Added

- Compiler defaults for IntelliJ IDEA's own build, managed by default: runtime assertions for @NotNull off, *Report use of deprecated features* off, annotation processing with processors from the classpath and a disabled profile for modules that contain a processor (restored after every Gradle sync).

## [1.0.0]

### Added

- Workspace-wide defaults for all linked Gradle builds: Gradle distribution (wrapper, local installation, bundled), Gradle JVM, *Build and run using* and *Run tests using*.
- Workspace project defaults: project SDK, language level, project and properties files encoding, line separator.
- Exclude folders by name (e.g. Eclipse `bin`) from indexing without changing the modules.
- Map Git and SVN working copies without a VCS mapping; SVN requires the optional Subversion plugin.
- Settings page listing every build and setting that deviates from the defaults, with *Apply to All Builds Now*.
- Newly linked Gradle builds get the defaults and their VCS mappings automatically (can be turned off).
