plugins {
    id("tsundoku.android.library")
    id("tsundoku.android.compose")
    id("tsundoku.android.hilt")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.feature.settings"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
