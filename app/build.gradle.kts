plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.github.oscsta.runni"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.github.oscsta.runni"
        minSdk = 31
        targetSdk = 35
    }
}

dependencies {
    implementation(libs.guava)
}