# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 Rules for RocketPlayer
# ═══════════════════════════════════════════════════════════════════

# Mantener clases de Spotify SDK (no ofuscar)
-keep class com.spotify.sdk.android.** { *; }
-keep class com.spotify.protocol.** { *; }
-keep class com.spotify.android.auth.** { *; }

# Mantener Media3/ExoPlayer
-keep class androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# Mantener Gson (serialización)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Mantener Room entities
-keep class com.example.myapplication.data.** { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Mantener Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# Mantener clases de serialización JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# No ofuscar nombres de métodos de Activity/Service (referenciados en manifest)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optimizaciones agresivas para release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-assumenosideeffects class android.util.Log {
    public static *** w(...);
}

# Eliminar logging en release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
