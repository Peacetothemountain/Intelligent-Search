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

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Lottie Animations
-keep class com.airbnb.lottie.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
