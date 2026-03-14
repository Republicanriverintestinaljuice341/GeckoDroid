# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.adaptix.client.models.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
