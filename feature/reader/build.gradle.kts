plugins {
    id("tsundoku.android.library")
    id("tsundoku.android.compose")
    id("tsundoku.android.hilt")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.feature.reader"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    // Readium's EPUB and PDF navigators are fragments built on AppCompat and Material.
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.readium.shared)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium.navigator)

    testImplementation(libs.junit)
}
