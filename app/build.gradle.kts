import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
}

android {
    namespace = "com.pixel.intelligentsearch"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pixel.intelligentsearch"
        minSdk = 31
        targetSdk = 37
        versionCode = 122
        versionName = "9.2"
        ndk {
            // CRITICAL: Must support arm64-v8a, armeabi-v7a, x86_64, and x86 to retain Google Play compatibility for 2,159+ devices.
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { localProperties.load(it) }
            }
            val customStore = localProperties.getProperty("RELEASE_STORE_FILE")
            val targetStore = when {
                customStore != null && file(customStore).exists() -> file(customStore)
                customStore != null && rootProject.file(customStore).exists() -> rootProject.file(customStore)
                rootProject.file("release.keystore").exists() -> rootProject.file("release.keystore")
                else -> null
            }
            val storePass = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
            val keyAliasProp = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")

            if (targetStore != null && targetStore.exists() && !storePass.isNullOrBlank() && !keyAliasProp.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = targetStore
                storePassword = storePass
                keyAlias = keyAliasProp
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val relConfig = signingConfigs.getByName("release")
            // Strict security: Never silently sign release APKs with debug keys
            signingConfig = if (relConfig.storeFile != null && relConfig.storeFile!!.exists()) relConfig else null
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    // CI fails on any lint warning not in the baseline; regenerate it with ./gradlew updateLintBaseline after
    // fixing entries. Library-update notices are left to dependency review rather than breaking builds.
    lint {
      baseline = file("lint-baseline.xml")
      warningsAsErrors = true
      abortOnError = true
      disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    testOptions {
      unitTests {
        isReturnDefaultValues = true
        // Robolectric tests load the merged manifest and resources.
        isIncludeAndroidResources = true
      }
    }

    packaging {
      resources {
        excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/*.version",
            "/META-INF/DEPENDENCIES"
        )
      }
      jniLibs {
        useLegacyPackaging = false
      }
    }
}

composeCompiler {
    includeSourceInformation.set(false)
}

room {
    // Exported Room schemas are checked in so every search history schema change is reviewed and
    // shipped with a migration instead of wiping the user's history. The Room plugin copies the schema
    // per variant, avoiding the parallel-build race of the room.schemaLocation KSP argument.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.google.material)
  // Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(platform(libs.androidx.compose.bom))

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests (the androidTest classpath does not inherit the BOM applied to `implementation`)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // Robolectric runs Android-dependent code (SharedPreferences, DataStore, Room, ContentResolver) on the JVM.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation & Serialization
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.serialization.json)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)
  implementation(libs.hilt.navigation.compose)

  // Preferences DataStore
  implementation(libs.androidx.datastore.preferences)

  // Graphics Shapes for Material Morph Animations
  implementation(libs.androidx.graphics.shapes)

  // AppFunctions: exposes local search to on-device assistants
  implementation(libs.androidx.appfunctions)
  ksp(libs.androidx.appfunctions.compiler)
}

tasks.register<Zip>("packageNativeDebugSymbols") {
    archiveFileName.set("native-debug-symbols.zip")
    destinationDirectory.set(file("${layout.buildDirectory.get()}/outputs/native-debug-symbols/release"))
    from("${layout.buildDirectory.get()}/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
    dependsOn("mergeReleaseNativeLibs")
}
