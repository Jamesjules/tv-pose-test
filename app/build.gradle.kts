plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Build with:  gradle assembleDebug -PwithUvc=true   to include the USB (UVC) camera path
val withUvc = project.hasProperty("withUvc")

android {
    namespace = "com.razcorp.posetest"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.razcorp.posetest"
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDir(if (withUvc) "src/uvc/kotlin" else "src/nouvc/kotlin")
    androidResources { noCompress += "task" }
}

dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    if (withUvc) implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.3.3")
}
