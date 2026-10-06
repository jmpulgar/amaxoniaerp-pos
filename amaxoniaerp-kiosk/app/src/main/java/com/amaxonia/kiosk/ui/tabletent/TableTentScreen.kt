package com.amaxonia.kiosk.ui.tabletent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.CheckoutStep
import com.amaxonia.kiosk.ui.components.CheckoutStepper
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.NumericKeypad
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.rememberCheckoutSteps
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas

private val LandscapeKeyHeight = 100.dp

@Composable
fun TableTentScreen(
    viewModel: TableTentViewModel,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            KioskHeader(title = stringResource(R.string.checkout_step_details), onBack = onBack)
            CheckoutStepper(steps = rememberCheckoutSteps(), currentIndex = CheckoutStep.DETAILS)

            if (LocalKioskCanvas.current.isLandscape) {
                // Landscape: prompt, number and actions on the left; the keypad on the right.
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TentHeading()
                        Spacer(modifier = Modifier.height(48.dp))
                        TentActions(
                            uiState = uiState,
                            onConfirm = { viewModel.confirm(onConfirmed) },
                            onSkip = { viewModel.skip(onConfirmed) },
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TentNumberDisplay(number = uiState.tentNumber, valid = uiState.isValid)
                        Spacer(modifier = Modifier.height(24.dp))
                        NumericKeypad(
                            onDigit = viewModel::onDigit,
                            onBackspace = viewModel::onBackspace,
                            extraKey = "C",
                            onExtraKey = viewModel::onClear,
                            keyHeight = LandscapeKeyHeight,
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        )
                    }
                }
                return@Column
            }

            ScrollableFillColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 32.dp),
            ) {
                TentPrompt(number = uiState.tentNumber, valid = uiState.isValid)

                Spacer(modifier = Modifier.height(32.dp))

                NumericKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    extraKey = "C",
                    onExtraKey = viewModel::onClear,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                shadowElevation = 20.dp,
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                border = if (LocalHighContrast.current) BorderStroke(3.dp, colors.onSurface) else null,
            ) {
                Box(modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp)) {
                    TentActions(
                        uiState = uiState,
                        onConfirm = { viewModel.confirm(onConfirmed) },
                        onSkip = { viewModel.skip(onConfirmed) },
                    )
                }
            }
        }
    }
}

/** Table icon, title, hint and the big number display. */
@Composable
private fun TentPrompt(
    number: String,
    valid: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TentHeading()
        Spacer(modifier = Modifier.height(32.dp))
        TentNumberDisplay(number = number, valid = valid)
    }
}

/** Table icon, title and hint. */
@Composable
private fun TentHeading() {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(136.dp).background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(104.dp).background(KioskColors.ctaBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.TableRestaurant,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(60.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.table_tent_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.table_tent_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Confirm (needs a number) and "no table tent" actions. */
@Composable
private fun TentActions(
    uiState: TableTentUiState,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.centeredMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KioskButton(
            text =
                if (uiState.isValid) {
                    stringResource(R.string.table_tent_confirm, uiState.tentNumber)
                } else {
                    stringResource(R.string.table_tent_confirm_empty)
                },
            onClick = onConfirm,
            enabled = uiState.isValid,
            modifier = Modifier.fillMaxWidth(),
        )
        KioskButton(
            text = stringResource(R.string.table_tent_skip),
            onClick = onSkip,
            style = KioskButtonStyle.Ghost,
            height = KioskTouchTarget,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TentNumberDisplay(
    number: String,
    valid: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (valid) colors.primary else colors.outline, label = "tent_border")
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier =
            Modifier
                .widthIn(min = 420.dp)
                .height(180.dp)
                .background(colors.surface, shape)
                .border(if (valid) 6.dp else 3.dp, border, shape)
                .padding(horizontal = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = number,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.7f) + fadeIn()) togetherWith fadeOut()
            },
            label = "tent_number",
        ) { value ->
            Text(
                text = value.ifEmpty { "– – –" },
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, lineHeight = 124.sp, letterSpacing = 8.sp),
                color = if (value.isEmpty()) colors.outline else colors.primary,
            )
        }
    }
}
