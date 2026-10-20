# 🚀 RocketPlayer — Reproductor de Audio y Streaming Estilo Winamp

Aplicación Android nativa desarrollada en **Kotlin**, inspirada en la clásica estética retro de **Winamp** y **Rocket Player**, impulsada por la arquitectura moderna de **AndroidX Media3 (ExoPlayer)** y autenticación en la nube con **Firebase Authentication**.

---

## 🛠️ Tecnologías y Librerías Utilizadas

- **Lenguaje:** Kotlin 2.1.0 (JVM 11)
- **SDK Target / Min:** Android SDK 35+ / Min SDK 24 (Android 7.0+)
- **Audio Engine:** `androidx.media3:media3-exoplayer:1.4.1`
- **Background Playback & Notification:** `androidx.media3:media3-session:1.4.1`
- **Autenticación en la Nube:** Firebase BoM `33.7.0` + `firebase-auth` (Google Services `4.4.2`)
- **Renderizado Gráfico:** Vista personalizada sobre `Canvas` con aceleración por hardware (GPU)
- **Arquitectura de Interfaz:** ViewBinding, ConstraintLayout, Edge-to-Edge y RecyclerView

---

## ⚙️ ¿Cómo Funciona Todo? (Arquitectura Técnica)

### 1. Motor de Audio en Segundo Plano (`Media3`)
- **`PlaybackService.kt`:** Extiende de `MediaSessionService`. Instancia y gestiona el ciclo de vida del reproductor `ExoPlayer` en un servicio en primer plano (`foregroundServiceType="mediaPlayback"`).
- **Control de Notificaciones:** Emite automáticamente controles multimedia en la barra de notificaciones y pantalla de bloqueo del sistema Android con un `PendingIntent` inmutable hacia `MainActivity`.
- **`MainActivity.kt`:** Se conecta al servicio de manera asíncrona mediante un `MediaController` asociado al `SessionToken`. Esto asegura que si la interfaz se cierra, la música continúe sonando sin interrupciones.
- **Sincronización del Segundero y SeekBar:** Para garantizar que el tiempo (`00:00`) y la barra de progreso avancen fluidamente, se implementa un `Handler(Looper.getMainLooper())` que consulta `player.currentPosition` cada 1 segundo mientras `player.isPlaying` es verdadero, pausándose en segundo plano para ahorrar batería.

### 2. Visualizador de Espectro por Hardware (`VisualizerView.kt`)
- Dibuja 14 barras dinámicas directamente en el método `onDraw(canvas: Canvas)`.
- **Sin Layout Thrashing:** En lugar de alterar parámetros de vistas XML (lo que genera caídas de FPS y sobrecalentamiento), calcula la altura de cada barra matemáticamente mediante interpolación suave (`linear interpolation / easing`) hacia alturas objetivo aleatorias.
- Cambia sus colores dinámicamente según el tema seleccionado en la aplicación.

### 3. Autenticación Híbrida (`LoginActivity.kt`)
- **Conexión Firebase Auth:** Conectado al proyecto Firebase `app-lot-viernes` mediante `app/google-services.json`.
- **Auto-Registro y Normalización:** Permite ingresar tanto correos estándar (`usuario@email.com`) como alias (`samuel`, normalizado internamente a `samuel@rocketplayer.app`). Si el usuario no existe, Firebase lo registra automáticamente; si ya existe, valida sus credenciales.
- **Resiliencia Offline y Modo Invitado:** Si el dispositivo no tiene conexión a internet o el usuario prefiere no registrarse, el botón **"ENTRAR COMO INVITADO"** habilita una sesión local inmediata mediante `SharedPreferences`.
- **Cierre de Sesión:** El botón de apagado en `MainActivity` ejecuta `FirebaseAuth.getInstance().signOut()`, limpia las preferencias y redirige de manera segura a la pantalla de acceso.

