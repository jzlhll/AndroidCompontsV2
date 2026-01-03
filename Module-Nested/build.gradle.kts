plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.au.module_nested"
    compileSdk = gradle.extra["compileSdk"] as Int

    defaultConfig {
        minSdk = gradle.extra["minSdk"] as Int
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(project(":Module-AndroidCommon"))
    implementation(project(":Module-AndroidUi"))
    implementation(project(":Module-AndroidColor"))
}