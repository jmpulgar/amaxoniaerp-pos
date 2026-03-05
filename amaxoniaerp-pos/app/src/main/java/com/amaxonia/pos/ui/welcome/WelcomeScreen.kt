package com.amaxonia.pos.ui.welcome

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.R
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.BgLightGray
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class) // Necesario para ModalBottomSheet
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    // onRequestAccountClick se mantiene por si quieres trackear el evento,
    // pero la navegación ahora se maneja internamente.
    onRequestAccountClick: () -> Unit
) {
    val context = LocalContext.current
    val images = listOf(
        R.drawable.welcome_1,
        R.drawable.welcome_2,
        R.drawable.welcome_3
    )

    var currentImage by remember { mutableIntStateOf(0) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // --- ESTADO PARA EL MENÚ DE CONTACTO ---
    var showContactSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val scope = rememberCoroutineScope()
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    // Función auxiliar para abrir enlaces
    fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun pauseAutoplay(ms: Long = 1200L) {
        isUserInteracting = true
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(ms)
            isUserInteracting = false
        }
    }

    fun goTo(index: Int) {
        if (index == currentImage) return
        pauseAutoplay(1200L)
        currentImage = index
    }

    fun next() = goTo((currentImage + 1) % images.size)
    fun prev() = goTo(if (currentImage - 1 < 0) images.size - 1 else currentImage - 1)

    // Autoplay
    LaunchedEffect(currentImage, isUserInteracting) {
        if (isUserInteracting) return@LaunchedEffect
        delay(3500)
        currentImage = (currentImage + 1) % images.size
    }

    val subtitleColor = Color(0xFF2A3256)

    // --- TUNEABLES DEL LOGO ---
    val HEADER_HEIGHT = 76.dp
    val LOGO_BOX = 44.dp
    val LOGO_ZOOM = 1.0f
    val LOGO_NUDGE_X = 0f
    val LOGO_NUDGE_Y = 0f
    val LOGO_TEXT_GAP = 10.dp

    Scaffold(containerColor = BgLightGray) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // --- HEADER LOGO ---
            Spacer(modifier = Modifier.weight(0.1f)) // Espacio superior dinámico
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(LOGO_BOX),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_amaxonia),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = LOGO_ZOOM
                                    scaleY = LOGO_ZOOM
                                    translationX = LOGO_NUDGE_X
                                    translationY = LOGO_NUDGE_Y
                                },
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(LOGO_TEXT_GAP))

                    Text(
                        text = "Amaxonia ERP",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmaxoniaBlue,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- CARRUSEL ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Mantiene la responsividad principal
                    .pointerInput(images.size) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                                isUserInteracting = true
                                resumeJob?.cancel()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                val threshold = 60f
                                when {
                                    totalDrag <= -threshold -> next()
                                    totalDrag >= threshold -> prev()
                                    else -> pauseAutoplay(700L)
                                }
                            },
                            onDragCancel = { pauseAutoplay(700L) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = currentImage,
                    label = "imageFade",
                    animationSpec = tween(500)
                ) { index ->
                    Image(
                        painter = painterResource(id = images[index]),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Gestión fácil para tu negocio",
                fontSize = 16.sp,
                color = subtitleColor
            )

            // --- INDICADOR ---
            HorizontalPagerIndicator(
                count = images.size,
                activeIndex = currentImage,
                activeColor = AmaxoniaBlue,
                inactiveColor = Color(0x332A3256),
                onSelect = { idx -> goTo(idx) }
            )

            Spacer(modifier = Modifier.weight(0.15f))

            // --- BOTÓN INICIAR SESIÓN ---
            Button(
                onClick = {
                    pauseAutoplay(1500L)
                    onLoginClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue)
            ) {
                Text(
                    text = "Iniciar sesión",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTÓN SOLICITAR CUENTA (AHORA ABRE EL MENÚ) ---
            OutlinedButton(
                onClick = {
                    pauseAutoplay(1500L)
                    onRequestAccountClick() // Callback opcional
                    showContactSheet = true // ACTIVAMOS EL SHEET
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
            ) {
                Text(
                    text = "Solicitar tu cuenta",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AmaxoniaBlue
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- MENÚ DESPLEGABLE (BOTTOM SHEET) ---
        if (showContactSheet) {
            ModalBottomSheet(
                onDismissRequest = { showContactSheet = false },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 48.dp) // Espacio inferior seguro
                ) {
                    Text(
                        text = "Solicitar Cuenta",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmaxoniaBlue,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Elige cómo deseas contactarnos:",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Opción 1: Web
                    ContactOptionItem(
                        icon = Icons.Default.Language, // Icono genérico web
                        text = "Visitar amaxoniaerp.com",
                        onClick = {
                            showContactSheet = false
                            openLink("https://amaxoniaerp.com")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opción 2: WhatsApp
                    ContactOptionItem(
                        icon = Icons.Default.Phone, // Icono genérico teléfono/chat
                        text = "Escribir por WhatsApp",
                        onClick = {
                            showContactSheet = false
                            // Enlace universal de WhatsApp API
                            openLink("https://wa.me/50764188582")
                        }
                    )
                }
            }
        }
    }
}

// --- COMPONENTES AUXILIARES ---

@Composable
fun ContactOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgLightGray)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AmaxoniaBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = AmaxoniaBlue
        )
    }
}

@Composable
private fun HorizontalPagerIndicator(
    count: Int,
    activeIndex: Int,
    activeColor: Color,
    inactiveColor: Color,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { idx ->
            val isActive = idx == activeIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .height(6.dp)
                    .width(if (isActive) 22.dp else 10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) activeColor else inactiveColor)
                    .clickable { onSelect(idx) }
            )
        }
    }
}