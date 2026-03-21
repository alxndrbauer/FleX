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

    signingConfigs {
        create("release") {
            storeFile = file("../android.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

tasks.register<Sync>("renameWearReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    into(layout.buildDirectory.dir("outputs/apk/release/renamed"))
    include("wear-release.apk")
    val versionName = project.extensions
        .getByType<com.android.build.api.dsl.ApplicationExtension>()
        .defaultConfig.versionName
    rename { "flex-wear-v$versionName.apk" }
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
