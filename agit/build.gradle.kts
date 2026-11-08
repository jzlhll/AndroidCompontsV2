plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.au.agit"
    compileSdk = gradle.extra["compileSdk"] as Int
    defaultConfig {
        applicationId = "com.au.agit"
        minSdk = gradle.extra["minSdk"] as Int
        targetSdk = gradle.extra["targetSdk"] as Int
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += setOf(
        "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "OSGI-INF/**"
    )
}

dependencies {
    implementation(project(":Module-AndroidCommon"))
    implementation(project(":Module-AndroidUi"))
    implementation(project(":Module-AndroidUiEx"))
    implementation(project(":Module-ImageCompressed"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.jgit)
    // JGit 的内部输出不记录远端响应，业务错误统一转换后展示。
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
