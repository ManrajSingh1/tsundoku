plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself is built into the Android Gradle plugin from 9.0; only the Compose compiler is applied here.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.spike"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Throwaway id for Phase 0. The real package id is still an open decision.
        applicationId = "io.github.manrajsingh1.tsundoku.spike"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    signingConfigs {
        // A test key kept in the repo so every CI build installs over the previous one.
        // It protects nothing and must never sign a Play release.
        create("spike") {
            storeFile = file("keystore/spike-test.jks")
            storePassword = "tsundoku-spike"
            keyAlias = "spike"
            keyPassword = "tsundoku-spike"
        }
    }

    buildTypes {
        // Frame rates are only meaningful on a minified, non-debuggable build.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("spike")
        }
        debug {
            signingConfig = signingConfigs.getByName("spike")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
