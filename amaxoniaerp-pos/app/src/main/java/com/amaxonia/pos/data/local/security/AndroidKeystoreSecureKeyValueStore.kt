package com.amaxonia.pos.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM storage whose non-exportable key is generated and retained by Android Keystore. */
class AndroidKeystoreSecureKeyValueStore(
    context: Context,
) : SecureKeyValueStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            SECURE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    override fun readString(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(encoded) }
            .getOrElse { throw SecureStorageException("Unable to decrypt secure value '$key'", it) }
    }

    @Synchronized
    override fun writeString(
        key: String,
        value: String,
    ) {
        val encoded =
            runCatching { encrypt(value) }
                .getOrElse { throw SecureStorageException("Unable to encrypt secure value '$key'", it) }
        if (!preferences.edit().putString(key, encoded).commit()) {
            throw SecureStorageException("Unable to persist secure value '$key'")
        }
    }

    @Synchronized
    override fun remove(key: String) {
        if (!preferences.edit().remove(key).commit()) {
            throw SecureStorageException("Unable to remove secure value '$key'")
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherText, Base64.NO_WRAP),
        ).joinToString(SEPARATOR)
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(SEPARATOR, limit = 3)
        if (parts.size != 3 || parts[0] != FORMAT_VERSION) {
            throw SecureStorageException("Unsupported secure value format")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val SECURE_PREFERENCES_NAME = "amaxonia_pos_secure"
        private const val KEY_ALIAS = "amaxonia_pos_secure_values_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val FORMAT_VERSION = "v1"
        private const val SEPARATOR = ":"
    }
}
