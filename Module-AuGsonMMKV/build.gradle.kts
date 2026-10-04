plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.au.module_gson"
    compileSdk = gradle.extra["compileSdk"] as Int

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    compileOnly(project(":Module-AndroidCommon"))
    implementation(libs.gson)
    implementation(libs.mmkv.static)
}