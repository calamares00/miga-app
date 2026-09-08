package com.bmo00.miga.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "miga_sync_token_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH_BITS = 128

/**
 * Cifra el [SyncConnectionEntity.accessToken] en reposo. La base de datos entera se incluye hoy en
 * la copia de seguridad automática de Android y en la transferencia entre dispositivos (ver
 * backup_rules.xml/data_extraction_rules.xml, correcto para recetas/fotos), así que sin esto el
 * token viajaría en claro dentro de esa copia. La clave AES vive en el Keystore del dispositivo
 * (no exportable, no viaja en la copia de seguridad ni se restaura en otro dispositivo), así que el
 * blob cifrado queda inservible fuera del dispositivo que lo creó.
 */
object TokenCipher {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Devuelve un único String Base64 con el IV (12 bytes, generado por el propio Cipher) seguido
     *  del texto cifrado, ya que Room solo tiene una columna TEXT para este campo. */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + cipherText)
    }

    /** Null (nunca excepción) ante cualquier fallo: clave ausente, formato inesperado o dato
     *  corrupto. Se usa deliberadamente como señal de "esto no es un token cifrado por
     *  [encrypt]" para poder hacer fallback a texto plano en filas anteriores a este cambio. */
    fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.getDecoder().decode(encoded)
        if (combined.size <= GCM_IV_LENGTH) return null
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()
}
