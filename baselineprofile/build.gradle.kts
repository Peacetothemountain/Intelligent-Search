// Generates the overlay's Baseline Profile and measures overlay start-up, on a connected phone:
//   ./gradlew :app:generateBaselineProfile                      (writes the profile into the app)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  (start-up with and without it)
plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "com.pixel.intelligentsearch.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 31
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  targetProjectPath = ":app"
}

baselineProfile {
  // The owner's phone, not an emulator: the profile is meant for real Pixel hardware.
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
