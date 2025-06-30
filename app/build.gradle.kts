plugins {
    // alias(libs.plugins.android.application)
    id("com.android.application") version "8.11.0"
    // alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.android") version "2.2.0"
}

android {
    namespace = "com.github.oscsta.runni"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.github.oscsta.runni"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    implementation(libs.guava)
    implementation("androidx.appcompat:appcompat:1.7.0")
}