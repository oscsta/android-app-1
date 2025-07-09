plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // id("com.android.application") version "8.11.0"
    // id("org.jetbrains.kotlin.android") version "2.2.0"

    id("com.google.devtools.ksp")
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
    jvmToolchain(21)
}

dependencies {
    implementation(libs.appcompat)

    // Room DB
    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
