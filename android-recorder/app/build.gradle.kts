import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
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
        val deepseekKey = props.getProperty("DEEPSEEK_API_KEY") ?: ""
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekKey\"")
        val deepseekBase = props.getProperty("DEEPSEEK_BASE_URL") ?: "https://api.deepseek.com"
        buildConfigField("String", "DEEPSEEK_BASE_URL", "\"$deepseekBase\"")
        val deepseekModel = props.getProperty("DEEPSEEK_MODEL") ?: "deepseek-chat"
        buildConfigField("String", "DEEPSEEK_MODEL", "\"$deepseekModel\"")
        val backendTranscribeUrl = props.getProperty("BACKEND_TRANSCRIBE_URL") ?: "https://android-recorder-backend.onrender.com/"
        buildConfigField("String", "BACKEND_TRANSCRIBE_URL", "\"$backendTranscribeUrl\"")
        val whisperUrl = props.getProperty("WHISPER_MODEL_URL") ?: ""
        buildConfigField("String", "WHISPER_MODEL_URL", "\"$whisperUrl\"")
        val whisperSha = props.getProperty("WHISPER_MODEL_SHA256") ?: ""
        buildConfigField("String", "WHISPER_MODEL_SHA256", "\"$whisperSha\"")
        val openaiKey = props.getProperty("OPENAI_API_KEY") ?: ""
        buildConfigField("String", "OPENAI_API_KEY", "\"$openaiKey\"")
        val openaiBase = props.getProperty("OPENAI_BASE_URL") ?: "https://api.openai.com"
        buildConfigField("String", "OPENAI_BASE_URL", "\"$openaiBase\"")
        val openaiModel = props.getProperty("OPENAI_WHISPER_MODEL") ?: "whisper-1"
        buildConfigField("String", "OPENAI_WHISPER_MODEL", "\"$openaiModel\"")
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
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
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
