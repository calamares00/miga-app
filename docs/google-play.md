# Publicación en Google Play

Guía para rellenar la Play Console con la información que corresponde a Miga. Mantenerla al día si
cambian las funciones de red, los permisos o la IA (y actualizar a la vez `PRIVACY.md`).

## Ficha de la tienda

- Título, descripción corta y larga: `fastlane/metadata/android/es-ES/` (corta ≤ 80 caracteres).
- Correo de contacto: miga@calamares.org. Sitio web: https://miga.calamares.org
- Política de privacidad: **https://miga.calamares.org/privacy/** (en inglés,
  https://miga.calamares.org/en/privacy/), publicada desde el repositorio `bmo00/web-miga`. La app
  la muestra además en Ajustes → Acerca de (`app/src/main/assets/docs/privacy-es.md`, mismo texto
  que `PRIVACY.md`). Si cambia, copiarla también a `content/` de web-miga y regenerar la web.
- Catálogo de packs: https://miga.calamares.org/packs/catalog.json (las URLs de portada y ZIP pueden ser relativas a él).
- ID de la aplicación: `org.calamares.miga` (permanente una vez publicada).
- Categoría: Comida y bebida. Etiquetas sugeridas: recetas, lista de la compra.
- Recursos gráficos: icono 512×512 y gráfico destacado 1024×500 en `fastlane/metadata/android/*/images/`.
  **Pendiente**: 4-8 capturas de teléfono por idioma (`images/phoneScreenshots/`, mínimo 2).
- No usar marcas de terceros en la ficha (salvo la atribución "Datos de Open Food Facts").

## Seguridad de los datos (Data safety)

Respuestas recomendadas:

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte datos de usuario? | **Sí** (solo por acciones del usuario, ver abajo). |
| ¿Los datos se cifran en tránsito? | **Sí** para IA, Open Food Facts y el catálogo de packs (HTTPS). El servidor propio puede ser `http://` en red local, elegido por el usuario; la app avisa si es público sin cifrar. |
| ¿El usuario puede pedir que se borren? | **Sí**: todo se borra desde la app o desinstalándola; no hay datos en servidores del desarrollador. |
| Cuentas | La app no tiene cuentas (no aplica el requisito de borrado de cuenta). |

Tipos de datos (marcar como **compartidos**, **no recopilados por el desarrollador**, **opcionales** y
**a petición del usuario**):

- **Fotos** (Fotos y vídeos): se envían al proveedor de IA elegido solo al importar una receta desde
  una foto o al pedir que mejore una foto. Finalidad: funcionalidad de la app.
- **Otro contenido generado por el usuario** (recetas, ingredientes, texto de páginas web, texto
  dictado, nombres de categorías, etiquetas y equipamiento propios): se envía al proveedor de IA
  elegido al usar funciones de IA (importar, mejorar recetas, limpiar contenido, ideas...), y al
  servidor propio del usuario si configura la sincronización. Finalidad: funcionalidad de la app.
- **Grabaciones de audio**: no. El audio lo procesa el reconocimiento de voz de Android; la app solo
  recibe texto.
- **Diagnóstico / fallos**: no. El informe de fallos se queda en el dispositivo salvo que el usuario
  lo comparta manualmente.
- **Identificadores, ubicación, contactos, datos financieros, salud**: no.

Nota: los envíos a Gemini/Anthropic/OpenAI/OpenRouter usan la clave de API del propio usuario; Google Play los
considera "compartir" igualmente, por eso se declaran.

## Contenido generado con IA

- Declarar en "Contenido de la app" que la app genera contenido con IA.
- La app marca ese contenido con "Generado con IA" y permite reportarlo (botón **Reportar** en el
  editor tras importar con IA, valoración de salud, nutrición, sustituciones, buscador de platos e
  importación múltiple). Los reportes llegan por correo a miga@calamares.org
  (`AiContentReport.SUPPORT_EMAIL`).

## Otros cuestionarios

