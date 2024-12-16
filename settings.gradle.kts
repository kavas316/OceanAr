// Proje Seviyesi build.gradle (root level)
pluginManagement {
    repositories {
        google() // Google reposunun burada olduğundan emin olun
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


rootProject.name = "OceanAr"
include(":app")
