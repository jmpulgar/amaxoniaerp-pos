package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.payment.GatewayApproval
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.GatewayPaymentRequest
import com.amaxonia.pos.domain.repository.PaymentGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteGatewayPaymentUseCaseTest {
    @Test
    fun `launches each required gateway payment and awaits approval`() =
        runTest {
            val gateway = FakeGateway(requiredIndexes = setOf(0, 2))
            val useCase = ExecuteGatewayPaymentUseCase(gateway)
            val launched = mutableListOf<String>()

            val result =
                useCase(
                    request = requestWithThreeMethods(),
                    launch = { launched += it.packageName },
                )

            assertTrue(result.isSuccess)
            assertEquals(listOf("gateway-0", "gateway-2"), launched)
            assertEquals(2, gateway.approvalRequests)
        }

    @Test
    fun `stops after a rejected payment without launching later methods`() =
        runTest {
            val gateway = FakeGateway(requiredIndexes = setOf(0, 1, 2), rejectApprovalNumber = 2)
            val useCase = ExecuteGatewayPaymentUseCase(gateway)
            val launched = mutableListOf<String>()

            val result = useCase(requestWithThreeMethods()) { launched += it.packageName }

            assertTrue(result.isFailure)
            assertEquals(listOf("gateway-0", "gateway-1"), launched)
            assertEquals(2, gateway.approvalRequests)
        }

    private fun requestWithThreeMethods() =
        GatewayPaymentRequest(
            methods =
                List(3) { index ->
                    TransactionPaymentMethod(description = "method-$index", amount = index + 1.0)
                },
            customerIdentifier = "customer",
            exchangeRate = 1.0,
            isMultiCurrency = false,
        )

    private class FakeGateway(
        private val requiredIndexes: Set<Int>,
        private val rejectApprovalNumber: Int? = null,
    ) : PaymentGateway {
        var approvalRequests: Int = 0

        override suspend fun validateConfiguration(methods: List<TransactionPaymentMethod>): Result<Unit> = Result.success(Unit)

        override suspend fun prepare(
            method: TransactionPaymentMethod,
            customerIdentifier: String,
            exchangeRate: Double,
            isMultiCurrency: Boolean,
        ): Result<GatewayLaunchPayload?> {
            val index = method.description.substringAfterLast('-').toInt()
            return Result.success(
                if (index in requiredIndexes) {
                    GatewayLaunchPayload(
                        packageName = "gateway-$index",
                        activityClassName = "Activity",
                        encryptedCommand = byteArrayOf(index.toByte()),
                        backgroundColor = "background",
                        textColor = "text",
                        message = "message",
                    )
                } else {
                    null
                },
            )
        }

        override suspend fun awaitApproval(): GatewayApproval {
            approvalRequests += 1
            val approved = approvalRequests != rejectApprovalNumber
            return GatewayApproval(approved = approved, message = if (approved) "ok" else "rejected")
        }
    }
}
