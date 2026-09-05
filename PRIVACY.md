# Política de privacidad de Miga

Última actualización: ver el historial de este archivo en GitHub.

Miga es una app de recetario familiar. Este documento explica, con la mayor
concreción posible, qué datos maneja la app y qué se hace (o no se hace) con
ellos.

## Resumen

- **Todos tus datos viven solo en tu dispositivo.** Miga no tiene cuentas de
  usuario, no tiene servidor propio y no sincroniza nada entre dispositivos.
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
- **Cámara**: la app no declara el permiso de cámara. Al añadir una foto,
  delega en la propia app de cámara del sistema (un `Intent` estándar de
  Android) y solo recibe el archivo de imagen resultante.
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

Ninguna de estas conexiones pasa por un servidor propio de Miga: no existe
tal servidor.

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
