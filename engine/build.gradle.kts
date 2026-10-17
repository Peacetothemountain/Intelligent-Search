// The search engine: understanding a query and ranking what matches it. Plain Kotlin with no Android
// dependency, so every rule is unit tested on the JVM in seconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
