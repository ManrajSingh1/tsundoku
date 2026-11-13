plugins {
    id("tsundoku.android.library")
    id("tsundoku.android.compose")
    id("tsundoku.android.hilt")
}

android {
    // "import" is a Kotlin keyword, so the package is named for what the feature does.
    namespace = "io.github.manrajsingh1.tsundoku.feature.importing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
