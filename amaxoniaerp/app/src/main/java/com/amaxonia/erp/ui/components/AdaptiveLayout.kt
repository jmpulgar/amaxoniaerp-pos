package com.amaxonia.erp.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Determina si el dispositivo se encuentra actualmente en orientación horizontal (Landscape).
 */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Determina si la altura disponible de la pantalla es reducida (típica en teléfonos y terminales
 * POS en horizontal, con menos de 500dp de alto).
 */
@Composable
fun isCompactHeight(): Boolean =
    LocalConfiguration.current.screenHeightDp < 500

/**
 * Determina si el ancho disponible de la pantalla es amplio (tablets, terminales de mostrador
 * o pantallas con 600dp o más de ancho).
 */
@Composable
fun isWideScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600

/**
 * Determina si se trata de un dispositivo horizontal amplio (tablet o terminal de ventas).
 */
@Composable
fun isTabletLandscape(): Boolean =
    isLandscape() && isWideScreen()

/**
 * Provee la altura actual de la pantalla en dp.
 */
@Composable
fun screenHeightDp(): Dp =
    LocalConfiguration.current.screenHeightDp.dp

/**
 * Provee el ancho actual de la pantalla en dp.
 */
@Composable
fun screenWidthDp(): Dp =
    LocalConfiguration.current.screenWidthDp.dp
