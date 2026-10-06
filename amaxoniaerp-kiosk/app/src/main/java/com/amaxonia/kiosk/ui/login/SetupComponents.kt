package com.amaxonia.kiosk.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.FlowWaves
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.outline
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.theme.FlowIndigoSoft
import com.amaxonia.kiosk.ui.theme.FlowLavender
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

private val HeroHeight = 820.dp
private val HeroOverlap = 260.dp
private val FieldHeight = 108.dp
private val WaveHeight = 240.dp
private val CardRadius = 48.dp
private const val ORB_ALPHA = 0.10f
private const val WAVE_BACK_ALPHA = 0.35f
private const val DISABLED_TEXT_ALPHA = 0.6f

/**
 * Operator setup frame (login, company, caja): Flow ERP deep-gradient hero with the wordmark,
 * title and subtitle, waves melting into the page and a big white card for the form. Scrolls and
 * pads for the on-screen keyboard (the window uses adjustResize + edge-to-edge).
 */
@Composable
fun SetupScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    heroExtra: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var rootTop by remember { mutableFloatStateOf(0f) }
    // The hero follows the card: it ends HeroOverlap below the card top, wherever centering/scrolling puts it.
    var cardTop by remember { mutableStateOf<Dp?>(null) }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .onGloballyPositioned { rootTop = it.positionInRoot().y },
    ) {
        val screenWidth = maxWidth
        val viewport = maxHeight
        val heroHeight = cardTop?.let { it + HeroOverlap }?.coerceAtLeast(WaveHeight) ?: HeroHeight
        Box(modifier = Modifier.fillMaxWidth().height(heroHeight).background(KioskColors.deepBrush)) {
            if (!LocalHighContrast.current) {
                HeroOrb(size = 520.dp, x = screenWidth - 300.dp, y = (-180).dp)
                HeroOrb(size = 260.dp, x = (-90).dp, y = heroHeight - 560.dp)
            }
            FlowWaves(
                modifier = Modifier.fillMaxWidth().height(WaveHeight).align(Alignment.BottomCenter),
                frontBrush = SolidColor(colors.background),
                backColor = FlowLavender.copy(alpha = WAVE_BACK_ALPHA),
                amplitude = 26.dp,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            // At least one screen tall and centered: short forms sit in the middle of the portrait screen.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        .padding(start = 72.dp, end = 72.dp, top = 96.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                BrandWordmark(onDark = true, height = 120.dp)
                Spacer(Modifier.height(56.dp))
                Text(text = title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = FlowIndigoSoft)
                if (heroExtra != null) {
                    Spacer(Modifier.height(28.dp))
                    heroExtra()
                }
                Spacer(Modifier.height(56.dp))
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                cardTop = with(density) { (coords.positionInRoot().y - rootTop).toDp() }
                            }.softShadow(CardRadius, Depth.High),
                    shape = RoundedCornerShape(CardRadius),
                    color = colors.surface,
                    border = if (LocalHighContrast.current) BorderStroke(3.dp, colors.onSurface) else null,
                ) {
                    Column(modifier = Modifier.padding(56.dp), content = content)
                }
                if (footer != null) {
                    Spacer(Modifier.height(40.dp))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun HeroOrb(
    size: Dp,
    x: Dp,
    y: Dp,
) {
    Box(
        modifier =
            Modifier
                .offset(x = x, y = y)
                .size(size)
                .background(Color.White.copy(alpha = ORB_ALPHA), CircleShape),
    )
}

/** Translucent pill on the hero (company · user on the caja screen). */
@Composable
fun HeroChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

/**
 * Large filled text field for the setup forms: 108 dp tall container (the whole box focuses the
 * field), 30 sp text, label inside and a brand outline while focused (red when [isError]).
 */
@Composable
fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shape = MaterialTheme.shapes.medium
    val borderColor =
        when {
            isError -> colors.error
            focused -> colors.primary
            highContrast -> colors.onSurface
            else -> Color.Transparent
        }
    val container =
        when {
            isError -> colors.errorContainer
            focused -> colors.surface
            else -> colors.surfaceVariant
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FieldHeight)
                .clip(shape)
                .background(container)
                .outline(if (borderColor == Color.Transparent) 0.dp else 3.dp, borderColor, shape)
                .clickable(interactionSource = null, indication = null, enabled = enabled) { focusRequester.requestFocus() },
        contentAlignment = Alignment.CenterStart,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { text -> { Text(text) } },
            leadingIcon = {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 16.dp).size(40.dp),
                )
            },
            trailingIcon = trailingIcon,
            singleLine = true,
            enabled = enabled,
            isError = isError,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedLeadingIconColor = colors.primary,
                    unfocusedLeadingIconColor = colors.onSurfaceVariant,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant,
                    disabledTextColor = colors.onSurface.copy(alpha = DISABLED_TEXT_ALPHA),
                ),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

/** Red inline message with an icon, animated in/out. */
@Composable
fun SetupErrorBanner(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val colors = MaterialTheme.colorScheme
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
                    .background(colors.errorContainer, MaterialTheme.shapes.medium)
                    .outline(if (LocalHighContrast.current) 3.dp else 0.dp, colors.error, MaterialTheme.shapes.medium)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(20.dp))
            Text(text = message.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
        }
    }
}

/** Compact pill toggle (e.g. PA / VE). */
@Composable
fun SetupPillToggle(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val pill = RoundedCornerShape(percent = 50)
    Row(
        modifier =
            modifier
                .background(colors.surfaceVariant, pill)
                .outline(if (LocalHighContrast.current) 2.dp else 0.dp, colors.onSurface, pill)
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = 112.dp, minHeight = 72.dp)
                        .clip(pill)
                        .background(if (isSelected) colors.primary else Color.Transparent)
                        .kioskPressable(enabled = enabled) { onSelect(value) }
                        .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
