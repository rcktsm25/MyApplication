# Referencia funcional: Winamp → RocketPlayer

Consulta: 26 de octubre de 2026. Se revisaron la [guía oficial de Winamp para Windows](https://support.winamp.com/winamp-desktop-player-for-windows), su [versión Android](https://support.winamp.com/winamp-legacy-players-for-android-mac) y una [captura de Winamp Classic](https://www.bleepstatic.com/images/news/software/w/winamp/winamp-5.9.1-rc/winamp-classic.jpg).

| Función de Winamp | En esta versión de RocketPlayer |
|---|---|
| Display, reloj, transporte, volumen y búsqueda temporal | Implementados, con posición y estado reales de Media3 o Spotify. |
| Anterior, reproducir, pausa, stop, siguiente | Implementados. Stop pausa y vuelve al inicio. |
| Shuffle y repeat | Implementados; muestran el estado del motor. Repetir alterna apagado, todas y una. |
| EQ de diez bandas | DSP para archivos locales: 60, 170, 310, 600, 1k, 3k, 6k, 12k, 14k, 16k Hz, ±12 dB. |
| Preamplificación y balance | Reales para audio local. Preamplificador −12 a +6 dB; balance por canal. |
| Presets de EQ | Plano, Rock, Electrónica, Voces, Graves y ajustes personalizados persistentes. Los presets con realce reducen el preamp para dejar margen. |
| Visualización | FFT real del audio local. Spotify no expone su PCM y no se muestra un espectro inventado. |
| Biblioteca y búsqueda | Carpeta local con subcarpetas y filtro; búsqueda de canciones y Me gusta de Spotify con paginación. |
| Skins/apariencia | Diseño inspirado en Classic; colores Fósforo, Ámbar e Hielo. No carga archivos de skins Winamp. |
| Preferencias de reproducción | Velocidad local, temporizador local, volumen multimedia y pantalla encendida opcional. |
| Segundo plano y controles del sistema | Media3 para archivos; Spotify controla su propia reproducción y notificación. |
| Playlists guardadas y login de RocketPlayer | Retirados por petición del usuario. No se borran playlists de la cuenta de Spotify. |
| EQ de Spotify | Se usa el de Spotify/sistema desde su aplicación. El DSP local no puede modificar el audio de otra app. |
| ReplayGain, crossfade, grabación de CD, conversión, etiquetas, vídeo, plugins DSP/MilkDrop, Shoutcast | No implementados. No se muestran controles decorativos para estas funciones. CD y plugins de escritorio no son directamente portables a Android. |

Spotify usa [PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow) con `user-library-read` para leer canciones. La [búsqueda](https://developer.spotify.com/documentation/web-api/reference/search) solicita diez resultados por página según el límite vigente. La reproducción se envía a App Remote. La autorización es de Spotify; RocketPlayer no crea cuentas ni pide contraseñas propias.
