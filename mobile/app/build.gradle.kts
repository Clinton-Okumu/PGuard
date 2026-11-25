import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

val debugApiBaseUrl = normalizeBaseUrl(
    localProperties.getProperty("pguard.apiBaseUrl") ?: "https://pguard.orkestr.run/"
)
val releaseApiBaseUrl = normalizeBaseUrl(
    localProperties.getProperty("pguard.releaseApiBaseUrl") ?: "https://pguard.orkestr.run/"
)
val googleWebClientId = (localProperties.getProperty("pguard.googleWebClientId") ?: "146143329754-t7ded9rqac7lvh81i3us31t4fgns9ldc.apps.googleusercontent.com").trim()

android {
    namespace = "com.example.pguard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pguard"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${escapeBuildConfigString(debugApiBaseUrl)}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${escapeBuildConfigString(googleWebClientId)}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${escapeBuildConfigString(releaseApiBaseUrl)}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${escapeBuildConfigString(googleWebClientId)}\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // DataStore (Session persistence)
    implementation(libs.androidx.datastore.preferences)

    // Google Credential Manager (Modern Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
