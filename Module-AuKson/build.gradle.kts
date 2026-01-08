plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)

    id("com.vanniktech.maven.publish") version "0.35.0"
}

android {
    namespace = "com.au.module_kson"
    compileSdk {
        version = release(36)
    }

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
    compileOnly(project(":Module-AndroidCommon"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

mavenPublishing {
    coordinates("io.github.jzlhll", "module-aukson", "0.0.4")

    pom {
        name = "module-aukson"
        description = "Android kotlin serialization libs."
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