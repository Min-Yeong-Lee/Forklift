pluginManagement {
    plugins {
        id("com.android.application")             version "8.9.0"
        id("org.jetbrains.kotlin.android")        version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // hannesa2 포크를 쓰는 경우에만 필요
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Forklift_phone"
include(":app")
