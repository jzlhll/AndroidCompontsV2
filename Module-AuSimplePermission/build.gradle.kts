plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.36.0"
}

android {
    namespace = "com.au.module_simplepermission"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

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
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

mavenPublishing {
    coordinates("io.github.jzlhll", "module-ausimplepermission", "0.9.0")

    pom {
        name = "module-ausimplepermission"
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