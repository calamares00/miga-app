# Miga

**Tus recetas. Tus libros. Tu cocina.**

Miga es una app de recetas para Android, minimalista, sin cuentas y sin
anuncios. Funciona sin conexión: no necesita internet ni envía nada fuera
del teléfono, salvo si activas voluntariamente alguna función opcional que
sí la requiere (comprobar actualizaciones, añadir receta con foto vía IA).

## Funciones

- **Libros de recetas**: organiza tus recetas en libros independientes (por
  ejemplo, uno por cada miembro de la familia), cada uno con su propia
  portada.
- **Recetas completas**: ingredientes con cantidad y unidad, pasos
  numerados, foto, utensilios, dificultad, etiquetas y grupos de
  subreceta (salsas, guarniciones...).
- **Categorías, búsqueda y filtros** totalmente personalizables.
- **Modo cocina**: pantalla ampliada paso a paso.
- **Autocompletado de ingredientes** a partir de lo ya escrito.
- **Exportación/importación**: receta o libro entero como texto, PDF o
  JSON/ZIP (con fotos), y copia de seguridad completa de la biblioteca.
- **Añadir receta con foto** (beta, opcional): reconoce el texto de una
  foto (cámara o galería) con Google Gemini y precarga el editor para
  revisarlo. Requiere configurar tu propia API key de Gemini en Ajustes;
  sin ella, esta función no hace ninguna llamada de red.
- **Bloqueo biométrico** opcional (huella, rostro o PIN del dispositivo).
- **Tema** claro, oscuro o según el sistema.
- **Comprobación de actualizaciones** opcional contra las Releases de
  este repositorio.

## Stack técnico

- Kotlin + [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- [Room](https://developer.android.com/training/data-storage/room) para persistencia local
- [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) para ajustes
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- [Coil](https://coil-kt.github.io/coil/) para carga de imágenes
- `kotlinx.serialization` para exportación/importación en JSON
- `androidx.biometric` para el bloqueo por huella/rostro

## Privacidad

Miga no tiene cuentas, servidor propio, analítica ni publicidad. Ver
[PRIVACY.md](PRIVACY.md) para el detalle de qué datos maneja la app y las
pocas conexiones de red que hace (todas opcionales o de solo lectura).

## Compilar en local

```bash
./gradlew :app:assembleDebug
```

### Tests

```bash
./gradlew :app:testDebugUnitTest
```

Tests unitarios (JVM, sin emulador) de la lógica de dominio: filtrado y
orden de recetas, huella de invalidación de la valoración de salud,
comparación de versiones del comprobador de actualizaciones, construcción
de la URL del catálogo de packs, y la regla de solo-lectura de un pack.

El APK debug se firma con el keystore `debug.keystore` (versionado a
propósito en este repo — es el mismo tipo de clave que genera Android
Studio por defecto, sin datos sensibles) para que las instalaciones se
puedan actualizar sin desinstalar antes.

## CI/CD

El workflow `.github/workflows/android-build.yml` corre siempre, en cualquier
evento, los tests unitarios y una compilación `assembleRelease` de
comprobación (para detectar una regla de ProGuard/R8 rota antes de fusionar,
no solo al llegar a `main`); si algo de eso falla, el resto de pasos no se
ejecuta. Después, según el evento:

| Evento | Qué hace además |
|---|---|
| Push a cualquier rama que no sea `main` | Compila un APK **debug** (para probar el cambio) |
| Pull request hacia `main` | Lint + compilación de comprobación (sin publicar nada) |
| Push a `main` (tras un merge) | Compila APK **debug** + **release** (minificado con R8) por arquitectura (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, universal) + AAB para Play Store, y publica una [Release](../../releases) en GitHub con todo adjunto |

### Firma del build release

El build release se firma con un keystore que **no** vive en el
repositorio (a diferencia del de debug), sino en 4 secretos de GitHub
Actions (*Settings → Secrets and variables → Actions*):

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Mientras no existan, el build release sale sin firmar y la Release de
GitHub se marca como *pre-release*.

## Distribución

- `fastlane/metadata/android/es-ES/` contiene título, descripciones y
  changelog listos para dar de alta la app en Play Store o F-Droid.
- Cada Release de GitHub incluye APKs listos para instalar directamente
  o a través de [Obtainium](https://github.com/ImranR98/Obtainium), y
  un `.aab` para subir a Play Console.
