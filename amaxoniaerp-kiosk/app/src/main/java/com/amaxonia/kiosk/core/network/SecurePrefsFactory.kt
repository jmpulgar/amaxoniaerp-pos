package com.amaxonia.kiosk.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/** Creates the secure preferences store and can wipe it (file + key) when it becomes unreadable. */
interface SecurePrefsFactory {
    fun create(): SharedPreferences

    fun destroy()
}

/**
 * AES-256 EncryptedSharedPreferences backed by the Android Keystore master key. On first open it
 * migrates credentials from the legacy plain-text file used by earlier builds and deletes it.
 */
@Suppress("DEPRECATION") // EncryptedSharedPreferences is deprecated upstream but remains the supported API 28 option.
class EncryptedPrefsFactory(
    private val context: Context,
) : SecurePrefsFactory {
    override fun create(): SharedPreferences {
        val masterKey =
            MasterKey
                .Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        val prefs =
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        migrateLegacy(prefs)
        return prefs
    }

    override fun destroy() {
        context.getSharedPreferences(ENCRYPTED_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences(ENCRYPTED_FILE)
        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                load(null)
                deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Could not delete master key alias: ${e.javaClass.simpleName}")
        } catch (e: IOException) {
            Log.w(TAG, "Could not load keystore to delete master key: ${e.javaClass.simpleName}")
        }
    }

    private fun migrateLegacy(target: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PLAIN_FILE, Context.MODE_PRIVATE)
        val entries = legacy.all.filterValues { it is String }
        if (entries.isEmpty()) return
        val editor = target.edit()
        entries.forEach { (key, value) -> editor.putString(key, value as String) }
        if (editor.commit()) {
            legacy.edit().clear().commit()
            context.deleteSharedPreferences(LEGACY_PLAIN_FILE)
        }
    }

    private companion object {
        const val TAG = "EncryptedPrefsFactory"
        const val ENCRYPTED_FILE = "kiosk_secure_prefs_encrypted"
        const val LEGACY_PLAIN_FILE = "kiosk_secure_prefs"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
