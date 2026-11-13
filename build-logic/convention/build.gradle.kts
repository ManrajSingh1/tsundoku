plugins {
    `kotlin-dsl`
}

// The Gradle plugins the convention plugins apply. Their versions live in the catalog.
dependencies {
    implementation(libs.gradle.plugin.android)
    implementation(libs.gradle.plugin.kotlin)
    implementation(libs.gradle.plugin.compose)
    implementation(libs.gradle.plugin.ksp)
    implementation(libs.gradle.plugin.hilt)
    implementation(libs.gradle.plugin.room)
}
