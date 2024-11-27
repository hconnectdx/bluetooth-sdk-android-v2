include(":bluetooth-sdk-android-v2-example")
include(":polihealth-sdk-android-v2-example")

pluginManagement {
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

/** 플러그인 패키지를 어디서 다운로드를 해야 하는지 **/
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bluetooth-sdk-android-v2"
include(":bluetooth-sdk-android-v2")
include(":polihealth-sdk-android-v2")