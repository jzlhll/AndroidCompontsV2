import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.allan.mydroid"
    compileSdk = gradle.extra["compileSdk"] as Int

    defaultConfig {
        applicationId = "com.allan.mydroid"
        minSdk = 29
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

            resValue("xml", "network_security", "@xml/network_mydroid")
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
        }
    }
    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }
    buildFeatures {
        resValues = true
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
    implementation(libs.gson)

    implementation(libs.okhttp)

    implementation(libs.blurview)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(project(":Module-AndroidCommon"))
    implementation(project(":Module-AndroidColor"))

    implementation(project(":Module-AndroidUi"))
    implementation(project(":Module-AndroidUiEx"))
    implementation(project(":Module-Nested"))
    implementation(project(":Module-ImageCompressed"))
    implementation(project(":Module-Okhttp"))
    implementation(project(":Module-AndroidLogSystem"))
    implementation(project(":Module-Native"))
    implementation(project(":Module-AuGsonMMKV"))
    implementation(project(":Module-AuSimplePermission"))

    implementation(libs.jsbridgev2)
    implementation(libs.okhttp)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}

apply(from = "../plugin-gradle-preaction/preSourceStringEncypt.gradle")
// 外部 Groovy 脚本没有 AGP 编译类路径，由宿主传入同一插件的产物标识。
extra["assetsEncryptionArtifact"] = SingleArtifact.ASSETS
apply(from = "../plugin-gradle-preaction/assetsEncryptRules.gradle")
