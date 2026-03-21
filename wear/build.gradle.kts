plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.flex.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Wearable Data Layer
    implementation(libs.play.services.wearable)

    // Wear Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)

    // Tiles
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.wear.protolayout)

    // Coroutines
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.wear.compose.ui.tooling)
    debugImplementation(libs.wear.tiles.tooling)
}
