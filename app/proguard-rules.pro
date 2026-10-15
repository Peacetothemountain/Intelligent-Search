# ProGuard and R8 rules for Intelligent Search - NG Designs

# Preserve source file and line numbers for deobfuscated crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimization & Obfuscation
-repackageclasses ""
-allowaccessmodification

# Strip verbose, debug, info and warning logs from release builds (several include package names or
# queries); errors stay for diagnosing crashes over adb.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
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
-keep,allowobfuscation class com.pixel.intelligentsearch.core.navigation.** { *; }
-keepclasseswithmembers class com.pixel.intelligentsearch.core.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Assume Log.isLoggable returns false in release builds for dead-branch elimination
-assumevalues class android.util.Log {
    boolean isLoggable(java.lang.String, int) return false;
}

# Material 3 Dynamic Colors
-keep class com.google.android.material.color.DynamicColors { *; }

# Hilt & Architecture Components
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Android 14/15/16 Hidden APIs accessed safely via reflection
-dontwarn android.content.pm.LauncherUserInfo
-dontwarn android.os.UserProperties


