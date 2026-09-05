# Add project specific ProGuard rules here.

# Trazas de pila legibles a partir de logcat/reportes copiados por el usuario (la app ya tiene un
# diálogo para copiar el error completo de la IA): se mantienen los números de línea, pero se
# oculta el nombre real del fichero fuente.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: los serializadores se generan en tiempo de compilación (sin reflexión),
# pero R8 puede eliminar por "no usados" el companion/objeto que expone serializer() si nada más
# lo referencia directamente. Reglas oficiales del proyecto:
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}
