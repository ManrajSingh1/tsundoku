pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Readium's PDFium adapter takes its PDFium build from a personal GitHub fork
        // (com.github.marain87) that is published nowhere else. Limited to that group so
        // nothing else can ever resolve from JitPack. See docs/decisions/0002.
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.marain87")
            }
        }
    }
}

rootProject.name = "tsundoku"

include(":app")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":feature:library")
include(":feature:import")
include(":feature:reader")
include(":feature:settings")
