import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "kr.co.hconnect.polihealth_sdk_android_v2_example"
    compileSdk = 34

    // 키스토어 프로퍼티 로드
    val keystorePropertiesFile =
        rootProject.file("./polihealth-sdk-android-v2-example/signature/keystore.properties")
    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"].toString())
            storePassword = keystoreProperties["storePassword"].toString()
            keyAlias = keystoreProperties["keyAlias"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
        }
    }
//    /Users/gwagmin-u/Documents/0_workspace/aos/bluetoothlib/bluetooth-sdk-android-v2/polihealth-sdk-android-v2-example/signature
    defaultConfig {
        applicationId = "kr.co.hconnect.polihealth_sdk_android_v2_example"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "API_URL",
            "\"https://mapi-stg.health-on.co.kr\""
        )
        buildConfigField(
            "String",
            "CLIENT_ID", "\"3270e7da-55b1-4dd4-abb9-5c71295b849b\""
        )
        buildConfigField(
            "String",
            "CLIENT_SECRET",
            "\"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpbmZyYSI6IkhlYWx0aE9uLVN0YWdpbmciLCJjbGllbnQtaWQiOiIzMjcwZTdkYS01NWIxLTRkZDQtYWJiOS01YzcxMjk1Yjg0OWIifQ.u0rBK-2t3l4RZ113EzudZsKb0Us9PEtiPcFDBv--gYdJf9yZJQOpo41XqzbgSdDa6Z1VDrgZXiOkIZOTeeaEYA\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )


            buildConfigField("String", "API_URL", "\"https://mapi.health-on.co.kr/\"")
            buildConfigField("String", "CLIENT_ID", "\"659c95fd-900a-4a9a-8f61-1888334a3c7b\"")
            buildConfigField(
                "String",
                "CLIENT_SECRET",
                "\"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpbmZyYSI6IkhlYWx0aE9uLUxpdmUiLCJjbGllbnQtaWQiOiI2NTljOTVmZC05MDBhLTRhOWEtOGY2MS0xODg4MzM0YTNjN2IifQ.GV8Fg5pY-08GlZI0UUFLIqtrmlwnU7kQ-soN6VFlj_usXBex7mv3-vjkAZxV5Yb2MMecifUqwOQpikyirX9aBw\""
            )
            signingConfig = signingConfigs.getByName("release")

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
//    implementation("kr.co.hconnect:polihealth-sdk-android-v2:0.0.7")
//    implementation("kr.co.hconnect:polihealth-sdk-android-v2:0.0.1")
    implementation(project(":polihealth-sdk-android-v2"))
//    implementation(project(":bluetooth-sdk-android-v2"))
}