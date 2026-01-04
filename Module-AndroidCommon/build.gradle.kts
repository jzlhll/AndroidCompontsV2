plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")

    id("com.vanniktech.maven.publish") version "0.35.0"
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
        }
        debug {
            // 是否混淆
            isMinifyEnabled = false
            // 压缩资源，必须开启isMinifyEnabled才有用
            isShrinkResources = false
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

mavenPublishing {
    coordinates("io.github.jzlhll", "module-androidcommon", "0.0.3")

    pom {
        name = "module-androidcommon"
        description = "This is my android project some base utils."
        inceptionYear = "2026"
        url = "https://github.com/jzlhll/AndroidCompontsV2"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "jzlhll"
                name = "jzlhll"
                url = "https://github.com/jzlhll/"
            }
        }
        scm {
            url = "https://github.com/jzlhll/AndroidCompontsV2"
            connection = "scm:git:git://github.com/jzlhll/AndroidCompontsV2.git"
            developerConnection = "scm:git:ssh://git@github.com/jzlhll/AndroidCompontsV2.git"
        }
    }
}