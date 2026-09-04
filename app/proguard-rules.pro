# ProGuard and R8 rules for Intelligent Search - NG Designs

# Preserve source file and line numbers for deobfuscated crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimization & Obfuscation
-repackageclasses ""
-allowaccessmodification

# Strip debug and verbose logging in release builds while preserving errors and warnings
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Titan M3+ Hardware Security, Keystore & Biometrics
-keep class com.pixel.intelligentsearch.core.security.** { *; }
-keepclassmembers enum com.pixel.intelligentsearch.core.security.HardwareSecurityLevel { *; }
-keepclassmembers class com.pixel.intelligentsearch.core.security.AttestationResult { *; }
-keepclassmembers class com.pixel.intelligentsearch.core.security.EncryptedPayload { *; }

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
-keepnames class androidx.navigation3.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Lottie Animations
-keep class com.airbnb.lottie.** { *; }

# Google Generative AI SDK
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Coroutines
-dontwarn kotlinx.coroutines.**
