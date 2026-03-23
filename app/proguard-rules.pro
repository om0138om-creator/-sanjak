# proguard-rules.pro
# تم تصميم التطبيق بواسطة عمر سنجق

# ==================== General ====================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# ==================== Kotlin ====================
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ==================== Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# ==================== Compose ====================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==================== Coil ====================
-keep class coil.** { *; }
-dontwarn coil.**

# ==================== App Classes ====================
-keep class com.omarssinjaq.quickshare.** { *; }
-keepclassmembers class com.omarssinjaq.quickshare.** {
    *;
}

# ==================== Data Classes ====================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==================== Enums ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== Serialization ====================
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==================== Remove Logging ====================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}