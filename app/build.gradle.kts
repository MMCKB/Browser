import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingProperties = Properties().apply {
    val source = rootProject.file("signing.properties")
    if (source.exists()) source.inputStream().use(::load)
}

fun signingValue(environmentName: String, propertyName: String): String =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(propertyName, "")

android {
    namespace = "com.mmckb.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mmckb.browser"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "2.20.0-settings-refactor"

        ndk {
            abiFilters += listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("mmckb") {
            storeFile = file(signingValue("KEYSTORE_FILE", "storeFile").ifBlank { "release.keystore" })
            storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingValue("KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 调试包也使用 MMCKB 证书，确保每次编译的升级签名一致。
            signingConfig = signingConfigs.getByName("mmckb")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("mmckb")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
}
