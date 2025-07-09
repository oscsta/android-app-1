rootProject.name = "runni"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Apply the foojay-resolver plugin to allow automatic download of JDKs
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    }
}

include("app")