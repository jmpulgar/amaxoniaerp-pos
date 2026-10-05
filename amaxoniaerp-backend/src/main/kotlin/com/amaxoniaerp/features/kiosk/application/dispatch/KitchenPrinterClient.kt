package com.amaxoniaerp.features.kiosk.application.dispatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

interface KitchenPrinterClient {
    suspend fun sendBytes(ip: String, port: Int, bytes: ByteArray): Result<Unit>
}

class TcpKitchenPrinterClient(
    private val connectTimeoutMs: Int = 3000,
) : KitchenPrinterClient {
    override suspend fun sendBytes(ip: String, port: Int, bytes: ByteArray): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                socket.getOutputStream().use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }
}
