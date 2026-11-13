// Every Android library module. Kotlin support is built into the Android Gradle plugin from 9.0.
plugins {
    id("com.android.library")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

android {
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Readium's libraries are built against newer java.* APIs and refuse to link without this.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    add("coreLibraryDesugaring", catalog.findLibrary("android-desugar-jdk-libs").get())
}
