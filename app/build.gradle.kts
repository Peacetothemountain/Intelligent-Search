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
        versionCode = 91
        versionName = "7.8"
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
            storeFile = file(localProperties.getProperty("RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: "password"
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: "release"
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: "password"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
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

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        useLegacyPackaging = true
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
  implementation(libs.androidx.activity.compose)
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
  implementation("androidx.navigation:navigation-compose:2.9.8")
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

  // Google Generative AI
  implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

  // Lottie for Animations
  implementation("com.airbnb.android:lottie-compose:6.7.1")

  // Graphics Shapes for Material Morph Animations
  implementation("androidx.graphics:graphics-shapes:1.1.0")
}


