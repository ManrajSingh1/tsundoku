pluginManagement {
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
        // Spike only. Readium's PDFium adapter pulls a PDFium build from a personal GitHub
        // fork (com.github.marain87) that is published nowhere else. Limited to that group.
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "tsundoku"

// Phase 0 only: a throwaway app that proves the risky parts (3D carousel, PDF engines).
// It is deleted when Gate 0 closes and the real modules arrive.
include(":spike")
