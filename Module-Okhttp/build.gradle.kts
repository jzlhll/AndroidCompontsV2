plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.au.module_okhttp"
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
    ksp(libs.glideKsp)
    implementation(project(":Module-AndroidCommon"))
    implementation(project(":Module-AuGsonMMKV"))

    // 向调用模块传递版本约束，供无版本号的 OkHttp 依赖使用。
    api(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    api(libs.okhttp.logging)
    implementation(libs.okhttp.tls)
}
