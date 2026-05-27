package com.example.privatecalendar.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private var cachedKey: SecretKey? = null

    private fun getKey(): SecretKey {
        cachedKey?.let { return it }
        val existingKey = keyStore.getEntry("private_calendar_key", null) as? KeyStore.SecretKeyEntry
        val key = existingKey?.secretKey ?: createKey()
        cachedKey = key
        return key
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder("private_calendar_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false) // Simplificado para que funcione sin biometría siempre activa
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private val encryptCipher get() = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getKey())
    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        }
    }

    fun encrypt(text: String): String {
        if (text.isEmpty()) return ""
        val cipher = encryptCipher
        val bytes = cipher.doFinal(text.toByteArray())
        val combined = cipher.iv + bytes
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
            if (combined.size < 12) return encryptedBase64
            val iv = combined.copyOfRange(0, 12)
            val bytes = combined.copyOfRange(12, combined.size)
            val cipher = getDecryptCipherForIv(iv)
            String(cipher.doFinal(bytes))
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Decryption failed for: $encryptedBase64", e)
            // Si parece base64 pero falla el descifrado, devolvemos el original por si es texto plano legacy
            // o un indicador de error si es claramente corrupto.
            encryptedBase64
        }
    }
}
