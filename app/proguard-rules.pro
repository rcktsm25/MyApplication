# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 Rules for RocketPlayer
# ═══════════════════════════════════════════════════════════════════

# Mantener clases de Spotify SDK (no ofuscar)
-keep class com.spotify.sdk.android.** { *; }
-keep class com.spotify.protocol.** { *; }
-keep class com.spotify.android.auth.** { *; }
-dontwarn com.spotify.protocol.mappers.jackson.**
-dontwarn com.spotify.base.annotations.**

# Ignorar clases de Jackson que el SDK de Spotify referencia pero no incluye
# El SDK usa Jackson internamente pero no expone la dependencia
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }

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

# Mantener clases de serialización JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# No ofuscar nombres de métodos de Activity/Service (referenciados en manifest)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Eliminar logging en release para reducir tamaño
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
