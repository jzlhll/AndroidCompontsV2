plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.allan.mydroid"
    compileSdk = gradle.extra["compileSdk"] as Int

    signingConfigs {
        create("auRelease") {
            //sha1   C3:66:E7:5D:0B:8F:59:64:3B:8D:22:3E:E3:EB:E2:DF:C8:8E:EC:CD
            //sha256 44:70:2D:3F:D6:6A:4A:82:4E:C4:5D:EC:AB:57:4A:75:13:F2:78:A5:FB:72:A0:6B:5A:DE:6E:CD:B1:87:98:74
            keyAlias = "au"
            keyPassword = "a12345"
            storeFile = file("../auReleaseA12345.jks")
            storePassword = "a12345"
        }
    }

    defaultConfig {
        applicationId = "com.allan.mydroid"
        minSdk = 26
        targetSdk = gradle.extra["targetSdk"] as Int
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isDebuggable = false
            //是否混淆
            isMinifyEnabled = true
            //压缩资源，必须开启minifyEnabled才有用
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("auRelease")
            resValue("xml", "network_security", "@xml/network_release")
            //apply plugin: 'com.au.plugins.plugin-string-encrypt'
            // 上传bundle包所需的原生调试符号
            ndk {
                debugSymbolLevel = "FULL"//或者 'SYMBOL_TABLE'
            }
        }
        debug {
            isDebuggable = true
            //是否混淆
            isMinifyEnabled = false
            //压缩资源，必须开启minifyEnabled才有用
            isShrinkResources = false
            resValue("xml", "network_security", "@xml/network_debug")
//            apply plugin: 'com.au.plugins.plugin-string-encrypt'
            //signingConfig signingConfigs.auRelease
        }
    }
    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
    implementation(libs.gson)

    implementation(libs.blurview)
    implementation(project(":Module-AndroidCommon"))

    implementation(project(":Module-AndroidUi"))
    implementation(project(":Module-Nested"))
    implementation(project(":Module-ImageCompressed"))
    implementation(project(":Module-Okhttp"))
    implementation(project(":Module-AndroidLogSystem"))
    implementation(project(":Module-Native"))
    implementation(project(":Module-AuGsonMMKV"))
    implementation(project(":Module-AuSimplePermission"))

    implementation(libs.jsbridgev2)

    //hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.okhttp)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}

apply(from = "../plugin-gradle-preaction/preSourceStringEncypt.gradle")
apply(from = "../plugin-gradle-preaction/assetsEncryptRules.gradle")