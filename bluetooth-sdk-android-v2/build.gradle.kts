import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    `maven-publish`
}

android {
    namespace = "kr.co.hconnect.bluetooth_sdk_android_v2"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.work:work-runtime:2.7.1")

}

val projectProps = Properties()
projectProps.load(FileInputStream(project.file("project.properties")))

val projectName: String = projectProps.getProperty("name")
val projectTitle: String = projectProps.getProperty("title")
val projectVersion: String = projectProps.getProperty("version")
val projectGroupId: String = projectProps.getProperty("publication_group_id")
val projectArtifactId: String = projectProps.getProperty("publication_artifact_id")

val githubUrl: String = projectProps.getProperty("github_url")
val githubUsername: String = projectProps.getProperty("github_user_name")
val githubAccessToken: String = projectProps.getProperty("github_access_token")

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = projectGroupId
                artifactId = projectArtifactId
                version = projectVersion
                pom.packaging = "aar"
                artifact("${layout.projectDirectory}/build/outputs/aar/bluetooth-sdk-android-v2-release.aar")
            }
        }

        repositories {
            maven {
                name = "bluetoothLib-sdk-android-v2"
                url = uri(githubUrl)
                credentials {
                    username = githubUsername
                    password = githubAccessToken
                }
            }
        }
    }
}