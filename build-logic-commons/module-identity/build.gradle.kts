plugins {
    `kotlin-dsl`
}

description = "Provides a plugin to define the version and name for subproject publications"

group = "gradlebuild"

dependencies {
    api(platform(projects.buildPlatform))

    // Exposed so downstream build-logic projects can use the GeneratePomProperties task type
    // for the distribution jars that are not the standard `jar` (shaded, ABI, metadata jars).
    api(buildLibs.pomPropertiesPlugin)

    implementation(projects.basics)

    implementation(buildLibs.gson)
}
