import java.io.FileInputStream
import java.util.Properties

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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

val localPropertiesFile = File(rootDir, "local.properties")
val localProperties = Properties()
localProperties.load(FileInputStream(localPropertiesFile))

val githubUserName: String = localProperties.getProperty("github_user_name")
val githubAccessToken: String = localProperties.getProperty("github_access_token")
val githubUrl: String = localProperties.getProperty("github_url")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri(githubUrl)

            credentials {
                username = githubUserName
                password = githubAccessToken
            }
        }
    }
}

rootProject.name = "bluetooth-sdk-android-v2"
include(":bluetooth-sdk-android-v2")
include(":polihealth-sdk-android-v2")
