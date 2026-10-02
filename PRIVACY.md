# Política de privacidad de Miga

Última actualización: ver el historial de este archivo en GitHub.

Miga es una app de recetario familiar. Este documento explica, con la mayor
concreción posible, qué datos maneja la app y qué se hace (o no se hace) con
ellos.

## Resumen

- **Todos tus datos viven solo en tu dispositivo.** Miga no tiene cuentas de
  usuario, no tiene servidor propio y no sincroniza nada entre dispositivos, salvo que
  tú configures un servidor de sincronización que alojes tú mismo.
- **No hay analítica, publicidad ni rastreo de ningún tipo.** La app no
  incluye ningún SDK de terceros para medir el uso, mostrar anuncios o
  identificarte.
- Las únicas conexiones a internet que hace la app son las que se describen
  en la sección "Conexiones de red" — todas opcionales o de solo lectura.

## Qué datos guarda la app y dónde

Tus recetas, libros, categorías, etiquetas, utensilios y fotos se guardan
**únicamente en el almacenamiento interno de tu dispositivo** (una base de
datos local y los archivos de fotos que añades). Nada de esto sale de tu
teléfono salvo que tú, explícitamente, lo exportes o lo compartas (ver
"Exportar y compartir").

Si activas la copia de seguridad automática de Android (`allowBackup`), el
propio sistema operativo puede incluir estos datos en su copia de seguridad
gestionada por tu cuenta de Google, igual que con cualquier otra app; Miga no
interviene en ese proceso ni tiene acceso a esa copia.

## Permisos que usa la app

- **Internet**: solo para las conexiones descritas más abajo, y solo cuando
  las usas (comprobar actualizaciones, explorar el catálogo de packs, o usar
  las funciones de IA si las has configurado).
- **Cámara**: al añadir una foto a una receta, la app delega en la propia app
  de cámara del sistema (un `Intent` estándar de Android) y solo recibe el
  archivo de imagen resultante. El único uso directo de la cámara es el
  escáner de códigos QR de la lista de la compra (Lista → menú → Escanear QR),
  que solo se abre cuando lo pides; la imagen se procesa en el dispositivo
  para leer el código y no se guarda ni se envía a ningún sitio.
- **Biometría** (huella, rostro, PIN): si activas el bloqueo biométrico en
  Ajustes, la verificación la gestiona directamente el sistema operativo
  (`BiometricPrompt`). Miga nunca ve, recibe ni almacena tu huella ni ningún
  otro dato biométrico; solo recibe un "sí" o un "no" de Android.

## Conexiones de red

Todas son bajo demanda; ninguna ocurre en segundo plano sin que la acción
correspondiente esté activada.

1. **Comprobar actualizaciones** (Ajustes → Actualizaciones, activado por
   defecto pero desactivable): consulta la API pública de GitHub
   (`api.github.com`) para ver si hay una versión nueva de la app. No se
   envía ningún dato personal, solo una petición HTTP estándar.
2. **Catálogo de packs de recetas** (Ajustes → Packs de recetas): si abres
   el catálogo, la app descarga un listado público (`catalog.json`) y, si
   decides instalar un pack, su archivo ZIP, desde el repositorio de GitHub
   que tengas configurado (`raw.githubusercontent.com`). No requiere cuenta
   ni envía datos tuyos: es una descarga de contenido público.
3. **Importar receta con foto / valoración de salud con IA** (Ajustes →
   Importar con IA, desactivado hasta que introduces tu propia clave):
   ambas funciones son opcionales y usan tu propia clave de API de Google
   Gemini (BYOK, *bring your own key*). Si las usas, la foto o el texto de
   ingredientes/pasos de esa receta se envía a la API de Google Gemini para
   su análisis, sujeto a las condiciones de Google. Miga no guarda una copia
   de lo enviado más allá de lo que tú decidas conservar en la propia
   receta (la foto que añades, o el resultado de la valoración de salud).
   Sin una clave configurada, no se envía nada a Google.
4. **Servidor de sincronización propio** (Ajustes → Servidor de sincronización,
   desactivado hasta que añades una conexión): si configuras la URL, el
   namespace y el token de un servidor que tú mismo alojas (miga-server),
   los libros y recetas que vincules y, si lo activas, la lista de la compra
   se envían solo a ese servidor. Las invitaciones por QR contienen la
   dirección del servidor, el namespace y un token de acceso nuevo; quien
   escanee el código puede leer y modificar ese namespace.

5. **Escáner de productos de la lista de la compra** (Lista de la compra →
   menú → Escanear producto): solo cuando escaneas un código de barras, se
   envía ese código a Open Food Facts (base de datos abierta, sin cuenta) para
   obtener el nombre, la foto y la ficha del producto (Nutri-Score, alérgenos,
   nutrición, ingredientes). La ficha se guarda en tu dispositivo y, si
   compartes la lista, en tu propio servidor. Las fotos de producto solo se
   descargan si activas "Mostrar fotos de productos" (desactivado por
   defecto) o cuando abres la ficha de un producto, y entonces se piden a los
   servidores de Open Food Facts. También puedes buscar productos por nombre
   (Lista de la compra → Buscar en Open Food Facts): el texto que escribes se
   envía a Open Food Facts y se descargan las miniaturas de los resultados.

El dictado por voz usa el reconocimiento de voz de Android en el idioma que
elijas en Ajustes; Miga no recibe ni guarda el audio.

Miga también puede recibir texto que compartas desde otras apps hacia la
lista de la compra; ese texto se queda en tu dispositivo.

Ninguna de estas conexiones pasa por un servidor propio de Miga: no existe
tal servidor.

## Informe de fallos

Si la app se cierra de forma inesperada, se guarda un informe de texto
(versión de la app, modelo del dispositivo, versión de Android y la traza
del error) **solo en el almacenamiento interno de tu dispositivo**. Al
volver a abrir la app, se te ofrece verlo, copiarlo o compartirlo tú
mismo (por ejemplo, adjuntándolo a un email o un issue de GitHub) si
quieres reportarlo, o simplemente descartarlo. No se usa ningún servicio
de terceros (tipo Crashlytics o Sentry) ni se envía nada de forma
automática: la app no se entera de que has tenido un fallo salvo que tú
decidas contárselo a alguien.

## Exportar y compartir

Las funciones de exportar (JSON, ZIP, PDF), compartir una receta o hacer una
copia de seguridad completa usan el selector de compartir estándar de
Android (`Intent.ACTION_SEND` / creación de documentos). Eres tú quien elige
el destino final (otra app, un contacto, guardarlo en tu almacenamiento...);
Miga no envía esos archivos a ningún sitio por sí sola.

## Menores de edad

Miga no está dirigida a menores ni recopila datos que permitan identificar
la edad de quien la usa. Al no haber cuentas ni recogida de datos personales,
no hay un tratamiento diferenciado para menores más allá de lo anterior.

## Cambios en esta política

Cualquier cambio se reflejará en este mismo archivo, versionado junto con el
código de la app; el historial de commits de este archivo en el repositorio
sirve como registro de cambios.

## Contacto

Para preguntas sobre esta política, abre un issue en el repositorio de
GitHub del proyecto.
