package com.amaxonia.pos.data.repository

import android.content.Context
import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.data.remote.NetworkMonitor
import java.net.ServerSocket

/** Almacen seguro en memoria para tests de LocalStore y repos offline-first. */
internal class FakeSecureKeyValueStore(
    private val failReads: Boolean = false,
) : SecureKeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun readString(key: String): String? {
        if (failReads) error("keystore no disponible")
        return values[key]
    }

    override fun writeString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

/**
 * NetworkMonitor con conectividad fija; el resto del comportamiento queda
 * intacto. Necesario porque el android.jar mockable de los tests unitarios no
 * expone NetworkCapabilities.Builder para forzar el estado via shadow.
 */
internal class SettableNetworkMonitor(
    context: Context,
    private val online: Boolean,
) : NetworkMonitor(context) {
    override fun isOnline(): Boolean = online
}

/**
 * URL a un puerto local reservado y liberado, de modo que toda peticion HTTP
 * falle de inmediato (conexion rechazada) sin depender de la red real.
 */
internal fun closedPortBaseUrl(): String {
    val port = ServerSocket(0).use { it.localPort }
    return "http://127.0.0.1:$port/"
}

internal fun testCompanySession(token: String = "token-1"): CompanySessionSnapshot =
    CompanySessionSnapshot(
        token = token,
        company =
            CompanyDetailsSnapshot(
                id = 7,
                name = "Empresa Test",
                adminDb = "db_admin",
                accountingDb = "db_contable",
                payrollDb = "db_nomina",
            ),
    )
