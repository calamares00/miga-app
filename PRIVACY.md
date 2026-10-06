# Política de privacidad de Miga

Última actualización: 5 de octubre de 2026.

Miga es una app de recetario familiar y lista de la compra. Este documento explica qué datos
maneja la app, adónde van y qué control tienes sobre ellos.

## Resumen

- **Tus datos viven en tu dispositivo.** Miga no tiene cuentas de usuario ni un servidor propio
  del desarrollador.
- **Sin analítica, publicidad ni rastreo.** La app no incluye SDKs de terceros para medir el uso,
  mostrar anuncios o identificarte.
- **Las conexiones a internet son opcionales.** Ocurren solo cuando usas una función que las
  necesita (las detallamos más abajo): IA con tu propia clave, Open Food Facts, el catálogo de
  packs o un servidor de sincronización que alojas tú.
- **No vendemos ni compartimos datos** con fines comerciales.

## Qué datos guarda la app y dónde

Recetas, libros, fotos, categorías, etiquetas, utensilios, valoraciones, listas de la compra,
plantillas, supermercados y ajustes se guardan **en el almacenamiento interno de tu dispositivo**.

Las claves de API de Google Gemini, Anthropic u OpenRouter que introduzcas se guardan solo en tu dispositivo y
se usan únicamente para llamar al proveedor que elijas.

Si tienes activada la copia de seguridad de Android, el sistema puede incluir estos datos en la
copia gestionada por tu cuenta de Google, como con cualquier otra app. Miga no tiene acceso a esa
copia.

## Permisos

- **Internet**: solo para las conexiones de la sección siguiente.
- **Micrófono**: para dictar pasos de receta y artículos de la lista. El reconocimiento lo hace el
  servicio de voz de Android en el idioma que elijas; Miga recibe el texto, no el audio, y no lo
  guarda.
- **Cámara**: para escanear códigos de barras de productos y códigos QR de listas o invitaciones.
  La imagen se procesa en el dispositivo y no se guarda ni se envía. Las fotos de recetas se hacen
  con la app de cámara del sistema; Miga solo recibe el archivo resultante.
- **Biometría**: si activas el bloqueo, la verificación la hace Android (`BiometricPrompt`). Miga
  solo recibe un "sí" o un "no"; nunca ve tu huella ni tu rostro.

## Conexiones de red y con quién se comparten datos

Todas se inician por una acción tuya o por una función que has activado.

1. **Funciones de IA (opcionales, con tu propia clave)**: importar recetas desde fotos o desde una
   página web, buscar y generar recetas de un plato, valoración de salud, estimación nutricional,
   sustitución de ingredientes y limpieza del texto dictado. Al usarlas, se envía a los proveedores
   que configures (**Google Gemini**, **Anthropic Claude** u **OpenRouter**, que a su vez lo reenvía
   al proveedor del modelo que elijas) la foto, el texto de la página, el nombre del plato o los
   ingredientes y pasos de la receta, según la función. Se usan en el orden de prioridad que
   elijas: si uno falla o no puede leer imágenes, se envía la misma petición al siguiente. El
   tratamiento de esos datos está sujeto a las condiciones de cada proveedor asociadas a tu clave
   (algunos modelos gratuitos de OpenRouter pueden usar las peticiones para entrenar). Sin clave
   configurada no se envía nada. La lista de modelos de OpenRouter se descarga de su catálogo
   público, sin enviar datos tuyos. Puedes desactivar todas las funciones de IA (o solo la valoración de salud o la
   estimación nutricional automáticas) en Ajustes → Inteligencia artificial. El contenido generado
   con IA se marca en la app, puede contener errores y se puede reportar.
2. **Open Food Facts**: al escanear un código de barras o buscar un producto por nombre se envía
   ese código o ese texto a Open Food Facts (base de datos abierta, sin cuenta) para obtener el
   nombre, la foto y la ficha del producto. Las fotos de producto se descargan de sus servidores.
   Datos de Open Food Facts bajo licencia ODbL.
3. **Catálogo de packs de recetas**: al abrir el catálogo o instalar un pack se descargan un
   listado público y el archivo del pack desde `miga.calamares.org` (o desde el catálogo alternativo
   que configures). No se envían datos tuyos.
4. **Servidor de sincronización propio (opcional)**: si añades una conexión a un servidor que
   alojas tú (miga-server), los libros, recetas y fotos que vincules y, si lo activas, la lista de
   la compra (con el nombre que pongas como autor) se envían solo a ese servidor. La app sincroniza
   al abrirse, periódicamente en segundo plano y cada pocos segundos mientras ves una lista
   compartida. Las invitaciones por QR contienen la dirección del servidor, el espacio compartido y
   un token de acceso: quien escanee el código puede leer y modificar ese espacio. Si el servidor
   usa `http://` fuera de tu red local, la app te avisa de que la conexión no está cifrada.
5. **Importar desde una URL**: la app descarga la página que indiques para extraer la receta (y, si
   usas IA, envía su texto al proveedor elegido, ver punto 1).
6. **Reportes y contacto**: si reportas un contenido generado con IA o informas de un problema, la
   app abre tu correo con el mensaje para miga@calamares.org ya escrito; tú decides si lo envías.

Ninguna de estas conexiones pasa por un servidor del desarrollador de Miga: no existe tal servidor.

## Informe de fallos

Si la app se cierra de forma inesperada, se guarda un informe de texto (versión de la app, modelo
del dispositivo, versión de Android y traza del error) **solo en tu dispositivo**. Al volver a
abrirla puedes verlo, compartirlo tú mismo o descartarlo. No se envía nada automáticamente ni se
usan servicios como Crashlytics o Sentry.

## Exportar y compartir

Exportar (JSON, ZIP, PDF), compartir recetas o listas y las copias de seguridad usan el selector
estándar de Android. Tú eliges el destino; Miga no envía esos archivos por su cuenta.

## Conservación y borrado

Los datos se conservan mientras tengas la app instalada. Puedes borrar recetas, libros, listas y
plantillas desde la app, quitar tus claves de IA en Ajustes, eliminar conexiones de sincronización
(los datos que ya estén en tu servidor dependen de ti como administrador) o borrarlo todo
desinstalando la app o limpiando sus datos desde los ajustes de Android.

## Menores de edad

Miga no está dirigida a menores de 13 años y no recopila datos que permitan conocer la edad de
quien la usa.

## Cambios en esta política

Los cambios se publican en este mismo documento, junto con su fecha de actualización. El historial
del archivo en el repositorio sirve como registro.

## Contacto

Para preguntas sobre privacidad escribe a **miga@calamares.org** (también desde "Ayuda y soporte →
Informar de un problema" en la app).
