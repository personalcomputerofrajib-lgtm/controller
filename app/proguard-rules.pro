# Add project specific ProGuard rules here.
-keep class com.wifimonitor.data.** { *; }
-keep class com.wifimonitor.network.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
