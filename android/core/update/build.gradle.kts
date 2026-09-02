plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "kz.lvk.languagelearning.core.update"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":android:core:common"))
    implementation(project(":android:core:network"))
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
}
