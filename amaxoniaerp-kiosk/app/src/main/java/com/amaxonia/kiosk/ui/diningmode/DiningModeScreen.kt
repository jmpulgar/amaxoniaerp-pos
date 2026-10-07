package com.amaxonia.kiosk.ui.diningmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.StaggeredReveal
import com.amaxonia.kiosk.ui.components.brushTint
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas

const val MODE_DINE_IN = "COMER_AQUI"
const val MODE_TAKEAWAY = "PARA_LLEVAR"

private val CardsMaxWidth = 1000.dp
private const val CARD_ASPECT = 0.82f
private const val DISC_HEIGHT_FRACTION = 0.85f
private const val GLYPH_FRACTION = 0.58f

/** Landscape screens are short: wider cards keep both choices and the title on screen. */
private const val LANDSCAPE_CARD_ASPECT = 1.3f

/**
 * "¿Dónde vas a comer hoy?" as on fast-food self-order kiosks: a plain white screen, the question
 * on top and two square choice cards (label above, illustration below). Tapping a card continues.
 */
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
            ScrollableFillColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            ) {
                BrandWordmark(height = 88.dp)
                Spacer(modifier = Modifier.height(48.dp))
                StaggeredReveal(index = 0) {
                    Text(
                        text = stringResource(R.string.dining_mode_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(if (LocalKioskCanvas.current.isLandscape) 40.dp else 72.dp))
                Row(
                    modifier = Modifier.widthIn(max = CardsMaxWidth).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    StaggeredReveal(index = 1, modifier = Modifier.weight(1f)) {
                        DiningModeOptionCard(
                            title = stringResource(R.string.dining_mode_dine_in_title),
                            icon = Icons.Rounded.Restaurant,
                            isSelected = currentMode == MODE_DINE_IN,
                            onClick = { onModeSelected(MODE_DINE_IN) },
                        )
                    }
                    StaggeredReveal(index = 2, modifier = Modifier.weight(1f)) {
                        DiningModeOptionCard(
                            title = stringResource(R.string.dining_mode_takeaway_title),
                            icon = Icons.Rounded.ShoppingBag,
                            isSelected = currentMode == MODE_TAKEAWAY,
                            onClick = { onModeSelected(MODE_TAKEAWAY) },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                KioskButton(
                    text = stringResource(R.string.menu_cancel_order),
                    onClick = onBack,
                    style = KioskButtonStyle.Secondary,
                    height = KioskTouchTarget,
                    modifier = Modifier.widthIn(min = 420.dp),
                )
            }
        }
    }
}

@Composable
private fun DiningModeOptionCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    KioskCard(
        modifier = modifier.fillMaxWidth().aspectRatio(if (LocalKioskCanvas.current.isLandscape) LANDSCAPE_CARD_ASPECT else CARD_ASPECT),
        selected = isSelected,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Illustration: a big brand-colored glyph on a pale disc.
                Box(
                    modifier = Modifier.fillMaxHeight(DISC_HEIGHT_FRACTION).aspectRatio(1f).background(colors.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier =
                            Modifier
                                .fillMaxSize(GLYPH_FRACTION)
                                .then(if (highContrast) Modifier else Modifier.brushTint(KioskColors.heroBrush)),
                    )
                }
            }
        }
    }
}
