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
        targetSdk = 36
        versionCode = 95
        versionName = "9.0.2"
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
                rootProject.file("release.keystore").exists() -> rootProject.file("release.keystore")
                file("F:/release.keystore").exists() -> file("F:/release.keystore")
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
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
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
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation("org.jetbrains.kotlin:kotlin-reflect")
  testImplementation(libs.kotlinx.coroutines.test)

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

  // Lottie for Animations
  implementation(libs.lottie.compose)

  // Graphics Shapes for Material Morph Animations
  implementation(libs.androidx.graphics.shapes)
}


