pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WM-Launcher"
include(":app")
if (file("validation/app-fixture/build.gradle.kts").exists()) {
    include(":validation-app")
    project(":validation-app").projectDir = file("validation/app-fixture")
}
if (file("validation/car-fixture/build.gradle.kts").exists()) {
    include(":validation-car")
    project(":validation-car").projectDir = file("validation/car-fixture")
}
if (file("validation/oem-entry-fixture/build.gradle.kts").exists()) {
    include(":validation-oem-entry")
    project(":validation-oem-entry").projectDir = file("validation/oem-entry-fixture")
}
