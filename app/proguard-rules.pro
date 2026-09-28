# ProGuard and R8 rules for Intelligent Search - NG Designs

# Preserve source file and line numbers for deobfuscated crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimization & Obfuscation
-repackageclasses ""
-allowaccessmodification

# Strip debug, verbose, and info logging in release builds while preserving errors and warnings
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Titan M3+ Hardware Security, Keystore & Biometrics
-keep class com.pixel.intelligentsearch.core.security.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <init>(...);
    *** Companion;
}

# Jetpack Compose Navigation & Destinations
-keepnames class androidx.navigation.compose.** { *; }
-keep class com.pixel.intelligentsearch.core.navigation.** { *; }
-keepnames class com.pixel.intelligentsearch.core.navigation.** { *; }
-keepclassmembers class com.pixel.intelligentsearch.core.navigation.** { *; }
-keepclasseswithmembers class com.pixel.intelligentsearch.core.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Lottie Animations
-keep class com.airbnb.lottie.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# Assume Log.isLoggable returns false in release builds for dead-branch elimination
-assumevalues class android.util.Log {
    boolean isLoggable(java.lang.String, int) return false;
}

# Android Framework & Internal Reflection Targets
-dontwarn com.android.internal.os.PowerProfile
-keepclassmembers class com.android.internal.os.PowerProfile {
    public <init>(android.content.Context);
    public double getBatteryCapacity();
}

# Surface & Frame Pacing Reflection
-keep class androidx.graphics.surface.** { *; }
-dontwarn androidx.graphics.surface.**

# Material 3 Dynamic Colors
-keep class com.google.android.material.color.DynamicColors { *; }

# Hilt & Architecture Components
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

