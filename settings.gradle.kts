pluginManagement {
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
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
include(":hwr")
