package com.amaxonia.kiosk.ui.cajasetup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.network.KioskCajaDto
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.StaggeredReveal
import com.amaxonia.kiosk.ui.components.outline
import com.amaxonia.kiosk.ui.login.HeroChip
import com.amaxonia.kiosk.ui.login.LoadingPill
import com.amaxonia.kiosk.ui.login.SetupErrorBanner
import com.amaxonia.kiosk.ui.login.SetupScaffold
import com.amaxonia.kiosk.ui.login.SetupTextField
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

private const val SAMPLE_ORDER_NUMBER = "001"

/** Callbacks of the stateless [CajaSetupContent]. */
class CajaSetupActions(
    val onCajaSelected: (String) -> Unit,
    val onPrefixChanged: (String) -> Unit,
    val onStart: () -> Unit,
    val onRetry: () -> Unit,
    val onLogout: () -> Unit,
) {
    companion object {
        val NoOp = CajaSetupActions({}, {}, {}, {}, {})
    }
}

@Composable
fun CajaSetupScreen(
    viewModel: CajaSetupViewModel,
    onStarted: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CajaSetupEvent.Started -> onStarted()
                CajaSetupEvent.LoggedOut -> onLoggedOut()
            }
        }
    }
    CajaSetupContent(
        uiState = uiState,
        actions =
            CajaSetupActions(
                onCajaSelected = viewModel::onCajaSelected,
                onPrefixChanged = viewModel::onPrefixChanged,
                onStart = {
                    focusManager.clearFocus()
                    viewModel.start()
                },
                onRetry = { viewModel.loadCajas() },
                onLogout = viewModel::logout,
            ),
        modifier = modifier,
    )
}

/** Stateless caja setup: active cajas as big cards, the order prefix and "Comenzar". */
@Composable
fun CajaSetupContent(
    uiState: CajaSetupUiState,
    actions: CajaSetupActions,
    modifier: Modifier = Modifier,
) {
    SetupScaffold(
        title = stringResource(R.string.caja_setup_title),
        subtitle = stringResource(R.string.caja_setup_subtitle),
        modifier = modifier,
        heroExtra = {
            if (uiState.companyName.isNotBlank()) {
                HeroChip(
                    text =
                        if (uiState.username.isNotBlank()) {
                            stringResource(R.string.caja_setup_session, uiState.companyName, uiState.username)
                        } else {
                            uiState.companyName
                        },
                    icon = Icons.Rounded.Storefront,
                )
            }
        },
        footer = {
            KioskButton(
                text = stringResource(R.string.caja_setup_logout),
                onClick = actions.onLogout,
                style = KioskButtonStyle.Ghost,
                icon = Icons.AutoMirrored.Rounded.Logout,
                height = KioskTouchTarget,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        SetupErrorBanner(message = if (uiState.previousCajaInvalid) stringResource(R.string.caja_setup_invalid) else null)
        SectionLabel(text = stringResource(R.string.caja_setup_list_title), icon = Icons.Rounded.PointOfSale)
        Spacer(Modifier.height(24.dp))
        CajaList(uiState, actions)

        Spacer(Modifier.height(48.dp))
        SectionLabel(text = stringResource(R.string.caja_setup_prefix), icon = Icons.Rounded.Tag)
        Spacer(Modifier.height(24.dp))
        PrefixField(uiState, actions)

        Spacer(Modifier.height(48.dp))
        AnimatedVisibility(visible = uiState.showValidation && uiState.selectedCaja == null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = stringResource(R.string.caja_setup_select_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            )
        }
        KioskButton(
            text = stringResource(R.string.caja_setup_start),
            onClick = actions.onStart,
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            dimmed = !uiState.canStart,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    icon: ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CajaList(
    uiState: CajaSetupUiState,
    actions: CajaSetupActions,
) {
    when {
        uiState.isLoading -> LoadingPill(text = stringResource(R.string.caja_setup_loading))
        uiState.loadError != null ->
            EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.caja_setup_error_title),
                message =
                    when (val error = uiState.loadError) {
                        CajaLoadError.Connectivity -> stringResource(R.string.login_error_connectivity)
                        is CajaLoadError.Server -> error.message.orEmpty()
                        null -> ""
                    },
                onRetry = actions.onRetry,
            )
        uiState.cajas.isEmpty() ->
            EmptyState(
                icon = Icons.Rounded.Inbox,
                title = stringResource(R.string.caja_setup_empty_title),
                message = stringResource(R.string.caja_setup_empty_message),
                onRetry = actions.onRetry,
            )
        else ->
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                uiState.cajas.forEachIndexed { index, caja ->
                    StaggeredReveal(index = index) {
                        CajaCard(
                            caja = caja,
                            selected = caja.idCaja == uiState.selectedCajaId,
                            onClick = { actions.onCajaSelected(caja.idCaja) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun CajaCard(
    caja: KioskCajaDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val badge by animateColorAsState(if (selected) colors.secondary else colors.primaryContainer, label = "caja_badge")
    val details =
        listOfNotNull(
            caja.codCaja?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.caja_setup_code, it) },
            caja.sucursalNombre?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.caja_setup_branch, it) },
        ).joinToString("  ·  ")
    KioskCard(modifier = Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 152.dp).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(badge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PointOfSale,
                    contentDescription = null,
                    tint = if (selected) colors.onSecondary else colors.primary,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.width(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caja.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(16.dp))
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(64.dp))
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .background(colors.surfaceVariant, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PrefixField(
    uiState: CajaSetupUiState,
    actions: CajaSetupActions,
) {
    val colors = MaterialTheme.colorScheme
    val showError = !uiState.isPrefixValid && (uiState.showValidation || uiState.prefix.isNotEmpty())
    SetupTextField(
        value = uiState.prefix,
        onValueChange = actions.onPrefixChanged,
        label = null,
        leadingIcon = Icons.Rounded.Tag,
        isError = showError,
        textStyle =
            MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            ),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
                autoCorrect = false,
            ),
        keyboardActions = KeyboardActions(onDone = { actions.onStart() }),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text =
            if (showError || (uiState.showValidation && !uiState.isPrefixValid)) {
                stringResource(R.string.caja_setup_prefix_error)
            } else {
                stringResource(R.string.caja_setup_prefix_hint)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = if (showError) colors.error else colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp),
    )
    if (uiState.isPrefixValid) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier =
                Modifier
                    .padding(start = 24.dp)
                    .background(KioskColors.softBrush, MaterialTheme.shapes.small)
                    .outline(if (LocalHighContrast.current) 2.dp else 0.dp, colors.onSurface, MaterialTheme.shapes.small)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.caja_setup_prefix_example, "${uiState.prefix}-$SAMPLE_ORDER_NUMBER"),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, MaterialTheme.shapes.large)
                .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(112.dp).background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        KioskButton(
            text = stringResource(R.string.btn_retry),
            onClick = onRetry,
            style = KioskButtonStyle.Secondary,
            icon = Icons.Rounded.Refresh,
            height = KioskTouchTarget,
        )
    }
}
