plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.au.module_android"
    compileSdk = gradle.extra["compileSdk"] as Int

    // 读取外部属性并处理空安全
    val supportLocales = findProperty("app.supportLocales")?.toString()?.toBoolean() ?: false
    val supportDarkMode = findProperty("app.supportDarkMode")?.toString()?.toBoolean() ?: false

    defaultConfig {
        minSdk = gradle.extra["minSdk"] as Int
        lint.targetSdk = gradle.extra["targetSdk"] as Int

        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("boolean", "SUPPORT_LOCALES", supportLocales.toString())
        buildConfigField("boolean", "SUPPORT_DARKMODE", supportDarkMode.toString())
    }

    buildTypes {
        release {
            //是否混淆
            isMinifyEnabled = false
            // 压缩资源，必须开启isMinifyEnabled才有用
            // shrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = signingConfigs.getByName("auRelease")
            // 设置是否要自动上传
            // firebaseCrashlytics {
            //     mappingFileUploadEnabled = false
            // }
            // 上传bundle包所需的原生调试符号
            // ndk {
            //     debugSymbolLevel = "FULL" // 或者 "SYMBOL_TABLE"
            // }

            buildConfigField("boolean", "ENABLE_LOGCAT", "false")
            buildConfigField("boolean", "ENABLE_FILE_LOG_DEFAULT", "false")
        }
        debug {
            // 是否混淆
            isMinifyEnabled = false
            // 压缩资源，必须开启isMinifyEnabled才有用
            isShrinkResources = false

            buildConfigField("boolean", "ENABLE_LOGCAT", "true")
            buildConfigField("boolean", "ENABLE_FILE_LOG_DEFAULT", "true")
        }
    }

    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.window)

    // ViewModel
    api(libs.androidx.lifecycle.viewmodel.ktx)
    // LiveData
    api(libs.androidx.lifecycle.livedata.ktx)
    // Lifecycles only (without ViewModel or LiveData)
    api(libs.androidx.lifecycle.runtime.ktx)
    // Saved state module for ViewModel
    api(libs.androidx.lifecycle.viewmodel.savedstate)

    api(libs.material)
    api(libs.androidx.startup.runtime)

    api(libs.androidx.recyclerview)

    implementation(libs.androidx.lifecycle.process)
    api(libs.glide) {
        exclude(group = "com.squareup.okhttp3",  module = "okhttp")
    }
    api(libs.okhttp3.integration) {
        exclude(group = "com.squareup.okhttp3",  module = "okhttp")
    }

    api(libs.androidx.datastore.preferences)

    implementation(libs.androidx.core.splashscreen)
    // implementation(libs.android.cn.oaid)
}