import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
  alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.pixel.intelligentsearch"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pixel.intelligentsearch"
        minSdk = 31
        targetSdk = 37
        versionCode = 127
        versionName = "9.4.1"
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
        // Screenshot tests record goldens with -Proborazzi.record (or =true) and compare against them otherwise,
        // =false included: any value but true used to switch the comparison off without recording either.
        val recordScreenshots = project.findProperty("roborazzi.record")?.toString()?.let { it.isEmpty() || it.toBoolean() } ?: false
        all { test ->
          test.systemProperty("roborazzi.test.record", recordScreenshots.toString())
          test.systemProperty("roborazzi.test.verify", (!recordScreenshots).toString())
          // Goldens are inputs: replacing an image must re-run the comparison instead of reusing a cached result.
          test.inputs.files(project.fileTree("src/test/screenshots")).withPropertyName("screenshotGoldens")
          // Goldens are recorded on Windows and match on CI's Linux runners; -PskipScreenshots leaves them out of a run.
          if (project.hasProperty("skipScreenshots")) test.exclude("**/screenshots/**")
          // The layout audit renders every screen at many device sizes and text sizes; it runs only on request
          // (-PlayoutAudit) and writes its report and captures to build/layout-audit.
          // Gradle's default 512 MB isn't enough for this many Robolectric and screenshot tests in one worker.
          test.maxHeapSize = "2g"
          // Robolectric runs each test on one thread, so the tests spread over up to three JVMs side by side (CI's four
          // cores: about a third of the time of one). The layout audit appends to one shared report, so it keeps to one.
          test.maxParallelForks =
            if (project.hasProperty("layoutAudit")) 1 else (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 3)
          // Widget doodles follow the date; an ordinary day keeps screenshots from changing on holidays.
          test.systemProperty("intelligentsearch.doodleDate", "2026-03-03")
          // The matte shells' film grain doesn't compress; goldens leave it out except the one that pins it.
          test.systemProperty("intelligentsearch.matteGrain", "off")
          test.systemProperty("layoutAudit", if (project.hasProperty("layoutAudit")) "true" else "false")
          test.systemProperty("layoutAuditDir", layout.buildDirectory.dir("layout-audit").get().asFile.path)
          // The audit appends each screen's findings to one report, so a run starts from an empty one; without this,
          // an earlier run's findings would stay in the report after they were fixed.
          if (project.hasProperty("layoutAudit")) {
            val auditDir = layout.buildDirectory.dir("layout-audit").get().asFile
            test.doFirst { auditDir.deleteRecursively() }
          }
        }
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
  implementation(libs.google.material)
  // Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.profileinstaller)
  // The overlay start-up profile, generated on a phone by :baselineprofile.
  baselineProfile(project(":baselineprofile"))
  // Weekly automatic encrypted backups.
  implementation(libs.androidx.work.runtime)
  implementation(platform(libs.androidx.compose.bom))

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // No ui-tooling: nothing uses @Preview, and it adds an exported PreviewActivity to debug builds.
  // The empty activity the Compose test rules host screens in; debug builds only.
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // Robolectric runs Android-dependent code (SharedPreferences, DataStore, Room, ContentResolver) on the JVM.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.work.testing)
  // Screenshot tests (Robolectric native graphics) that pin the UI while it is rebuilt.
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.android.compiler)

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
