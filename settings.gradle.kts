@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

pluginManagement {
    resolutionStrategy {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "ViMusic"

include(":app")
include(":core:data")
include(":core:material-compat")
include(":core:ui")
include(":compose:persist")
include(":compose:preferences")
include(":compose:routing")
include(":compose:reordering")
include(":ktor-client-brotli")
include(":providers:common")
include(":providers:github")
include(":providers:innertube")
include(":providers:kugou")
include(":providers:lrclib")
include(":providers:lyricsplus")
include(":providers:piped")
include(":providers:sponsorblock")
include(":providers:translate")
