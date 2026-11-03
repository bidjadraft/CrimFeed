# Safe R8 tree-shaking without obfuscation or bytecode optimization
-dontobfuscate
-dontoptimize

-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,SourceFile,LineNumberTable

# Keep all CrimsonFeed application classes, Room entities/DAOs, Navigation routes, and @Serializable models
-keep class com.example.** { *; }
-keep interface com.example.** { *; }

# Keep kotlinx.serialization runtime & generated serializers
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }

# Keep Retrofit service methods
-keepclassmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# Suppress benign optional platform warnings in OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
