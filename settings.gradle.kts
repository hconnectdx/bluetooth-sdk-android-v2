import java.io.FileInputStream
import java.util.Properties

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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hconnectdx/bluetooth-sdk-android-v2")
            credentials {
                username = "hconnectdx"

            }
        }
    }
}

rootProject.name = "bluetooth-sdk-android-v2"
include(":bluetooth-sdk-android-v2")
include(":polihealth-sdk-android-v2")
