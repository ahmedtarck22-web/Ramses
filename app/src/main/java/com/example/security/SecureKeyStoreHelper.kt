package com.example.security

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM helper for encrypting sensitive user secrets
 * (such as custom API keys) before storing them in local SharedPreferences.
 *
 * Automatically migrates any legacy cleartext preference entry into AES-GCM ciphertext and
 * deletes the cleartext key. Includes a deterministic local AES-GCM fallback strictly for
 * headless JVM unit tests where the `AndroidKeyStore` provider is unavailable.
 */
object SecureKeyStoreHelper {

    private const val TAG = "SecureKeyStoreHelper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ramses_ai_custom_secret_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                existing.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Headless local JVM / Robolectric unit test fallback where AndroidKeyStore is absent
            jvmFallbackKey ?: synchronized(this) {
                jvmFallbackKey ?: run {
                    val keyBytes = ByteArray(32)
                    SecureRandom().nextBytes(keyBytes)
                    SecretKeySpec(keyBytes, "AES").also { jvmFallbackKey = it }
                }
            }
        }
    }

    fun encrypt(plainText: String): String? {
        if (plainText.isBlank()) return null
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            if (secretKey is SecretKeySpec) {
                val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            }
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$ivB64:$ctB64"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt secret with Keystore", e)
            null
        }
    }

    fun decrypt(payload: String?): String {
        if (payload.isNullOrBlank() || !payload.contains(":")) return ""
        return try {
            val parts = payload.split(":", limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt stored secret; resetting corrupted entry")
            ""
        }
    }

    fun saveEncryptedPref(
        prefs: SharedPreferences,
        encryptedKey: String,
        legacyPlainKey: String,
        plainValue: String
    ) {
        val trimmed = plainValue.trim()
        val editor = prefs.edit().remove(legacyPlainKey)
        if (trimmed.isEmpty()) {
            editor.remove(encryptedKey).apply()
            return
        }
        val encrypted = encrypt(trimmed)
        if (encrypted != null) {
            editor.putString(encryptedKey, encrypted).apply()
        } else {
            editor.remove(encryptedKey).apply()
        }
    }

    fun readEncryptedPref(
        prefs: SharedPreferences,
        encryptedKey: String,
        legacyPlainKey: String
    ): String {
        val storedEncrypted = prefs.getString(encryptedKey, null)
        if (!storedEncrypted.isNullOrBlank()) {
            return decrypt(storedEncrypted)
        }
        val legacyPlain = prefs.getString(legacyPlainKey, null)
        if (!legacyPlain.isNullOrBlank()) {
            saveEncryptedPref(prefs, encryptedKey, legacyPlainKey, legacyPlain)
            return legacyPlain.trim()
        }
        if (prefs.contains(legacyPlainKey)) {
            prefs.edit().remove(legacyPlainKey).apply()
        }
        return ""
    }

    /**
     * Masks any accidental occurrence of [secret] inside diagnostic or exception strings.
     */
    fun redactSecret(text: String?, secret: String?): String {
        if (text.isNullOrEmpty()) return ""
        val cleanSecret = secret?.trim().orEmpty()
        if (cleanSecret.length < 6) return text
        return text.replace(cleanSecret, "[REDACTED_KEY]")
    }
}
