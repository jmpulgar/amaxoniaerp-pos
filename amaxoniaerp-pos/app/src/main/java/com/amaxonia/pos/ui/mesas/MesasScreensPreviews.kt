@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod")

package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.mesas.CuentaDetalleResponse
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa
import com.amaxonia.pos.domain.model.mesas.EstadoMesaOperativo
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the Fase-2 restaurant screens: mesas (grid + selection bar),
 * cuenta/división (cuenta activa + split cards) y comanda (líneas de pedido).
 *
 * Exercises the production composables at every target Android width plus landscape, including
 * the tricky variants: enormous totals that must shrink via AdaptiveAmountText instead of
 * clipping, long product descriptions, and all the table/order states.
 */
// ─────────────────────────────────────────────────────────────────────────────
// AreasMesas: grid fluido + barra de confirmación.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Mesas grid · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun MesasGrid320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MesasGridPreviewContent()
    }
}

@Preview(name = "Mesas grid · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun MesasGrid360() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MesasGridPreviewContent()
    }
}

@Preview(name = "Mesas grid · 480×960 (3 columnas)", showBackground = true, widthDp = 480, heightDp = 960)
@Composable
private fun MesasGrid480() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MesasGridPreviewContent()
    }
}

@Preview(name = "Mesas grid · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun MesasGridLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MesasGridPreviewContent()
    }
}

@Preview(name = "Barra mesa seleccionada · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun SelectedMesaBarPreview() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SelectedMesaBar(
            mesaName = "Mesa Terraza 12",
            areaName = "Terraza Jardín",
            onClear = {},
            onConfirm = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CuentaMesa: cuenta activa (total enorme) + tarjeta de división.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Cuenta activa · 320×568 · total enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CuentaActiva320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CuentaActivaCardPreviewContent()
    }
}

@Preview(name = "Cuenta activa · 412×915", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CuentaActiva412() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CuentaActivaCardPreviewContent()
    }
}

@Preview(name = "Cuenta activa · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun CuentaActivaLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CuentaActivaCardPreviewContent()
    }
}

@Preview(name = "División producto · 320×568 · precio enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun SplitProduct320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SplitProductCard(
            pedido =
                previewPedido(
                    descripcion = "Parrillada mixta familiar para cuatro personas con guarniciones",
                    total = 9_876_543.21,
                ),
            disponible = 3.0,
            value = "1",
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comanda: líneas de pedido en los estados del flujo de cocina.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Comanda líneas · 320×568 · todos los estados", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun ComandaLines320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ComandaLinesPreviewContent()
    }
}

@Preview(name = "Comanda líneas · 360×640 · monto enorme", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ComandaLinesHuge() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LineRowPedido(
                pedido =
                    previewPedido(
                        descripcion = "Botella vino reserva malbec",
                        total = 9_876_543.21,
                        estado = EstadoPedidoMesa.EN_PREPARACION,
                    ),
                onCambiarEstado = { _, _ -> },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview-only helpers — mirror the production layouts.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MesasGridPreviewContent() {
    // Estados operativos rotados para cubrir Disponible / Ocupada / Cuenta solicitada.
    val estados =
        listOf(
            null,
            EstadoMesaOperativo.OCUPADA,
            null,
            ESTADO_CUENTA_SOLICITADA,
            EstadoMesaOperativo.OCUPADA,
            null,
        )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(previewMesas()) { mesa ->
            MesaCard(
                mesa = mesa,
                isSelected = mesa.id == 3,
                estadoOperativo = estados.getOrElse(mesa.id - 1) { null },
                onClick = {},
            )
        }
    }
}

private fun previewMesas(): List<Mesa> =
    listOf(
        Mesa(id = 1, areaId = 1, codigo = "M01", nombre = "Mesa 01", capacidad = 4, forma = "rectangular"),
        Mesa(id = 2, areaId = 1, codigo = "M02", nombre = "Mesa 02", capacidad = 2, forma = "redonda"),
        Mesa(id = 3, areaId = 1, codigo = "M03", nombre = "Mesa VIP 03", capacidad = 8, forma = "rectangular"),
        Mesa(id = 4, areaId = 1, codigo = "M04", nombre = "Mesa 04", capacidad = 4, forma = "cuadrada"),
        Mesa(id = 5, areaId = 1, codigo = "M05", nombre = "Mesa 05", capacidad = 6, forma = "rectangular"),
        Mesa(id = 6, areaId = 1, codigo = "M06", nombre = "Mesa 06", capacidad = 2, forma = "redonda"),
    )

@Composable
private fun CuentaActivaCardPreviewContent() {
    Column(modifier = Modifier.padding(16.dp)) {
        CuentaActivaCard(
            cuenta = previewCuenta(),
            canPay = true,
            onPay = {},
            onCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewCuenta(): CuentaMesaResponse =
    CuentaMesaResponse(
        id = 1,
        numeroCuenta = 3,
        estado = EstadoCuentaMesa.ACTIVA,
        subtotal = 8_522.98,
        impuesto = 1_363.68,
        total = 9_876_543.21,
        detalle =
            listOf(
                CuentaDetalleResponse(
                    id = 1,
                    productoId = 10,
                    itemDescripcion = "Parrillada mixta familiar para cuatro personas",
                    cantidad = 2.0,
                    itemTotalConIva = 4_938_271.60,
                ),
                CuentaDetalleResponse(
                    id = 2,
                    productoId = 11,
                    itemDescripcion = "Ensalada cesar con pollo gratinado",
                    cantidad = 1.0,
                    itemTotalConIva = 4_938_271.61,
                ),
            ),
    )

private fun previewPedido(
    descripcion: String,
    total: Double,
    estado: String = EstadoPedidoMesa.PENDIENTE,
): PedidoMesa =
    PedidoMesa(
        id = descripcion.hashCode(),
        itemDescripcion = descripcion,
        itemCantidad = 2.0,
        itemTotalConIva = total,
        estado = estado,
        comandaSecuencia = if (estado != EstadoPedidoMesa.PENDIENTE) 7 else null,
    )

@Composable
private fun ComandaLinesPreviewContent() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LineRowPedido(
            pedido = previewPedido(descripcion = "Tequeños de queso (12 uds)", total = 12.50, estado = EstadoPedidoMesa.PENDIENTE),
            onCambiarEstado = null,
            modifier = Modifier.fillMaxWidth(),
        )
        LineRowPedido(
            pedido = previewPedido(descripcion = "Pasta carbonara", total = 18.00, estado = EstadoPedidoMesa.ENVIADA),
            onCambiarEstado = { _, _ -> },
            modifier = Modifier.fillMaxWidth(),
        )
        LineRowPedido(
            pedido = previewPedido(descripcion = "Pizza margarita extra grande", total = 22.75, estado = EstadoPedidoMesa.EN_PREPARACION),
            onCambiarEstado = { _, _ -> },
            modifier = Modifier.fillMaxWidth(),
        )
        LineRowPedido(
            pedido = previewPedido(descripcion = "Refresco cola 2L", total = 4.50, estado = EstadoPedidoMesa.LISTA),
            onCambiarEstado = { _, _ -> },
            modifier = Modifier.fillMaxWidth(),
        )
        LineRowPedido(
            pedido = previewPedido(descripcion = "Café espresso doble", total = 3.25, estado = EstadoPedidoMesa.ENTREGADA),
            onCambiarEstado = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
