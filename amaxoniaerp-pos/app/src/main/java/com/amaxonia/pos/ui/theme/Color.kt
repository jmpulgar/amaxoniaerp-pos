package com.amaxonia.pos.ui.theme

import androidx.compose.ui.graphics.Color

// --- Paleta de Colores Oficial Amaxonia POS ---

// Color Principal (Botones, Títulos destacados)
val AmaxoniaBlue = Color(0xFF1A72DD)

// Colores de Fondo y Superficies
val BgLightGray = Color(0xFFF5F5F5) // Gris claro para el fondo de las pantallas
val SurfaceWhite = Color(0xFFFFFFFF) // Blanco para tarjetas y campos de texto

// Colores de Texto e Iconos
val TextPrimary = Color(0xFF000000) // Negro para el texto principal
// Color(0xFF000000) con 5% de opacidad se traduce a un gris muy claro para placeholders o divisores sutiles.
// Para un gris de texto secundario más legible, usamos un gris medio estándar:
val TextSecondary = Color(0xFF757575)

// Colores de Estado y Acción
val ErrorRed = Color(0xFFB00020)    // Rojo estándar de Material Design para errores
val SuccessGreen = Color(0xFF4CAF50) // Verde para mensajes de éxito (opcional por ahora)