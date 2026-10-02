import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "childmonitor.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.serialization.json)
            implementation(libs.datetime)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.datastore.preferences)
        }
    }
}

dependencies { add("kspAndroid", libs.room.compiler) }
room { schemaDirectory("$projectDir/schemas") }
