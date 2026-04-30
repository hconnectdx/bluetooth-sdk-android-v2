import java.io.FileInputStream
import java.util.Properties

include(":bluetooth-sdk-android-peripheral-example")


include(":bluetooth-sdk-android-peripheral")


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

// local.properties 파일 읽기
val localProperties = Properties()
val localFile = File(rootDir, "local.properties")
if (localFile.exists()) {
    localProperties.load(FileInputStream(localFile))
}

/** 플러그인 패키지를 어디서 다운로드를 해야 하는지 **/
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://maven.pkg.github.com/hconnectdx/bluetooth-sdk-android-v2")
            credentials {
                username = localProperties.getProperty("githubUsername")
                password = localProperties.getProperty("githubAccessToken")
            }
        }
    }
}

rootProject.name = "bluetooth-sdk-android-v2"
include(":bluetooth-sdk-android-v2")
include(":polihealth-sdk-android-v2")