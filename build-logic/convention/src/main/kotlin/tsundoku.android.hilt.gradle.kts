// Adds Hilt to an Android module. Apply it after the application or library plugin.
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", catalog.findLibrary("hilt-android").get())
    add("ksp", catalog.findLibrary("hilt-compiler").get())
}
