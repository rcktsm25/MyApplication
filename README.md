# RocketPlayer — Classic Audio

Reproductor Android nativo en Kotlin, inspirado en Winamp Classic. Abre directamente, sin login de RocketPlayer. No guarda playlists propias ni modifica las listas de tu cuenta de Spotify.

## Uso

- **MÚSICA:** elige CARPETA en el selector de Android y confirma el permiso. Se leen archivos de audio de la carpeta y sus subcarpetas. El filtro busca por nombre y carpeta; toca una canción para reproducirla. Mantén pulsado CARPETA para actualizar. **Explorar carpetas** permite buscar rutas, entrar en subcarpetas y mostrar sus canciones (incluidas las descendientes). **Nombre ↑** abre el orden por nombre, artista o duración. Se leen etiquetas y duración en segundo plano; los archivos sin duración conocida aparecen al final de ese orden.
- **SPOTIFY:** conecta la biblioteca una vez para ver tus Me gusta o buscar una canción o artista. Toca una canción para reproducirla en la app oficial. VER MÁS CANCIONES permite seguir explorando los resultados. **Ordenar** ordena las canciones ya cargadas por nombre, artista o duración; no modifica la biblioteca de Spotify.
- **SONIDO:** diez bandas reales de ±12 dB, presets, preamplificación y balance para audio local. Desliza horizontalmente para ver las diez bandas. Los ajustes se conservan. Para Spotify abre su propio ecualizador desde la app oficial; RocketPlayer no procesa el audio de Spotify.
- **AJUSTES → APARIENCIA · CLASSIC:** 12 colores (incluido el verde clásico), color personalizado con tono, intensidad y brillo, ocho fondos para los paneles y un fondo personalizado. Incluye vista previa y restauración del estilo clásico. **Guardar tema** conserva fondo, color y todas las opciones del visualizador; **Mis temas** permite aplicar o eliminar combinaciones. Los temas no incluyen permisos, carpetas ni cuentas. Los cambios se aplican al display, títulos de canciones, controles y visualizador y se conservan al cerrar la app. También incluye visualizador, pantalla encendida, velocidad de audio local, temporizador local, carpeta y conexión de Spotify.

La interfaz conserva los paneles gris azulado biselados, franjas de título doradas, display negro, reloj digital y botones metálicos de Winamp Classic, con controles adaptados al teléfono.

Los controles de transporte, seek, volumen, azar y repetición reflejan el motor activo. Repetición alterna apagado, todas y una. Stop pausa y vuelve al inicio. El visualizador analiza el audio local mediante FFT y muestras PCM, sin micrófono. En **AJUSTES → VISUALIZADOR LOCAL** puedes elegir LED clásico, barras sólidas, línea de espectro u onda de audio, ajustar la respuesta del espectro y activar picos, colores clásicos y cuadrícula. La vista previa utiliza el audio que se está reproduciendo, y queda en reposo al pausar. Toca el display del reproductor o la vista previa para abrir el visualizador a pantalla completa, con selector de estilo y botón Cerrar. El temporizador se ejecuta en el servicio y funciona con la pantalla apagada; cambiar a Spotify lo cancela.

La canción local y su posición se guardan al pausar, buscar, cambiar de pista y cada cinco segundos durante la reproducción. Al volver a abrir, se restaura en pausa, junto con azar y repetición. Si el archivo ya no está disponible, se elige el primero de la biblioteca. Spotify conserva su posición mediante la app oficial.

## Nuevas Funcionalidades (v1.1)

RocketPlayer v1.1 incluye **16 mejoras** sobre la versión original:

### Funcionalidades Core
1. **Cola de reproducción visible** con drag & drop para reordenar y eliminar canciones
2. **Crossfade entre canciones** (transición suave de 2-10 segundos, configurable)
3. **Edición de metadatos locales** (título, artista, álbum, género sin modificar el archivo)
4. **Letras de canciones** mediante API LRCLib (gratuita, con soporte sincronizado)

### UI/UX
5. **Modo oscuro/claro automático** del sistema (toggle en ajustes)
6. **Nuevos estilos de visualizador**: LED, Barras, Espectro, Onda (osciloscopio), Partículas
7. **Gestos en el display**: swipe vertical (volumen), doble tap (visualizador), long press (metadatos)
8. **Vista de álbumes/artistas agrupados** (agrupar por álbum, artista o vista plana)

### Mejoras Técnicas
9. **Optimización de memoria** (ListAdapter con DiffUtil, carga perezosa preparada)
10. **Pruebas unitarias** (40 tests, incluyendo QueueManager, AudioDsp, etc.)
11. **Manejo de errores de red mejorado** (reintentos, timeouts, mensajes específicos)
12. **Notificación multimedia mejorada** (controles de bloqueo, PendingIntent)

