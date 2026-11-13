// Adds a Room database to an Android library module. Apply it after the library plugin.
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

// Each schema version is written here as JSON and committed, so a migration can always be
// tested against the exact schema a real install has.
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("api", catalog.findLibrary("androidx-room3-runtime").get())
    add("implementation", catalog.findLibrary("androidx-sqlite-framework").get())
    add("ksp", catalog.findLibrary("androidx-room3-compiler").get())
}