### 4. Sistema de Skins y Temas Dinámicos
La app cuenta con 5 temas intercambiables en caliente sin reiniciar la actividad:
1. **ROCKET:** Naranja neón (#FF4500) y dorado sobre negro profundo.
2. **CLASSIC:** Verde fósforo clásico de Winamp (#00FF00) sobre gris metálico.
3. **MATRIX:** Verde digital (#00FF41) sobre negro absoluto.
4. **CYBER:** Cian (#00F3FF) y magenta sobre fondo púrpura oscuro.
5. **GOLD:** Dorado de lujo (#FFD700) sobre carbón mate.

### 5. Integración con Spotify (Spotify App Remote SDK)
- **Controlador Bidireccional (`SpotifyManager.kt`):** Conecta RocketPlayer directamente con la app de Spotify instalada en el dispositivo mediante comunicación inter-procesos (IPC).
- **Suscripción a Eventos en Tiempo Real:** Escucha `PlayerState` para reflejar el título de la canción, artista, progreso y duración en la pantalla retro de Winamp.
- **Reproducción Híbrida Inteligente:** Al pulsar una estación de radio web se reproduce vía ExoPlayer y se pausa Spotify; al pulsar una pista o playlist de Spotify (`spotify:...` o `open.spotify.com`), se pausa la radio y toma el control Spotify.
- **Configuración en Spotify Developer Dashboard:**
  - **Package Name:** `com.example.myapplication`
  - **Redirect URI:** `rocketplayer://spotify-callback`
  - **SHA-1 (Debug):** `26:28:A4:37:81:DA:A7:ED:A7:28:41:2E:6D:50:8B:CB:DA:A3:F0:CB`

---

## 🚀 Guía de Instrucciones para Futuras Mejoras

### 💡 Mejora 1: Guardar Favoritos y Estaciones en Cloud Firestore
Actualmente los streams añadidos por el usuario se guardan en `SharedPreferences`. Para sincronizarlos entre diferentes dispositivos:
1. Añade la dependencia de Firestore en `gradle/libs.versions.toml`:
   ```toml
   firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
   ```
2. En `app/build.gradle.kts`:
   ```kotlin
   implementation(libs.firebase.firestore)
   ```
3. En `MainActivity.kt`, guarda los streams en una colección de usuario:
   ```kotlin
   val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
   val db = FirebaseFirestore.getInstance()
   db.collection("users").document(uid).collection("streams").add(track)
   ```

### 💡 Mejora 2: Cargar Música Local del Teléfono (Archivos MP3)
Para reproducir canciones almacenadas en el almacenamiento interno del dispositivo:
1. En `MainActivity.kt`, consulta el `ContentResolver` del sistema:
   ```kotlin
   val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA)
   val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
   ```
2. Convierte cada fila en un objeto `Track(title, artist, path)` y agrégalo a la lista `playlist`.

### 💡 Mejora 3: Ecualizador de Audio Gráfico de 5 Bandas
Para implementar ecualizador físico estilo Winamp:
1. Obtén el identificador de sesión de audio desde `ExoPlayer`:
   ```kotlin
   val audioSessionId = (player as? ExoPlayer)?.audioSessionId ?: return
   val equalizer = android.media.audiofx.Equalizer(0, audioSessionId)
   equalizer.enabled = true
   ```
2. Vincula 5 SeekBars verticales para ajustar las bandas de frecuencia (`60Hz`, `230Hz`, `910Hz`, `3.6kHz`, `14kHz`).

### 💡 Mejora 4: Caché Inteligente de Streams con ExoPlayer
Para evitar micro-cortes si la conexión a internet es inestable:
1. Configura un `SimpleCache` en `PlaybackService.kt`:
   ```kotlin
   val cache = SimpleCache(File(cacheDir, "media_cache"), LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), StandaloneDatabaseProvider(this))
   val cacheDataSourceFactory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
   ```

---

## 📦 Compilación y Ejecución

Para compilar el proyecto y generar el APK:
```bash
./gradlew assembleDebug
```
El archivo ejecutable se generará en:
`app/build/outputs/apk/debug/app-debug.apk`
