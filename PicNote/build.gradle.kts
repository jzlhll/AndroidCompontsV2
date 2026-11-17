plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.allan.picnote"
    compileSdk = gradle.extra["compileSdk"] as Int
    defaultConfig {
        applicationId = "com.allan.picnote"
        minSdk = 31
        targetSdk = gradle.extra["targetSdk"] as Int
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = gradle.extra["sourceCompatibility"] as JavaVersion
        targetCompatibility = gradle.extra["targetCompatibility"] as JavaVersion
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// 仅提取已发布的语法源码，避免旧版注解处理器依赖 javac 内部 API。
val codeGrammarBundle by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val codeGrammarSources = codeGrammarBundle.elements.map { artifacts ->
    artifacts.map { artifact ->
        zipTree(artifact.asFile).matching {
            include(listOf("clike", "java", "kotlin", "c", "cpp", "markup", "swift", "go", "javascript", "python", "json", "css", "sql", "yaml")
                .map { "languages/Prism_$it.java" })
        }
    }
}

/** 提取语法源码，并通过输出目录接入 Android 的源码与任务依赖。 */
abstract class ExtractCodeGrammars : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val extractCodeGrammars = tasks.register<ExtractCodeGrammars>("extractCodeGrammars") {
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/codeGrammars"))
    from(codeGrammarSources)
    into(outputDirectory)
    eachFile { path = "io/noties/prism4j/$path" }
    includeEmptyDirs = false
}

androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(extractCodeGrammars, ExtractCodeGrammars::outputDirectory)
}

dependencies {
    implementation(files("libs/pastehub-image-1.0.0.aar"))
    implementation(project(":Module-AndroidCommon"))
    implementation(project(":Module-AndroidUi"))
    implementation(project(":Module-AndroidUiEx"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    codeGrammarBundle("io.noties:prism4j-bundler:2.0.0")
}
