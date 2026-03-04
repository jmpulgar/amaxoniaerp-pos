package com.amaxonia.pos.ui.payment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    changeDue: Double,
    paymentMethodsLabel: String,
    codFactura: String,
    onNextOrder: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        visible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1565C0)) // Fondo Azul
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icono Check
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE3F2FD), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "¡Transacción Exitosa!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )

                Text(
                    if (codFactura.isBlank()) "Factura generada correctamente" else "Factura: $codFactura",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Detalles del pago
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1565C0), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "Metodo de pago: ${paymentMethodsLabel.ifBlank { "N/A" }}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Divider(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Cambio / Vuelto: $ ${Money.format(Money.fromDouble(changeDue))}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Email y Recibo digital
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {}, // Lógica para enviar correo
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ENVIAR RECIBO", color = Color(0xFF1565C0))
                }
            }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Botones Inferiores
        OutlinedButton(
            onClick = {}, // Lógica para imprimir
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("IMPRIMIR RECIBO")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNextOrder,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("SIGUIENTE ORDEN", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
        }
    }
}
