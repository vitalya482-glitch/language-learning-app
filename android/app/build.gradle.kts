import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionProperties = Properties().apply {
    val versionFile = rootProject.file("version.properties")
    check(versionFile.isFile) { "Missing version.properties" }
    versionFile.inputStream().use { load(it) }
}

val appVersionName = versionProperties.getProperty("APP_VERSION_NAME")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("APP_VERSION_NAME is missing in version.properties")

val androidVersionCode = versionProperties.getProperty("ANDROID_VERSION_CODE")
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("ANDROID_VERSION_CODE must be a positive integer in version.properties")

android {
    namespace = "kz.lvk.languagelearning.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "kz.lvk.languagelearning"
        minSdk = 26
        targetSdk = 37
        versionCode = androidVersionCode
        versionName = appVersionName

        // The native AI runtime is currently optimized for modern 64-bit Android phones.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("signing/dev-update-test.keystore")
            storePassword = "lvk-language-dev"
            keyAlias = "lvk-dev"
            keyPassword = "lvk-language-dev"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("dev")
            resValue("string", "app_name", "Language Learning Dev")
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://github.com/vitalya482-glitch/language-learning-app/releases/download/dev-latest/language-learning-manifest.json\"",
            )
        }
        create("prod") {
            dimension = "distribution"
            resValue("string", "app_name", "Language Learning")
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://raw.githubusercontent.com/vitalya482-glitch/LVK-Update-Feed/main/manifests/language-learning-stable.json\"",
            )
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":android:core:network"))
    implementation(project(":android:core:update"))
    implementation(project(":android:core:designsystem"))
    implementation(project(":android:core:ai-api"))
    implementation(project(":android:core:ai-native"))
    implementation(project(":android:core:models"))
    implementation(project(":android:core:speech"))
    implementation(project(":android:core:settings"))
    implementation(project(":android:feature:conversation"))
    implementation(project(":android:feature:home"))
    implementation(project(":android:feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
