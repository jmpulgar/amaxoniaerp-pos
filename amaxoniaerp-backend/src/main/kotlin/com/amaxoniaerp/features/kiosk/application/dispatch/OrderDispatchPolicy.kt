package com.amaxoniaerp.features.kiosk.application.dispatch

import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import org.slf4j.LoggerFactory

interface OrderDispatchPolicy {
    val destination: KioskDispatchDestination

    suspend fun dispatch(
        order: KioskOrderRecord,
        config: KioskDispatchConfig = KioskDispatchConfig(),
        itemNames: Map<Int, String> = emptyMap(),
    ): DispatchResult
}

class CounterPickupDispatchPolicy : OrderDispatchPolicy {
    override val destination: KioskDispatchDestination = KioskDispatchDestination.RETIRO_MOSTRADOR

    override suspend fun dispatch(
        order: KioskOrderRecord,
        config: KioskDispatchConfig,
        itemNames: Map<Int, String>,
    ): DispatchResult =
        DispatchResult.CounterPickup(
            orderNumber = order.codigoPedido,
            message = "Pedido asignado para retiro en mostrador: ${order.codigoPedido}",
        )
}

class TableDeliveryDispatchPolicy : OrderDispatchPolicy {
    override val destination: KioskDispatchDestination = KioskDispatchDestination.MESAS

    override suspend fun dispatch(
        order: KioskOrderRecord,
        config: KioskDispatchConfig,
        itemNames: Map<Int, String>,
    ): DispatchResult {
        val tableTent = order.portamesa?.trim()
        if (tableTent.isNullOrBlank()) {
            return DispatchResult.Failure(
                orderNumber = order.codigoPedido,
                destination = destination,
                errorMessage = "El pedido no contiene número de mesa o portamesa",
            )
        }

        return DispatchResult.TableDelivery(
            orderNumber = order.codigoPedido,
            tableTent = tableTent,
            message = "Pedido asignado para entrega en mesa con portamesa $tableTent",
        )
    }
}

class KitchenPrinterDispatchPolicy(
    private val printerClient: KitchenPrinterClient = TcpKitchenPrinterClient(),
) : OrderDispatchPolicy {
    private val logger = LoggerFactory.getLogger(KitchenPrinterDispatchPolicy::class.java)
    override val destination: KioskDispatchDestination = KioskDispatchDestination.IMPRESORA_COCINA

    override suspend fun dispatch(
        order: KioskOrderRecord,
        config: KioskDispatchConfig,
        itemNames: Map<Int, String>,
    ): DispatchResult {
        val ticketBytes = EscPosKitchenTicketBuilder.buildTicket(order, itemNames)
        val printerIp = config.kitchenPrinterIp?.trim()

        if (printerIp.isNullOrBlank()) {
            logger.warn("Kitchen printer IP not configured for order {}", order.codigoPedido)
            return DispatchResult.KitchenPrinter(
                orderNumber = order.codigoPedido,
                printerIp = null,
                port = config.kitchenPrinterPort,
                bytesCount = ticketBytes.size,
                printedSuccessfully = false,
                errorMessage = "IP de impresora de cocina no configurada",
            )
        }

        val sendResult = printerClient.sendBytes(printerIp, config.kitchenPrinterPort, ticketBytes)
        return sendResult.fold(
            onSuccess = {
                logger.info(
                    "Order {} successfully printed to kitchen printer at {}:{}",
                    order.codigoPedido,
                    printerIp,
                    config.kitchenPrinterPort,
                )
                DispatchResult.KitchenPrinter(
                    orderNumber = order.codigoPedido,
                    printerIp = printerIp,
                    port = config.kitchenPrinterPort,
                    bytesCount = ticketBytes.size,
                    printedSuccessfully = true,
                )
            },
            onFailure = { error ->
                logger.error(
                    "Failed to print kitchen ticket for order {} to {}:{}",
                    order.codigoPedido,
                    printerIp,
                    config.kitchenPrinterPort,
                    error,
                )
                DispatchResult.KitchenPrinter(
                    orderNumber = order.codigoPedido,
                    printerIp = printerIp,
                    port = config.kitchenPrinterPort,
                    bytesCount = ticketBytes.size,
                    printedSuccessfully = false,
                    errorMessage = error.message ?: "Error al conectar con impresora de cocina",
                )
            },
        )
    }
}

class OrderDispatchPolicyFactory(
    private val printerClient: KitchenPrinterClient = TcpKitchenPrinterClient(),
) {
    fun getPolicy(destination: KioskDispatchDestination): OrderDispatchPolicy =
        when (destination) {
            KioskDispatchDestination.RETIRO_MOSTRADOR -> CounterPickupDispatchPolicy()
            KioskDispatchDestination.IMPRESORA_COCINA -> KitchenPrinterDispatchPolicy(printerClient)
            KioskDispatchDestination.MESAS -> TableDeliveryDispatchPolicy()
        }

    fun getPolicy(destinationStr: String?): OrderDispatchPolicy = getPolicy(KioskDispatchDestination.fromString(destinationStr))
}
