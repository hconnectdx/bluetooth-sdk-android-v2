import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    kotlin("plugin.serialization") version "1.6.21"
    `maven-publish`
}

android {
    namespace = "kr.co.hconnect.polihealth_sdk_android_v2"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "IS_DEBUG", "true")
        }
        release {
            buildConfigField("boolean", "IS_DEBUG", "false")
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

    buildFeatures {
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hconnectdx/bluetooth-sdk-android-v2")

            credentials {
                username = "hconnectdx"
                password = ""
            }
        }
    }
}

dependencies {

    val ktor_version: String by project
    val logback_version: String by project

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    // ktor 버전을 꼭 맞춰야한다. 버전 호환성 이슈 심각 함.
    implementation("io.ktor:ktor-client-core:2.0.3")
    implementation("io.ktor:ktor-client-cio:2.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation("kr.co.hconnect:bluetooth-sdk-android-v2:0.0.9")

//    implementation(project(":bluetooth-sdk-android-v2"))
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

                artifact("${layout.projectDirectory}/build/outputs/aar/polihealth-sdk-android-v2-release.aar")

                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.implementation.get().allDependencies.forEach { dependency ->
                        if (!dependency.group!!.startsWith("androidx")) {
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
                name = "polihealth-sdk-android-v2"
                url = uri(githubUrl)
                credentials {
                    username = githubUsername
                    password = githubAccessToken
                }
            }
        }
    }
}