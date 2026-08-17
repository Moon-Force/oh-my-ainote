pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "oh-my-ainote"

include(":app")
include(":document")
include(":ink")
include(":pdf")
include(":ai-api")
include(":ai")
include(":export")
