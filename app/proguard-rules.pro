# ProGuard / R8 rules for Hazineh (Financial Management) Android App

# Preserve line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable

# Room entities and DAOs
-keep class com.example.data.model.** { *; }
-keep class com.example.data.db.** { *; }
-keep class com.example.data.dao.** { *; }

# Moshi & Retrofit models / annotations
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
    @com.squareup.moshi.* <fields>;
}

# OkHttp / Okio optional Conscrypt / BouncyCastle warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WebView JavaScript interfaces (if enabled)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
