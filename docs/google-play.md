# Publicación en Google Play

Guía para rellenar la Play Console con la información que corresponde a Miga. Mantenerla al día si
cambian las funciones de red, los permisos o la IA (y actualizar a la vez `PRIVACY.md`).

## Ficha de la tienda

- Título, descripción corta y larga: `fastlane/metadata/android/es-ES/` (corta ≤ 80 caracteres).
- Correo de contacto: miga@calamares.org. Sitio web: https://miga.calamares.org
- Política de privacidad: la app la muestra en Ajustes → Acerca de (`app/src/main/assets/docs/privacy-es.md`,
  mismo texto que `PRIVACY.md`). Play exige además una URL pública: falta publicar ese texto en una página
  y poner aquí su dirección.
- Catálogo de packs: https://miga.calamares.org/packs/catalog.json (las URLs de portada y ZIP pueden ser relativas a él).
- ID de la aplicación: `org.calamares.miga` (permanente una vez publicada).
- Categoría: Comida y bebida. Etiquetas sugeridas: recetas, lista de la compra.
- Recursos gráficos pendientes: icono 512×512, gráfico destacado 1024×500, 4-8 capturas de teléfono.
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
  una foto. Finalidad: funcionalidad de la app.
- **Otro contenido generado por el usuario** (recetas, ingredientes, texto de páginas web, texto
  dictado): se envía al proveedor de IA elegido al usar funciones de IA, y al servidor propio del
  usuario si configura la sincronización. Finalidad: funcionalidad de la app.
- **Grabaciones de audio**: no. El audio lo procesa el reconocimiento de voz de Android; la app solo
  recibe texto.
- **Diagnóstico / fallos**: no. El informe de fallos se queda en el dispositivo salvo que el usuario
  lo comparta manualmente.
- **Identificadores, ubicación, contactos, datos financieros, salud**: no.

Nota: los envíos a Gemini/Anthropic/OpenRouter usan la clave de API del propio usuario; Google Play los
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
- **Acceso a la app**: no requiere inicio de sesión. Las funciones de IA necesitan una clave propia;
  indicarlo en las instrucciones para revisores.
- **Permisos sensibles**: micrófono (dictado) y cámara (escáner de códigos), ambos en primer plano y
  bajo petición.
- **Pagos**: la app no contiene donaciones ni pagos externos (no está permitido fuera de Google Play
  Billing salvo para organizaciones benéficas verificadas).

## Requisitos técnicos

- `targetSdk` 36 y `compileSdk` 36 (Android 16).
- La app no se actualiza por su cuenta: las actualizaciones llegan solo por Google Play.
- Sin bibliotecas nativas propias (no aplica el requisito de páginas de 16 KB).
- Subir el AAB (`bundleRelease`) con Play App Signing; guardar copia segura de la clave de subida.

## Cuenta de desarrollador nueva (personal)

Antes de pedir acceso a producción hay que hacer una prueba cerrada con al menos **12 testers**
que mantengan la app instalada **14 días seguidos**.
