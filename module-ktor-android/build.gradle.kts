plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.au.module_ktor_android"
    compileSdk = gradle.extra["compileSdk"] as Int
    defaultConfig {
        minSdk = gradle.extra["minSdk"] as Int
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }
}

dependencies {
    api(project(":module-ktor-base"))
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    implementation(libs.ktor.client.okhttp)
    implementation(project(":Module-AndroidCommon"))
    implementation(libs.kotlinx.coroutines.android)
}
