plugins {
    id("tsundoku.android.application")
    id("tsundoku.android.compose")
    id("tsundoku.android.hilt")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku"

    defaultConfig {
        applicationId = "io.github.manrajsingh1.tsundoku"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // A test key kept in the repository, so every CI build installs over the previous
        // one. It protects nothing. A Play release is signed with a key that never enters
        // this repository.
        create("testKey") {
            storeFile = file("keystore/test-key.jks")
            storePassword = "tsundoku-spike"
            keyAlias = "spike"
            keyPassword = "tsundoku-spike"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("testKey")
        }
        // The release build plus one extra screen that lets CI import files without working
        // a file picker. Minified exactly like release, so it tests what ships; installed
        // under its own id, so it never replaces the real app.
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".staging"
        }
        debug {
            signingConfig = signingConfigs.getByName("testKey")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    // Hilt assembles the object graph here, so the app must see every module it draws from.
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:library"))
    implementation(project(":feature:import"))
    implementation(project(":feature:reader"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
