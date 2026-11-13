plugins {
    id("tsundoku.android.library")
    id("tsundoku.android.hilt")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.core.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.coroutines.android)

    // Readium reads the metadata and cover of all three formats at import.
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.adapter.pdfium.document)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
