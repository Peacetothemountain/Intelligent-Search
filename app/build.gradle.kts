import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ngdesigns.intelligentsearch"
    compileSdk = 37
    defaultConfig {
        // Its own application id, so it installs next to Intelligent Search instead of replacing it.
        applicationId = "com.pixel.intelligentsearch.dev2"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "2.0-dev"
    }

    signingConfigs {
        create("release") {
            val local = Properties().apply {
                rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
            }
            val store = local.getProperty("RELEASE_STORE_FILE")?.let(::file)?.takeIf { it.exists() }
            val storePass = local.getProperty("RELEASE_STORE_PASSWORD")
            val alias = local.getProperty("RELEASE_KEY_ALIAS")
            val keyPass = local.getProperty("RELEASE_KEY_PASSWORD")
            if (store != null && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = store
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Never fall back to the debug key for a release build.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Screenshot goldens: record with -Proborazzi.record, compare otherwise.
                test.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record")?.toString() ?: "false")
                test.systemProperty("roborazzi.test.verify", if (project.hasProperty("roborazzi.record")) "false" else "true")
                test.inputs.files(project.fileTree("src/test/screenshots")).withPropertyName("screenshotGoldens")
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

composeCompiler {
    includeSourceInformation.set(false)
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore)
    implementation(libs.material.kolor)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
