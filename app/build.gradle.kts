plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vrema"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vrema"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.3"

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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
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
    rename { "vrema-v${android.defaultConfig.versionName}.apk" }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Charts
    implementation("com.patrykandpatrick.vico:compose-m3:2.4.3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // DocumentFile (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Java 8+ API desugaring (for LocalDate/LocalTime on older APIs)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Unit Testing (JUnit 5 is okay here)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.1.1")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // Instrumented Testing (Switched to JUnit 4)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.56.2")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.56.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
