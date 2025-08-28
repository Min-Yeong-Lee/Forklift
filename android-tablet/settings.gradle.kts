/*pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(uri("https://jitpack.io"))
    }
    // ✅ plugins 블록은 여전히 비워도 됨
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ✅ 추가: Paho MQTT AndroidX 대응 포크를 가져오기 위해 필요!
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Forklift_k"
include(":app")
*/

pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")   // ✅ hannesa2 라이브러리 받을 때 필요
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")   // ✅ 마찬가지
    }
}
rootProject.name = "Forklift_k"
include(":app")
