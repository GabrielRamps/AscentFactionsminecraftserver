pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK named in gradle.properties (javaVersion) if it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.extendedclip.com/releases/") { name = "placeholderapi" }
        maven("https://jitpack.io") { name = "jitpack" } // VaultAPI
    }
}

rootProject.name = "ascent"

include("ascent-api", "ascent-plugin")
