# RocketPlayer v1.1 — Nuevas Funcionalidades

## Resumen de Mejoras Implementadas (16 total)

### 🎵 Funcionalidades Core

#### 1. Cola de Reproducción Visible con Drag & Drop
- **Archivo**: `QueueManager.kt`, `QueueAdapter.kt`, `item_queue.xml`, `panel_queue.xml`
- **Funcionalidad**:
  - Ver y gestionar la cola de reproducción actual
  - Reordenar canciones con drag & drop
  - Eliminar canciones individuales (swipe o botón X)
  - Soporte para limpiar toda la cola o activar modo aleatorio
  - Indicador visual de canción actual (▶)
- **Integración**: Ver `FeatureIntegration.kt` sección PASO 3-4

#### 2. Crossfade entre Canciones
- **Archivo**: `CrossfadeManager.kt`
- **Funcionalidad**:
  - Transición suave de 2-10 segundos entre pistas
  - Fade out gradual del volumen antes de cambiar
  - Configurable desde panel de ajustes
  - Se activa automáticamente al final de cada canción
  - No afecta a Spotify (solo audio local)
- **Uso**: `crossfadeManager.checkAndStartCrossfade()` en `renderPlayback()`

#### 3. Edición de Metadatos Locales
- **Archivo**: `MetadataEditor.kt`, `dialog_metadata_editor.xml`
- **Funcionalidad**:
  - Editar título, artista, álbum y género sin modificar el archivo
  - Los metadatos se guardan en SharedPreferences
  - Se aplican automáticamente al mostrar la canción
  - Long press en el display para abrir el editor
  - Opción para restaurar metadatos originales
- **Integración**: Agregar botón en UI o usar long press gesture

#### 4. Letras de Canciones (API LRCLib)
- **Archivo**: `LyricsFetcher.kt`, `item_lyrics.xml`
- **Funcionalidad**:
  - Búsqueda automática de letras usando LRCLib (gratuita, sin API key)
  - Soporte para letras sincronizadas (time-tagged)
  - Muestra tiempo real si la letra está sincronizada
  - Funciona con audio local y Spotify
  - Manejo de errores y estados de carga
- **Uso**: `lyricsFetcher.fetch(title, artist, durationMs) { lyrics -> ... }`

### 🎨 UI/UX

#### 5. Modo Oscuro/Claro Automático del Sistema
- **Archivo**: `SystemThemeHandler.kt`
- **Funcionalidad**:
  - Detecta automáticamente si el sistema usa modo oscuro o claro
  - Aplica colores apropiados (fondo, texto, acentos)
  - Toggle en ajustes para activar/desactivar
  - Preserva los temas personalizados si está desactivado
  - Se actualiza al cambiar la configuración del sistema
- **Integración**: Llamar en `onCreate()` y `onConfigurationChanged()`

#### 6. Nuevos Estilos de Visualizador
- **Archivos**: `OscilloscopeView.kt`, `ParticleVisualizer.kt`, `panel_visualizer_selector.xml`
- **Estilos disponibles**:
  - **LED Clásico**: El visualizador original de barras verdes
  - **Barras Sólidas**: Barras más gruesas con gradiente
  - **Espectro**: Análisis de frecuencia con líneas
  - **Onda (Osciloscopio)**: Forma de onda del audio en tiempo real
  - **Partículas**: Partículas reactivas que bailan con la música
- **Integración**: Selector en panel de visualizador o ajustes

#### 7. Gestos en el Display
- **Archivo**: `DisplayGestureHandler.kt`
- **Gestos soportados**:
  - **Swipe vertical arriba/abajo**: Subir/bajar volumen
  - **Doble tap**: Abrir visualizador a pantalla completa
  - **Long press**: Abrir editor de metadatos
  - **Tap simple**: Toggle play/pause (opcional)
- **Integración**: `gestureHandler.attachToView(binding.meterRow, ...)`

#### 8. Vista de Álbumes/Artistas Agrupados
- **Archivo**: `GroupedLibraryView.kt`, `panel_grouped_library.xml`
- **Funcionalidad**:
  - Agrupar canciones por álbum
  - Agrupar canciones por artista
  - Vista plana tradicional (sin agrupar)
  - Botones para cambiar entre modos
  - Orden alfabético automático
- **Integración**: Agregar tab o botón en biblioteca local

### ⚡ Mejoras Técnicas

#### 9. Optimización de Memoria (Preparada)
- **Estrategia**:
  - Usar `ListAdapter` con `DiffUtil` (ya implementado en `QueueAdapter`)
  - Carga perezosa de metadatos
  - Paginación en RecyclerView para bibliotecas grandes
  - `visibleLocalTracks()` filtra antes de mostrar
- **Recomendación**: Para bibliotecas >1000 canciones, activar paginación

#### 10. Pruebas Unitarias Faltantes
- **Archivos**: `QueueManagerTest.kt` y más en `test/`
- **Cobertura actual**:
  - `QueueManager`: 7 tests (colas, eliminación, movimiento, next)
  - `AudioDsp`, `AudioSpectrum`, `SpotifyManager` (ya existentes)
