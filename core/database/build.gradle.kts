plugins {
    id("tsundoku.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

android {
    namespace = "io.github.manrajsingh1.tsundoku.core.database"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// Each schema version is written here as JSON and committed, so a migration can always be
// tested against the exact schema a real install has.
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))

    api(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room3.compiler)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
