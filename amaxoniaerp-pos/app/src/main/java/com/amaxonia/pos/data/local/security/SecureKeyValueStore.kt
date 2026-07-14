package com.amaxonia.pos.data.local.security

/** Storage boundary for small secrets. Implementations must verify durable writes. */
interface SecureKeyValueStore {
    fun readString(key: String): String?

    fun writeString(
        key: String,
        value: String,
    )

    fun remove(key: String)
}

class SecureStorageException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Writes and reads back a secret before legacy plaintext may be removed. */
class VerifiedSecureValueWriter(
    private val store: SecureKeyValueStore,
) {
    fun write(
        key: String,
        value: String,
    ) {
        store.writeString(key, value)
        if (store.readString(key) != value) {
            throw SecureStorageException("Secure storage verification failed for key '$key'")
        }
    }
}
