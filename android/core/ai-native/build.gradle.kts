plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "kz.lvk.languagelearning.core.ai.nativeengine"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":android:core:ai-api"))
    implementation(libs.kotlinx.coroutines.android)
}
