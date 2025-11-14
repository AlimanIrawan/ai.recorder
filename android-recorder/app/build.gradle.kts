import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ai.recorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ai.recorder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        val lpFile = rootProject.file("local.properties")
        val props = Properties()
        if (lpFile.exists()) lpFile.inputStream().use { props.load(it) }
        val driveFolderId = props.getProperty("DRIVE_FOLDER_ID") ?: ""
        buildConfigField("String", "DRIVE_FOLDER_ID", "\"$driveFolderId\"")
        val backendUploadUrl = props.getProperty("BACKEND_UPLOAD_URL") ?: ""
        buildConfigField("String", "BACKEND_UPLOAD_URL", "\"$backendUploadUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.runtime:runtime-livedata:1.7.0")
}
