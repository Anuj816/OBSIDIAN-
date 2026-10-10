# ==============================================================================
# Obsidian - ProGuard & R8 Optimization Rules
# ==============================================================================

# --- General Optimization & Stack Trace Preservation ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# --- App Data Models & Serialization ---
-keep class com.anuj.obsidian.data.model.** { *; }
-keep class com.anuj.obsidian.data.local.** { *; }
-keep class com.anuj.obsidian.data.remote.** { *; }
-keep class com.anuj.obsidian.ui.dsp.** { *; }

# --- AndroidX Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-dontwarn androidx.room.paging.**

# --- Moshi & Retrofit ---
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn okhttp3.**

# --- OkHttp ---
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    java.lang.String[] PUBLIC_SUFFIX_LIST;
}

# --- AndroidX Media3 & Media Notification ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media.** { *; }
-dontwarn androidx.media.**

# --- Audio Tagging (JAudioTagger) ---
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# --- Coil Image Loading ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- AndroidX Palette ---
-keep class androidx.palette.graphics.** { *; }
-dontwarn androidx.palette.graphics.**

# --- Enum and Annotation Preservation ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}


# --- projectM Native JNI ---
-keep class com.anuj.obsidian.ui.visualizer.ProjectMNative { *; }
-keepclassmembers class com.anuj.obsidian.ui.visualizer.ProjectMNative {
    native <methods>;
}