- **Clasificación de contenido (IARC)**: app de referencia/utilidad, sin violencia, sexo, apuestas
  ni compras. Interacción entre usuarios: no (la sincronización es con un servidor privado propio).
- **Público objetivo**: 13+ (no dirigida a menores; evita las normas de la política de familias).
- **Anuncios**: no contiene anuncios.
- **Acceso a la app**: no requiere inicio de sesión. Marcar "Toda la funcionalidad está disponible sin
  acceso especial" y añadir en las instrucciones (en inglés):

  > Miga has no accounts. Recipes, books, shopping lists, cooking mode, PDF export and the barcode
  > scanner work without any setup. Optional AI features (Settings › AI) use the user's own API key
  > for Google Gemini, Anthropic, OpenAI or OpenRouter; without a key they are hidden or ask for one.
  > Optional sync (Settings › Sync server) connects to a server the user hosts (miga-server).

  Si se quiere que el revisor pruebe la sincronización, añadir la URL, el namespace y un token de un
  servidor de demostración.
- **Servicio en primer plano** (Contenido de la app › Permisos de servicios en primer plano): la app
  declara `FOREGROUND_SERVICE_DATA_SYNC` para `AiWorkService`. Tipo: *Sincronización de datos* /
  transferencia de red iniciada por el usuario. Descripción sugerida: "While an AI task the user
  started (importing recipes from photos, improving recipes) is running, a notification keeps the
  network request alive if the user leaves the app; it stops as soon as the task ends or is
  cancelled." Hace falta un vídeo corto: lanzar "Mejorar varias recetas", salir de la app y mostrar
  la notificación hasta que termina.
- **Declaración de apps de salud**: la app no es una app médica, pero muestra una valoración de salud
  y una estimación nutricional generadas con IA (y la puntuación de productos de Open Food Facts).
  Marcar solo la categoría de nutrición/alimentación. Ambas llevan el aviso "Es orientativo y no
  sustituye el consejo de un médico o dietista-nutricionista" y se pueden desactivar en Ajustes ›
  Inteligencia artificial.
- **Permisos sensibles**: micrófono (dictado) y cámara (escáner de códigos), ambos en primer plano y
  bajo petición.
- **Pagos**: la app no contiene donaciones ni pagos externos (no está permitido fuera de Google Play
  Billing salvo para organizaciones benéficas verificadas).

## Requisitos técnicos

- `targetSdk` 36 y `compileSdk` 36 (Android 16).
- La app no se actualiza por su cuenta: las actualizaciones llegan solo por Google Play.
- Sin bibliotecas nativas propias (no aplica el requisito de páginas de 16 KB).
- Subir el AAB (`bundleRelease`) con Play App Signing; guardar copia segura de la clave de subida.
- Firma: el CI firma el AAB y los APK de release si existen los secretos de GitHub
  `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`.
  Crear la clave de subida una sola vez:

  ```bash
  keytool -genkeypair -v -keystore miga-upload.jks -alias miga-upload \
    -keyalg RSA -keysize 4096 -validity 10000
  base64 -w0 miga-upload.jks   # valor de RELEASE_KEYSTORE_BASE64
  ```
- Los APK de GitHub (`beta-latest`) van firmados con la clave debug: quien los tenga instalados no
  puede actualizar a la versión de Play sin desinstalar. Avisar a los testers de que hagan antes una
  copia de seguridad desde Ajustes.

## Pruebas antes de producción

1. **Prueba interna** (hasta 100 testers) con el AAB firmado. Revisar el informe previo al
   lanzamiento (fallos, bloqueos, accesibilidad, capturas en varios dispositivos) y Android vitals
   (fallos y ANR).
2. **Prueba cerrada**: ver el apartado siguiente.
3. **Producción**: lanzamiento escalonado (por ejemplo, 20 % → 100 %) vigilando fallos y ANR.

## Cuenta de desarrollador nueva (personal)

Antes de pedir acceso a producción hay que hacer una prueba cerrada con al menos **12 testers**
que mantengan la app instalada **14 días seguidos**.
