package com.amaxonia.kiosk.ui.diningmode

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.FlowWaves
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.StaggeredReveal
import com.amaxonia.kiosk.ui.components.brushTint
import com.amaxonia.kiosk.ui.theme.FlowIndigoSoft
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

const val MODE_DINE_IN = "COMER_AQUI"
const val MODE_TAKEAWAY = "PARA_LLEVAR"

private val MinCardHeight = 560.dp
private val MaxCardHeight = 900.dp
private val MinTitleRoom = 340.dp
private val HeroOverlap = 220.dp
private val BottomGap = 56.dp
private val RoomForGlyphs = 440.dp

@Composable
fun DiningModeScreen(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            KioskHeader(
                title = null,
                onBack = onBack,
                leading = { BrandWordmark() },
            )
            // Hero band on top, two tall choice cards overlapping it. Heights follow the viewport so
            // the layout fills the screen and still fits (scrolling if needed) in "pantalla baja".
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val viewport = maxHeight
                val cardHeight = (viewport - BottomGap - MinTitleRoom).coerceIn(MinCardHeight, MaxCardHeight)
                val cardsTop = (viewport - BottomGap - cardHeight).coerceAtLeast(MinTitleRoom)
                Box(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    DiningHero(height = cardsTop + HeroOverlap, titleRoom = cardsTop)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = cardsTop, start = 48.dp, end = 48.dp, bottom = BottomGap)
                                .height(cardHeight),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        StaggeredReveal(index = 1, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DiningModeOptionCard(
                                title = stringResource(R.string.dining_mode_dine_in_title),
                                subtitle = stringResource(R.string.dining_mode_dine_in_desc),
                                icon = Icons.Rounded.Restaurant,
                                isSelected = currentMode == MODE_DINE_IN,
                                onClick = { onModeSelected(MODE_DINE_IN) },
                            )
                        }
                        StaggeredReveal(index = 2, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DiningModeOptionCard(
                                title = stringResource(R.string.dining_mode_takeaway_title),
                                subtitle = stringResource(R.string.dining_mode_takeaway_desc),
                                icon = Icons.Rounded.ShoppingBag,
                                isSelected = currentMode == MODE_TAKEAWAY,
                                onClick = { onModeSelected(MODE_TAKEAWAY) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Brand gradient band with the question in white (centered in [titleRoom]) and a wavy bottom edge. */
@Composable
private fun DiningHero(
    height: Dp,
    titleRoom: Dp,
) {
    val highContrast = LocalHighContrast.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(KioskColors.heroBrush),
    ) {
        if (!highContrast && titleRoom >= RoomForGlyphs) {
            HeroGlyph(Icons.Rounded.LunchDining, size = 260.dp, modifier = Modifier.offset(x = (-60).dp, y = 40.dp))
            HeroGlyph(Icons.Rounded.LocalDrink, size = 200.dp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = 120.dp))
        }
        if (!highContrast) {
            FlowWaves(
                modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter),
                frontBrush = SolidColor(MaterialTheme.colorScheme.background),
                backColor = Color.White.copy(alpha = 0.25f),
                amplitude = 22.dp,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(titleRoom).padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            StaggeredReveal(index = 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.dining_mode_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.dining_mode_subtitle),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (highContrast) Color.White else FlowIndigoSoft,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Big, faint food glyph bubble decorating the hero band. */
@Composable
private fun HeroGlyph(
    icon: ImageVector,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).background(Color.White.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun DiningModeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val halo by animateColorAsState(
        if (isSelected) colors.secondaryContainer else colors.primaryContainer,
        label = "dining_halo",
    )

    KioskCard(
        modifier = modifier.fillMaxSize(),
        selected = isSelected,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(56.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // "Illustration": a big glyph on a soft halo around a brand-gradient disc.
                Box(
                    modifier = Modifier.size(320.dp).background(halo, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(232.dp).background(KioskColors.ctaBrush, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(136.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(40.dp))
                ChooseChip(highContrast = highContrast)
            }
        }
    }
}

/** Round arrow affordance at the bottom of each choice card. */
@Composable
private fun ChooseChip(highContrast: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(88.dp)
                .background(if (highContrast) colors.primary else colors.primaryContainer, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = if (highContrast) colors.onPrimary else Color.Black,
            modifier =
                Modifier
                    .size(48.dp)
                    .then(if (highContrast) Modifier else Modifier.brushTint(KioskColors.ctaBrush)),
        )
    }
}
