plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.flex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flex"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Workaround: KSP's bundled IntelliJ Platform posts AWT EDT events asynchronously during
// annotation processing. When IntelliJ disposes its Application, pending EDT events call
// ApplicationManager.getApplication() which returns null → NPE in AWT-EventQueue-0 thread.
// These events are posted async and can fire during *any* subsequent task (not just ksp*Kotlin),
// so we drain the EDT after every task to ensure events run while Application is still alive.
tasks.configureEach {
    doLast {
        try {
            val gfxEnvClass = Class.forName("java.awt.GraphicsEnvironment")
            val isHeadless = gfxEnvClass.getMethod("isHeadless").invoke(null) as Boolean
            val eventQueueClass = Class.forName("java.awt.EventQueue")
            val isEdt = eventQueueClass.getMethod("isDispatchThread").invoke(null) as Boolean
            if (!isHeadless && !isEdt) {
                val invokeAndWait = eventQueueClass.getMethod("invokeAndWait", Runnable::class.java)
                repeat(2) {
                    try { invokeAndWait.invoke(null, Runnable { }) } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }
}

tasks.register<Sync>("renameReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    into(layout.buildDirectory.dir("outputs/apk/release/renamed"))
    include("app-release.apk")
    val versionName = project.extensions
        .getByType<com.android.build.api.dsl.ApplicationExtension>()
        .defaultConfig.versionName
    rename { "flex-v$versionName.apk" }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Charts
    implementation(libs.vico.compose.m3)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Gson
    implementation(libs.gson)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // DocumentFile (SAF)
    implementation(libs.androidx.documentfile)

    // Google Play Services Location (Geofencing)
    implementation(libs.play.services.location)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Java 8+ API desugaring (for LocalDate/LocalTime on older APIs)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit Testing (JUnit 5 is okay here)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented Testing (Switched to JUnit 4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
