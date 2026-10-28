# Preserve line numbers and source files for Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Firebase Crashlytics
-keepattributes *Annotation*

# Preserve domain and data models for Firestore reflection and serialization
-keep class com.example.domain.model.** { *; }
-keepclassmembers class com.example.domain.model.** { *; }
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }

# Firestore field annotations and JavaBean getters/setters
-keepclassmembers class * {
    @com.google.firebase.firestore.** *;
}

# AndroidX Credentials, Play Services Auth, GoogleId, and Google API client
-keep class androidx.credentials.** { *; }
-keep interface androidx.credentials.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keep interface androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-dontwarn androidx.credentials.**
-dontwarn androidx.credentials.playservices.**
-dontwarn com.google.android.libraries.identity.googleid.**
-dontwarn com.google.android.gms.auth.api.identity.**

# Moshi / Retrofit
-keepclassmembers class * {
    @com.squareup.moshi.** *;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

