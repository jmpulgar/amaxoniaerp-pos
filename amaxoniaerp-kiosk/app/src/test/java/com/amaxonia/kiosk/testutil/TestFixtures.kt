package com.amaxonia.kiosk.testutil

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskSession
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.data.db.PendingPayment
import com.amaxonia.kiosk.data.db.PendingPaymentDao
import com.amaxonia.kiosk.domain.payment.PaymentResult
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay

class FakePendingPaymentDao : PendingPaymentDao {
    val payments = mutableMapOf<String, PendingPayment>()

    override suspend fun insert(payment: PendingPayment) {
        payments[payment.orderId] = payment
    }

    override suspend fun update(payment: PendingPayment) {
        payments[payment.orderId] = payment
    }

    override suspend fun getByStatus(status: String): List<PendingPayment> = payments.values.filter { it.status == status }

    override suspend fun getByOrderId(orderId: String): PendingPayment? = payments[orderId]

    override suspend fun delete(orderId: String) {
        payments.remove(orderId)
    }
}

/** Scriptable card terminal for tests (the real mock lives only in the dev flavor). */
class FakePaymentTerminal(
    var result: PaymentResult = approvedCard(),
    var delayMs: Long = 0,
    override val isAvailable: Boolean = true,
) : PaymentTerminal {
    var processCalls = 0
    var cancelCalls = 0
    var lastAmount: Money? = null

    override suspend fun processPayment(
        amount: Money,
        orderId: String,
    ): PaymentResult {
        processCalls++
        lastAmount = amount
        if (delayMs > 0) delay(delayMs)
        return result
    }

    override suspend fun cancelPayment(): Boolean {
        cancelCalls++
        return true
    }
}

fun approvedCard(amount: String = "9.10") =
    PaymentResult.Success(
        transactionId = "TXN-1",
        authCode = "AUT1",
        reference = "REF1",
        last4 = "4242",
        brand = "VISA",
        amount = Money.fromString(amount),
    )

fun testSession(token: String = "token-test") =
    KioskSession(
        token = token,
        userId = 7,
        username = "cajero1",
        companyId = 2,
        companyName = "Compañía Prueba",
        companyDb = "test_db",
        countryCode = "PA",
        serverUrl = "http://localhost:8080/",
    )

/** Storage of a kiosk that is logged in with caja "CAJA-1" and prefix K1. */
fun loggedInTokenStorage(): KioskTokenStorage =
    KioskTokenStorage().apply {
        saveSession(testSession())
        saveCaja(cajaId = "CAJA-1", cajaName = "Caja Kiosco", prefix = "K1")
    }

/**
 * Ktor client over a MockEngine that records requests. MockEngine runs on real IO threads, so code
 * that mixes HTTP with delays/timeouts is tested in real time (runBlocking), not virtual time.
 */
class RecordingApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val engine = MockEngine { request -> handler(request) }
    val storage = loggedInTokenStorage()
    val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)

    val requests: List<HttpRequestData>
        get() = engine.requestHistory

    fun requestsTo(
        method: String,
        pathSuffix: String,
    ): List<HttpRequestData> = requests.filter { it.method.value == method && it.url.encodedPath.endsWith(pathSuffix) }
}

suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData =
    respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

val sampleItem =
    KioskItemDto(
        id = 1,
        categoryId = 1,
        name = "Combo Hamburguesa",
        description = "Con papas y soda",
        price = "8.50",
        taxRate = "7.00",
        imageUrl = null,
        soldOut = false,
        modifierGroups = emptyList(),
    )

fun quoteJson(
    orderId: String = "ord-uuid-1234",
    total: String = "9.10",
    expiresAt: String = "2099-10-05T18:00:00Z",
) = """
    {
        "orderId": "$orderId",
        "formattedOrderNumber": "K1-042",
        "subtotal": "8.50",
        "tax": "0.60",
        "total": "$total",
        "expiresAt": "$expiresAt",
        "diningMode": "COMER_AQUI",
        "tableTent": null,
        "customerId": "CF",
        "lines": [
            {"line": 1, "itemId": 1, "name": "Combo Hamburguesa", "qty": 1, "unitPrice": "8.50",
             "subtotal": "8.50", "tax": "0.60", "total": "9.10", "modifiers": [{"id": 7, "name": "Extra queso", "extraPrice": "0.00"}]}
        ]
    }
    """.trimIndent()

val payResponseJson =
    """
    {
        "orderNumber": "K1-042",
        "status": "FISCAL_SUCCESS",
        "invoice": {"codFactura": "FAC-2026-001", "cufe": "CUFE123", "qr": "https://dgi.mef.gob.pa/fe/123"},
        "dispatch": "RETIRO_MOSTRADOR",
        "receipt": {
            "companyName": "Amaxonia Kiosk", "orderNumber": "K1-042", "diningMode": "COMER_AQUI",
            "customerName": "Consumidor Final", "customerId": "CF", "date": "2026-10-05 16:30:00", "lines": [],
            "subtotal": "8.50", "tax": "0.60", "total": "9.10", "paymentBrand": "VISA", "paymentLast4": "4242",
            "paymentAuthCode": "AUT123456", "paymentReference": "REF12345"
        }
    }
    """.trimIndent()

fun yappyChargeJson(
    amount: String = "9.10",
    expiresInSec: Int = 180,
) = """{"transactionId": "YP-1", "qrHash": "yappy-qr-hash-123", "amount": "$amount", "expiresInSec": $expiresInSec}"""

fun yappyStatusJson(status: String) = """{"transactionId": "YP-1", "status": "$status"}"""
