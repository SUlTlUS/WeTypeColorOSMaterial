pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "WeTypeColorOSMaterial"
include(":app", ":api-102")
include(":liquidglass")
project(":liquidglass").projectDir = file("liquidglass")
