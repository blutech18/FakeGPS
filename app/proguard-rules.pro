# Add project specific ProGuard rules here.

# ============================================================
# GENERAL
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

# ============================================================
# FIREBASE
# ============================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Firestore model classes (they use reflection for deserialization)
-keep class com.nodare.geosec.data.model.** { *; }
-keepclassmembers class com.nodare.geosec.data.model.** { *; }

# Firebase Auth
-keep class com.google.android.gms.internal.firebase-auth-api.** { *; }

# ============================================================
# GOOGLE PLAY SERVICES
# ============================================================
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ============================================================
# RETROFIT + OKHTTP
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Retrofit response models
-keep class com.nodare.geosec.data.remote.** { *; }
-keepclassmembers class com.nodare.geosec.data.remote.** { *; }

# ============================================================
# ROOM
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class com.nodare.geosec.data.local.entity.** { *; }

# ============================================================
# HILT / DAGGER
# ============================================================
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ============================================================
# KOTLIN COROUTINES
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================
# GOOGLE MAPS
# ============================================================
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# ============================================================
# NAVIGATION COMPONENT
# ============================================================
-keep class * extends androidx.navigation.Navigator

# ============================================================
# WORKMANAGER
# ============================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ============================================================
# LIFECYCLE / VIEWMODEL
# ============================================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
