plugins {
    id("tsundoku.android.library")
    id("tsundoku.android.compose")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.core.ui"
}

dependencies {
    // The theme is chosen by a value from the model, so every caller sees that type too.
    api(project(":core:model"))

    testImplementation(libs.junit)
}
