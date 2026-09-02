plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kz.lvk.languagelearning.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "kz.lvk.languagelearning"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
                "\"https://raw.githubusercontent.com/vitalya482-glitch/LVK-Update-Feed/main/manifests/language-learning.json\"",
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
    implementation(project(":android:feature:home"))

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