### Extras
13. **Widget para pantalla de inicio** (2x2, estética Winamp, controles básicos)
14. **Android Auto support** (MediaSession configurado, requiere manifest adicional)
15. **Exportar/importar configuración** (JSON con temas, EQ, preferencias, metadatos)
16. **ReplayGain** (normalización de volumen entre canciones, modo pista/álbum)

Ver [NEW_FEATURES.md](NEW_FEATURES.md) para documentación detallada de cada mejora y [FeatureIntegration.kt](app/src/main/java/com/example/myapplication/FeatureIntegration.kt) para instrucciones de integración.

## Spotify

Requiere la app oficial y una cuenta con acceso al Client ID configurado. App Remote controla la reproducción; la Web API permite seleccionar canciones individuales. En AJUSTES → Configurar conexión introduce el Client ID. En [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) configura:

- Paquete: `com.example.myapplication`.
- Redirect URI: `rocketplayer://spotify-callback`.
- SHA-1: la huella de la instalación que muestra el diálogo; también se obtiene con `./gradlew signingReport`.
- API utilizada: Web API, además de Android SDK.

La biblioteca se autoriza con [Authorization Code + PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow), sin Client Secret. Se solicita `user-library-read` para Me gusta y `app-remote-control` para reproducción. El verificador y `state` se validan por intento; los tokens se guardan cifrados con una clave Android Keystore y quedan fuera de las copias de seguridad. Hay renovación automática y un reintento tras un 401. Desconectar elimina la autorización local; no modifica tu cuenta.

La búsqueda se pagina de diez en diez siguiendo el [límite de Spotify](https://developer.spotify.com/documentation/web-api/reference/search). Me gusta se pagina de cincuenta en cincuenta. La app descarta canciones nulas, archivos locales de Spotify y recursos no reproducibles. Los errores de cuenta/API, conexión y límites de solicitudes se muestran en pantalla.

## Audio y arquitectura

- `PlaybackService`: Media3/ExoPlayer, audio focus, pausa al desconectar auriculares, notificación y temporizador.
- `PlayerAudioProcessor` / `AudioDsp`: PCM de 16 bits, diez filtros biquad, preamp y balance; el estado es independiente por canal. El motor convierte los formatos admitidos a PCM para procesarlos.
- `AudioSpectrum` / `VisualizerView`: FFT de 512 muestras y 28 barras.
- `MusicFolderScanner`: consultas SAF en segundo plano, permiso persistente y búsqueda recursiva. Sin acceso global al almacenamiento.
- `SpotifyManager`: App Remote y alternativa de autorización por navegador para fallos del servicio nativo.
- `SpotifyWebApi` / `SpotifyWebAuthActivity`: lectura del catálogo con PKCE y almacenamiento cifrado.
- `SpotifyLibraryBrowser`: selección de canciones; cancela resultados obsoletos al cambiar de búsqueda.
- `PlayerPanels`: ajustes y ecualizador nativos accesibles y desplazables.

El ecualizador local no aplica a Spotify. El procesamiento usa PCM; la decodificación concreta depende de los formatos y del dispositivo. ReplayGain, crossfade, edición de etiquetas y plugins Winamp no están implementados. Consulta la [revisión de funciones de Winamp](docs/WINAMP_FEATURES.md).

## Compilación y pruebas

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Las 33 pruebas unitarias cubren reconocimiento de audio, enlaces y callbacks Spotify, carreras de autorización, bypass, balance, respuesta del EQ, preamplificación, estabilidad, espectro y fidelidad de las muestras de la onda de audio. Se verificaron además en el teléfono el procesamiento PCM real, el vector PKCE del RFC 7636 y la lectura/búsqueda de canciones de Spotify (tres pruebas). También se verificaron en Android la exploración de subcarpetas con metadatos, la reproducción de sus archivos, guardar/aplicar/eliminar temas y restaurar la canción local con su posición en pausa. Una prueba adicional de Android aplica los ocho fondos a la interfaz real y verifica sus paneles y franjas de título. Las pruebas de dispositivo de carpetas usan un proveedor de documentos de prueba. La prueba App Remote es optativa; no necesita ejecutarse para usar la app. Para conservar los permisos SAF del teléfono durante verificaciones, instala los APK con `adb install -r` y ejecuta el runner directamente; evita un flujo de pruebas que desinstale la app.

## Icono

Cohete con cuerpo de reproductor, display verde y controles de transporte. El PNG transparente está integrado en los iconos adaptativos y en la cabecera. [Prompt y herramienta utilizados](docs/ICON_GENERATION.md).
