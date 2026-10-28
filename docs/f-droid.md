# Publicación en F-Droid

Miga no tiene dependencias privativas (ni Google Play Services, ni Firebase, ni analítica), así que el
mismo código sirve para F-Droid sin variantes. F-Droid compila la app en sus servidores a partir de
una etiqueta de git de este repositorio.

## Antes de enviarla

- Que exista la etiqueta `v1.0.0` en `main`. La crea el CI al publicar la Release.
- Que la Release `v1.0.0` esté **firmada** con la clave de subida (secretos de GitHub configurados)
  e incluya `miga-v1.0.0-universal-release.apk`.
- Capturas en `fastlane/metadata/android/<idioma>/images/phoneScreenshots/` (las mismas que para
  Play). F-Droid lee título, descripciones, icono, capturas y changelogs de `fastlane/`.
- Huella SHA-256 del certificado de firma, sin dos puntos y en minúsculas:

  ```bash
  keytool -list -v -keystore miga-upload.jks -alias miga-upload \
    | grep 'SHA256:' | sed 's/.*SHA256: //; s/://g' | tr 'A-F' 'a-f'
  ```

## Envío

1. Crear una cuenta en https://gitlab.com y hacer un *fork* de https://gitlab.com/fdroid/fdroiddata.
2. En el fork, crear una rama y el fichero `metadata/org.calamares.miga.yml` con el contenido de
   abajo (sustituyendo la huella y el commit).
3. Abrir un *merge request* contra `fdroid/fdroiddata` con el título `New app: Miga` y rellenar la
   lista de comprobación de la plantilla. Los pipelines del MR (lint y build) tienen que pasar.
4. Responder a los revisores. Suele tardar entre días y semanas. Cuando lo fusionan, la app aparece
   en F-Droid tras el siguiente ciclo de compilación (uno o dos días más).

Alternativa sin GitLab ni YAML: abrir una petición en https://gitlab.com/fdroid/rfp/-/issues. Es
más lenta, porque depende de que un voluntario la empaquete.

## Metadatos (`metadata/org.calamares.miga.yml`)

```yaml
Categories:
  - Diet
License: GPL-3.0-only
AuthorName: Calamares
AuthorEmail: miga@calamares.org
WebSite: https://miga.calamares.org
SourceCode: https://github.com/calamares00/miga-app
IssueTracker: https://github.com/calamares00/miga-app/issues
Changelog: https://github.com/calamares00/miga-app/releases

AutoName: Miga

RepoType: git
Repo: https://github.com/calamares00/miga-app.git
Binaries: https://github.com/calamares00/miga-app/releases/download/v%v/miga-v%v-universal-release.apk

Builds:
  - versionName: 1.0.0
    versionCode: 193
    commit: <hash completo del commit de la etiqueta v1.0.0>
    subdir: app
    gradle:
      - yes
    output: build/outputs/apk/release/app-universal-release-unsigned.apk

AllowedAPKSigningKeys: <huella SHA-256 del certificado>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 193
```

- `versionCode` y `CurrentVersionCode`: el `versionCode` de `app/build.gradle.kts` en la etiqueta.
- `Categories`: el revisor puede pedir otra; F-Droid revisa su lista de categorías a menudo.
- `output`: con las divisiones por ABI, Gradle genera varios APK; F-Droid usa solo el universal.
- `Binaries` + `AllowedAPKSigningKeys` piden una **compilación reproducible**: F-Droid compila,
  compara su APK con el de la Release y, si son idénticos, publica el nuestro con nuestra firma.
  Así quien instala desde F-Droid, GitHub u Obtainium puede pasar de uno a otro sin desinstalar.
  Si no coinciden, el revisor lo dirá: o se corrige la diferencia o se quitan esas dos líneas y
  F-Droid firma con su propia clave (entonces las instalaciones de F-Droid no se pueden actualizar
  con APK de otra fuente, ni al revés).
- Con `AutoUpdateMode: Version` y `UpdateCheckMode: Tags`, cada nueva etiqueta `vX.Y.Z` se compila
  sola; no hace falta otro merge request.

## Antifunciones

F-Droid marcará previsiblemente **NonFreeNet**: el asistente de cocina usa servicios no libres
(Gemini, Anthropic, OpenAI, OpenRouter) con la clave del propio usuario, y el escáner consulta
Open Food Facts (que sí es abierto). Es habitual y no impide publicarla. Si el revisor la pide, se
añade al YAML:

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Optional cooking assistant using Gemini, Anthropic, OpenAI or OpenRouter with the user's own API key.
```

## Nuevas versiones

Nada que hacer en F-Droid: al publicar una Release nueva (merge a `main` con un `versionName`
nuevo) el CI crea la etiqueta y F-Droid la detecta. Si una versión solo cambia `versionCode` sin
cambiar `versionName`, no hay etiqueta nueva y F-Droid no la publica.
