plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

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
    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.service)

    // Room DB
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)

    // Location
    implementation(libs.play.services.location)

//    implementation("androidx.fragment:fragment-ktx:1.8.8")
//    implementation("androidx.fragment:fragment-compose:1.8.8")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
