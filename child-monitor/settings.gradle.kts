pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    defaultLibrariesExtensionName.set("childMonitorLibs")
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

gradle.extra["compileSdk"] = 36
gradle.extra["targetSdk"] = 36
gradle.extra["minSdk"] = 29
gradle.extra["sourceCompatibility"] = JavaVersion.VERSION_17
gradle.extra["targetCompatibility"] = JavaVersion.VERSION_17

rootProject.name = "ChildMonitor"
include(":androidApp")
include(":Module-AndroidCommon")
project(":Module-AndroidCommon").projectDir = file("../Module-AndroidCommon")
