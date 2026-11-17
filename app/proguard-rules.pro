# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tristan.spotifyfast.data.remote.**$$serializer { *; }
-keepclassmembers class com.tristan.spotifyfast.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.tristan.spotifyfast.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Spotify App Remote & Protocol + Gson
-keep class com.spotify.android.appremote.** { *; }
-keep class com.spotify.protocol.** { *; }
-keepclassmembers class * implements com.spotify.protocol.types.Item { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.naming.**
-dontwarn javax.net.ssl.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
