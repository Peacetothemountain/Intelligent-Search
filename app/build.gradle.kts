import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
    namespace = "com.pixel.intelligentsearch"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pixel.intelligentsearch"
        minSdk = 31
        targetSdk = 37
        versionCode = 92
        versionName = "8.9"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
            val customStore = localProperties.getProperty("RELEASE_STORE_FILE")
            val targetStore = when {
                customStore != null && file(customStore).exists() -> file(customStore)
                customStore != null && rootProject.file(customStore).exists() -> rootProject.file(customStore)
                file("F:/release.keystore").exists() -> file("F:/release.keystore")
                rootProject.file("release.keystore").exists() -> rootProject.file("release.keystore")
                else -> null
            }
            if (targetStore != null && targetStore.exists()) {
                storeFile = targetStore
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: "password"
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: "release"
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: "password"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val relConfig = signingConfigs.getByName("release")
            signingConfig = if (relConfig.storeFile != null && relConfig.storeFile!!.exists()) relConfig else signingConfigs.getByName("debug")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    testOptions {
      unitTests {
        isReturnDefaultValues = true
      }
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        useLegacyPackaging = false
      }
    }
}

dependencies {
  implementation("androidx.media3:media3-exoplayer:1.11.0")
  implementation("androidx.media3:media3-ui:1.11.0")
  implementation("com.google.android.material:material:1.14.0")
  // Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation("androidx.lifecycle:lifecycle-process:2.8.7")
  implementation(libs.androidx.activity.compose)
  implementation("androidx.profileinstaller:profileinstaller:1.4.1")
  implementation(platform(libs.androidx.compose.bom))

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)

  // Hilt
  implementation(libs.hilt.android)
  "ksp"(libs.hilt.android.compiler)
  implementation(libs.hilt.navigation.compose)

  // Preferences DataStore
  implementation(libs.androidx.datastore.preferences)


  // Lottie for Animations
  implementation("com.airbnb.android:lottie-compose:6.7.1")

  // Graphics Shapes for Material Morph Animations
  implementation("androidx.graphics:graphics-shapes:1.1.0")
}


