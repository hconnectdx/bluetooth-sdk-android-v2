import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    `maven-publish`
}

android {
    namespace = "kr.co.hconnect.bluetooth_sdk_android_peripheral"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}

val projectProps = Properties()
projectProps.load(FileInputStream(project.file("project.properties")))

val projectName: String = projectProps.getProperty("name")
val projectTitle: String = projectProps.getProperty("title")
val projectVersion: String = projectProps.getProperty("version")
val projectGroupId: String = projectProps.getProperty("publication_group_id")
val projectArtifactId: String = projectProps.getProperty("publication_artifact_id")

val rootProjectProps = Properties()
rootProjectProps.load(FileInputStream(project.file("../local.properties")))
val githubUrl: String = rootProjectProps.getProperty("githubUrl")
val githubUsername: String = rootProjectProps.getProperty("githubUsername")
val githubAccessToken: String = rootProjectProps.getProperty("githubAccessToken")

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = projectGroupId
                artifactId = projectArtifactId
                version = projectVersion
                pom.packaging = "aar"

                artifact("${layout.projectDirectory}/build/outputs/aar/bluetooth-sdk-android-peripheral-release.aar")

                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.implementation.get().allDependencies.forEach { dependency ->
                        if (dependency.group != null && !dependency.group!!.startsWith("androidx")) {
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", dependency.group)
                            dependencyNode.appendNode("artifactId", dependency.name)
                            dependencyNode.appendNode("version", dependency.version)
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "bluetooth-sdk-android-peripheral"
                url = uri(githubUrl)
                credentials {
                    username = githubUsername
                    password = githubAccessToken
                }
            }
        }
    }
}