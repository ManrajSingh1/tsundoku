// Adds Jetpack Compose to an Android module. Apply it after the application or library plugin.
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        buildFeatures {
            compose = true
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        buildFeatures {
            compose = true
        }
    }
}

dependencies {
    add("implementation", platform(catalog.findLibrary("androidx-compose-bom").get()))
    add("implementation", catalog.findLibrary("androidx-compose-ui").get())
    add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
    add("implementation", catalog.findLibrary("androidx-compose-foundation").get())
    add("implementation", catalog.findLibrary("androidx-compose-material3").get())
}
