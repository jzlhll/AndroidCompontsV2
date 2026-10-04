buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${childMonitorLibs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.maven.publish) apply false
    alias(childMonitorLibs.plugins.android.application) apply false
    alias(childMonitorLibs.plugins.android.library) apply false
    alias(childMonitorLibs.plugins.kotlin.compose) apply false
    alias(childMonitorLibs.plugins.kotlin.serialization) apply false
    alias(childMonitorLibs.plugins.ksp) apply false
    alias(childMonitorLibs.plugins.room) apply false
}
