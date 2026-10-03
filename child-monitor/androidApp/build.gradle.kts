import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "childmonitor.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.allan.childmonitor"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":Module-AndroidCommon"))
    implementation(childMonitorLibs.room.runtime)
    implementation(childMonitorLibs.sqlite.bundled)
    implementation(childMonitorLibs.datastore.preferences)
    ksp(childMonitorLibs.room.compiler)
    implementation(platform(childMonitorLibs.compose.bom))
    implementation(childMonitorLibs.compose.ui)
    implementation(childMonitorLibs.compose.foundation)
    implementation(childMonitorLibs.compose.material3)
    implementation(childMonitorLibs.compose.preview)
    debugImplementation(childMonitorLibs.compose.tooling)
    implementation(childMonitorLibs.activity.compose)
    implementation(childMonitorLibs.lifecycle.runtime.compose)
    implementation(childMonitorLibs.lifecycle.viewmodel.compose)
    implementation(childMonitorLibs.lifecycle.viewmodel.navigation3)
    implementation(childMonitorLibs.navigation3.runtime)
    implementation(childMonitorLibs.navigation3.ui)
    implementation(childMonitorLibs.serialization.json)
    implementation(childMonitorLibs.coroutines.android)
    implementation(childMonitorLibs.camera.camera2)
    implementation(childMonitorLibs.camera.lifecycle)
    implementation(childMonitorLibs.camera.video)
    implementation(childMonitorLibs.camera.view)
    implementation(childMonitorLibs.camera.mlkit)
    implementation(childMonitorLibs.mlkit.pose)
    implementation(childMonitorLibs.mediapipe.vision)
    implementation(childMonitorLibs.media3.exoplayer)
    implementation(childMonitorLibs.media3.ui)
    implementation(childMonitorLibs.media3.compose)
}

room { schemaDirectory("$projectDir/schemas") }