- **Para agregar**: Tests para `CrossfadeManager`, `LyricsFetcher`, `MetadataEditor`

#### 11. Manejo de Errores de Red Mejorado
- **Mejoras**:
  - Mensajes específicos para errores de Spotify
  - Reintentos automáticos en letras (LRCLib)
  - Timeout configurables
  - Estados de carga visibles (spinner + mensaje)
- **Archivos**: `LyricsFetcher.kt` (manejo de errores integrado)

#### 12. Notificación Multimedia Mejorada
- **Archivo**: `PlaybackService.kt` (ya existente, mejorado)
- **Mejoras**:
  - PendingIntent configurado para abrir MainActivity
  - Soporte para MediaSession (ya implementado)
  - Compatibilidad con controles de pantalla de bloqueo
  - Android Auto compatible

### 🎯 Extras

#### 13. Widgets para Pantalla de Inicio
- **Archivos**: `RocketPlayerWidget.kt`, `widget_rocket_player.xml`, `rocket_player_widget_info.xml`
- **Funcionalidad**:
  - Widget 2x2 con estética Winamp
  - Controles: anterior, play/pause, siguiente
  - Muestra título y artista actual
  - Toca el widget para abrir la app
  - Se actualiza automáticamente con el estado de reproducción
- **Configuración**: Agregar al `AndroidManifest.xml`:
  ```xml
  <receiver android:name=".RocketPlayerWidget" android:exported="false">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
          <action android:name="rocketplayer.WIDGET_PLAY"/>
          <action android:name="rocketplayer.WIDGET_PAUSE"/>
          <action android:name="rocketplayer.WIDGET_NEXT"/>
          <action android:name="rocketplayer.WIDGET_PREV"/>
      </intent-filter>
      <meta-data android:name="android.appwidget.provider"
                 android:resource="@xml/rocket_player_widget_info"/>
  </receiver>
  ```

#### 14. Android Auto / Android Automotive
- **Archivo**: `AutomotiveMetadataService.kt`
- **Funcionalidad**:
  - Servicio preparado para detección por Android Auto
  - MediaSession ya configurado en `PlaybackService`
  - Requiere configuración adicional en `AndroidManifest.xml`
  - Ver comentarios en el archivo para instrucciones completas

#### 15. Exportar/Importar Configuración
- **Archivo**: `ConfigBackupManager.kt`
- **Funcionalidad**:
  - Exportar toda la configuración a JSON
  - Incluye: temas, ecualizador, preferencias, metadatos
  - Importar desde archivo JSON
  - Validación de integridad al importar
  - Opción para exportar a archivo o string
- **Uso**: 
  ```kotlin
  val backupManager = ConfigBackupManager(context)
  backupManager.exportToFile(file) // Exportar
  backupManager.importFromFile(file) // Importar
  ```

#### 16. ReplayGain (Normalización de Volumen)
- **Archivo**: `ReplayGainProcessor.kt`
- **Funcionalidad**:
  - Lee etiquetas ReplayGain de archivos de audio
  - Modo pista individual o modo álbum
  - Ajusta volumen automáticamente para nivel constante
  - Previene clipping usando peak values
  - Pre-gain configurable
- **Uso**:
  ```kotlin
  val replayGain = ReplayGainProcessor(prefs)
  replayGain.setEnabled(true)
  val volume = replayGain.calculateVolumeFactor(replayGainInfo)
  player.volume = volume
  ```

## Cómo Integrar las Mejoras

Ver `FeatureIntegration.kt` para instrucciones paso a paso de cómo conectar cada funcionalidad al `MainActivity`.

### Pasos Rápidos:

1. **Declarar las nuevas instancias** en `MainActivity`
2. **Inicializar en `onCreate()`**
3. **Configurar gestos en el display**
4. **Agregar tab de cola** (opcional)
5. **Agregar opciones en ajustes** (crossfade, replaygain, tema automático, backup)
6. **Actualizar `renderPlayback()`** para crossfade check
7. **Agregar widget al manifest** (opcional)

## Estética Winamp Preservada

Todas las nuevas vistas y componentes mantienen la estética Winamp Classic:
- Paneles gris azulado biselados (`#1A2129`, `#203044`)
- Franjas de título doradas (`winamp_title_strip`)
- Display negro (`#0A0A0F`)
- Fuentes monospace
- Botones metálicos (`panel_button`, `ProTransport`)
- Acento verde clásico (`#B6EE65`)

## Compilación y Pruebas

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Las pruebas unitarias cubren:
- `QueueManager` (7 tests)
- `AudioDsp`, `AudioSpectrum` (existentes)
- `SpotifyManager` callbacks (existentes)

## Notas de Migración

- **No breaking changes**: Todas las mejoras son aditivas
- **Configuración por defecto**: Desactivada excepto visualizador LED (comportamiento actual)
- **Temas existentes**: No se modifican, solo se agrega opción de tema automático
- **Spotify**: Las mejoras de audio no aplican a Spotify (intencional)
