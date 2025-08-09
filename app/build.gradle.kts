plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    id("com.google.devtools.ksp")
}

android {
    namespace = "com.github.oscsta.runni"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.github.oscsta.runni"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
    }
    buildToolsVersion = "36.0.0"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.material3.android)
    implementation(libs.ui.tooling)
    implementation(libs.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.foundation)

    // Room DB
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)

    // Location
    implementation(libs.play.services.location)



//    implementation("androidx.fragment:fragment-ktx:1.8.8")
//    implementation("androidx.fragment:fragment-compose:1.8.8")

    androidTestImplementation(composeBom)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
