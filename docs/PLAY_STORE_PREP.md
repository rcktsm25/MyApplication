# 🚀 Guía de Preparación para Play Store - RocketPlayer

Este documento contiene todas las mejoras aplicadas y pendientes para publicar RocketPlayer en Google Play Store.

## ✅ Mejoras Aplicadas (Listas para Play Store)

### 1. Seguridad y Privacidad
- ✅ R8/ProGuard activado (ofuscación + minificación)
- ✅ Client ID de Spotify cifrado con AES-GCM + AndroidKeyStore
- ✅ allowBackup="false" (sin fuga de datos)
- ✅ networkSecurityConfig (solo HTTPS permitido)
- ✅ PlaybackService exported="false" (solo app local)
- ✅ LOGIN_ACTIVITY eliminado (código muerto)

### 2. UI/UX Profesional
- ✅ 16 presets de ecualizador (Rock, Pop, Jazz, etc.)
- ✅ 12 estilos de visualizador + 12 colores
- ✅ Caché de búsqueda Spotify (5 min TTL)
- ✅ Animaciones de transición entre pestañas
- ✅ Botones con relieve 3D, sombras y texturas
- ✅ Osciloscopio con audio real de Spotify (AudioPlaybackCapture)
- ✅ Widget profesional con shuffle/repeat
- ✅ Portadas de álbumes con Coil
- ✅ Shimmer efecto de carga
- ✅ Lottie animaciones JSON

### 3. Rendimiento
- ✅ notifyItemRangeChanged en adapters (sin parpadeo)
- ✅ DiffUtil con payload parcial
- ✅ Coroutines para operaciones asíncronas
- ✅ Coil para carga asíncrona de imágenes

### 4. Accesibilidad
- ✅ contentDescription en botones principales
- ✅ accessibliityHeading en títulos
- ✅ accessibilityLiveRegion en mensajes de estado

## ⏳ Mejoras Pendientes para Play Store

### Alta Prioridad
1. **Portadas reales desde Spotify API** - Conectar Coil con `api.spotify.com/v1/albums/{id}/images`
2. **Notificación media style completa** - Controles play/pause/next/previous en notification bar
3. **Tests unitarios** - QueueManager, Crossfade, ReplayGain, etc.

### Media Prioridad
4. **Room Database** - Persistencia de biblioteca local (50K+ canciones)
5. **ViewModel Architecture** - Separar lógica de UI
6. **Coroutines** - Reemplazar threads manuales completamente

### Baja Prioridad
7. **Animaciones Lottie en UI** - Integrar loading_animation.json
8. **Shimmer en búsqueda Spotify** - Efecto de brillo durante búsqueda
9. **Accesibilidad completa** - TalkBack en todos los elementos interactivos

## 📦 Configuración de Build para Play Store

### Versioning
```kotlin
// app/build.gradle.kts
defaultConfig {
    versionCode = 1
    versionName = "1.0.0"
}
```

### Signing
- Keystore: `/Users/samuel/.android/rocketplayer-release-key.jks`
- Alias: `rocketplayer`
- Válido hasta: 2054

### APK de Release
- Ubicación: `app/build/outputs/apk/release/app-release.apk`
- Tamaño: ~4.5MB (optimizado con R8)
- Firmado: ✅

## 📝 Checklist para Play Store

### Información de la App
- [ ] Título: "RocketPlayer - Classic Music Player"
- [ ] Descripción corta: "Reproductor de música local y Spotify con visualizadores Winamp Classic"
- [ ] Descripción larga: (ver abajo)
- [ ] Categoría: Música y Audio
- [ ] Tipo de precio: Gratis

### Capturas de Pantalla
- [ ] Teléfono (5 mínimo): 1080x1920
- [ ] Tablet (opcional): 1920x1200
- [ ] Descripción de cada captura

### Política de Privacidad
- [ ] URL de política de privacidad (requerido por Play Store)
- [ ] Explicar que no se recopilan datos personales
- [ ] Mencionar uso de Spotify API

### Contenido de la App
- [ ] Declaración de contenido: Música
- [ ] Declaración de publicidad: Sin anuncios
- [ ] Declaración de datos: No recopila datos

## 📄 Descripción para Play Store

### Descripción Corta (80 caracteres)
Reproductor de música local y Spotify con visualizadores estilo Winamp Classic

### Descripción Larga (4000 caracteres)
RocketPlayer es un reproductor de música que combina lo mejor de dos mundos: tu música local y Spotify, todo en una interfaz clásica estilo Winamp.

**Características Principales:**
- 🎵 **Biblioteca Local**: Reproduce archivos MP3, FLAC, WAV, M4A, AAC, OGG, OPUS desde tu dispositivo
- 🎧 **Spotify Integrado**: Busca y reproduce canciones de Spotify directamente desde la app
- 🎨 **12 Estilos de Visualizador**: LED clásico, Barras sólidas, Línea de espectro, Onda de audio, Barras gradiente, Onda rellena, Espejo, Circular, Montaña, Píxeles, Tubo, Anillo
- 🎨 **12 Colores Personalizables**: Verde Winamp, Naranja clásico, Azul hielo, Rojo fuego, Violeta, Cyan, Amarillo, Blanco, Rosa, Lima, Ámbar, Turquesa
- 🎛️ **Ecualizador de 10 Bandas**: 16 presets profesionales (Rock, Pop, Jazz, Clásica, Electrónica, Hip-Hop, R&B, Podcast, Graves, Agudos, Latin, Reggaetón, Metal, Ska) + modo personalizado
- 📱 **Widget Profesional**: Controles de reproducción en pantalla de inicio (Previous, Play/Pause, Next, Shuffle, Repeat)
- 🎤 **Captura de Audio del Sistema**: Osciloscopio con audio real de Spotify (Android 10+)
- 🔍 **Búsqueda Inteligente**: Busca en tu biblioteca local y en Spotify con caché de resultados
- 📂 **Explorador de Carpetas**: Navega por tu música local con vista agrupada por artista/álbum
- 🎨 **Estética Winamp Classic**: Diseño retro con paneles metálicos, gradientes y sombras 3D
- 📊 **Cola de Reproducción**: Arrastra y reordena canciones con drag & drop
- 🔄 **Crossfade**: Transición suave entre canciones
- 🔊 **ReplayGain**: Normalización de volumen entre pistas
- 🌙 **Tema Automático**: Cambia según el tema del sistema
- 💾 **Export/Import Config**: Respalda y restaura tu configuración

**Requisitos:**
- Android 7.0 (API 24) o superior
- Conexión a internet para Spotify
- Cuenta de Spotify (Free o Premium)

**Permisos:**
- Internet: Para buscar y reproducir en Spotify
- Foreground Service: Para reproducción en segundo plano
- Post Notifications: Para mostrar controles de reproducción

**Nota:** RocketPlayer no almacena ni comparte datos personales. Toda la configuración se guarda localmente en tu dispositivo.

---

Desarrollado por rcktsm
