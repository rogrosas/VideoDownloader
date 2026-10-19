# Video Downloader (Android)

App Android (Kotlin + Jetpack Compose) para descargar vídeos y listas eligiendo la calidad.
Usa [youtubedl-android](https://github.com/yausername/youtubedl-android), que incluye yt-dlp + Python + ffmpeg,
así que soporta los ~1800 sitios que soporta yt-dlp.

## Funciones
- Pegar una URL o **compartirla** desde YouTube o el navegador → la app se abre con el enlace.
- Calidad: Máxima / 1080p / 720p / 480p / 360p / Solo audio MP3.
- **Ver calidades disponibles**: analiza el vídeo y muestra las resoluciones reales (p. ej. 2160p, 1440p…).
- **Listas**: descarga la lista completa o un rango (`1-10,15`). Si un vídeo falla, sigue con los demás.
- Las descargas siguen en segundo plano (WorkManager + notificación con botón Cancelar).
- Los archivos se guardan en `Descargas/VideoDownloader` (visibles en la galería).
- **Actualizar motor**: actualiza yt-dlp sin reinstalar la app.

## Sobre «verifica que no eres un robot»
La app **no salta ni resuelve captchas automáticamente**. Lo que hace:
1. **Verificación / sesión**: abre el sitio en un WebView. Tú resuelves la verificación (o inicias sesión).
   La app guarda las cookies y el User-Agent y yt-dlp los reutiliza (`--cookies`, `--user-agent`).
2. **Importar cookies.txt**: si el sitio no permite iniciar sesión dentro de un WebView (Google suele
   bloquearlo), exporta las cookies desde el navegador del ordenador con una extensión de "cookies.txt"
   e impórtalas.
3. **Peticiones más lentas** (`--sleep-requests`, `--sleep-interval` en listas) para provocar menos
   verificaciones y errores 429.

> Si usas cookies de tu cuenta, el sitio puede limitar o bloquear esa cuenta. Usa una cuenta secundaria.

## Compilar
Necesitas Android Studio (Ladybug o posterior) o el Android SDK con JDK 17.
```
./gradlew assembleDebug
# APKs: app/build/outputs/apk/debug/app-<abi>-debug.apk
```
Se genera un APK por arquitectura (Python + ffmpeg ocupan ~50 MB cada una):
- `arm64-v8a`: casi cualquier móvil de los últimos años. **Usa este si dudas.**
- `armeabi-v7a`: móviles antiguos de 32 bits.
- `x86_64`: emuladores y algunos Chromebooks.

GitHub Actions compila en cada push a `main`; los APK están en *Actions → ejecución → Artifacts*.
Con un tag `v*` (`git tag v1.0 && git push --tags`) se publican también en *Releases*.

## Limitaciones conocidas
- **YouTube** exige desde nov. 2025 un runtime de JavaScript (Deno, Node, QuickJS…) para que yt-dlp
  resuelva sus desafíos. Si la versión empaquetada no lo incluye, puede que falten formatos o fallen
  las descargas de YouTube. Primero pulsa «Actualizar motor». Si el problema sigue, revisa el wiki EJS de
  yt-dlp y el issue tracker de youtubedl-android.
- Las cookies del WebView no traen la fecha de caducidad real; se guardan con caducidad de 1 año.
  Si una descarga empieza a fallar por verificación, vuelve a guardar la sesión.
- Respeta los términos de uso de cada sitio y los derechos de autor del contenido.
