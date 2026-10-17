# Liquid Glass UI library snapshot

This directory contains the `liquidglass` Android library used by this project's settings screen, copied from the existing local Liquid Glass component project so this repository builds without an absolute path to another checkout.

The source files and library build configuration are preserved as used by the module. Package names retain their original namespaces. Dependencies declared in `build.gradle.kts` are resolved from the project's Maven repositories. The original host-specific `LiquidGlassPrefs.kt` remains excluded by the library build configuration.
