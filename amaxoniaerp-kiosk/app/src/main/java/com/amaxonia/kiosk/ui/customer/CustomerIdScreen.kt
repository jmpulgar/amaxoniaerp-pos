package com.amaxonia.kiosk.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.KioskSegmentedControl
import com.amaxonia.kiosk.ui.components.NumericKeypad
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas

private val DocTypes = listOf("CEDULA", "RUC", "PASAPORTE")
private const val PASSPORT_INDEX = 2
private const val ICON_RATIO = 0.52f
private const val LANDSCAPE_SIDE_WEIGHT = 0.8f

@Composable
fun CustomerIdScreen(
    viewModel: CustomerIdViewModel,
    onCustomerConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val landscape = LocalKioskCanvas.current.isLandscape

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            KioskHeader(title = stringResource(R.string.checkout_step_details), onBack = onBack)

            if (!uiState.isCustomBilling) {
                InvoiceQuestion(
                    onNoThanks = { viewModel.selectConsumidorFinal(onCustomerConfirmed) },
                    onYes = { viewModel.toggleCustomBilling(true) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                return@Column
            }

            if (landscape && uiState.isCustomBilling) {
                LandscapeInvoiceForm(
                    uiState = uiState,
                    viewModel = viewModel,
                    onCustomerConfirmed = onCustomerConfirmed,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                return@Column
            }

            ScrollableFillColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // Centered while only the two choices show; top-aligned once the invoice form expands.
                verticalArrangement = if (uiState.isCustomBilling) Arrangement.Top else Arrangement.Center,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.customer_title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.customer_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(40.dp))

                val finalCard: @Composable (Modifier) -> Unit = { cardModifier ->
                    CustomerOptionCard(
                        title = stringResource(R.string.customer_final_title),
                        subtitle = stringResource(R.string.customer_final_desc),
                        icon = Icons.Rounded.Person,
                        isSelected = !uiState.isCustomBilling,
                        compact = uiState.isCustomBilling || landscape,
                        onClick = {
                            viewModel.toggleCustomBilling(false)
                            viewModel.selectConsumidorFinal(onCustomerConfirmed)
                        },
                        modifier = cardModifier,
                    )
                }
                val invoiceCard: @Composable (Modifier) -> Unit = { cardModifier ->
                    CustomerOptionCard(
                        title = stringResource(R.string.customer_custom_title),
                        subtitle = stringResource(R.string.customer_custom_desc),
                        icon = Icons.Rounded.ReceiptLong,
                        isSelected = uiState.isCustomBilling,
                        compact = uiState.isCustomBilling || landscape,
                        onClick = { viewModel.toggleCustomBilling(true) },
                        modifier = cardModifier,
                    )
                }
                Column(modifier = Modifier.centeredMaxWidth()) {
                    if (landscape) {
                        // Landscape: the two choices side by side, so the form fits below them.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            finalCard(Modifier.weight(1f).fillMaxHeight())
                            invoiceCard(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        finalCard(Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        invoiceCard(Modifier.fillMaxWidth())
                    }

                    AnimatedVisibility(
                        visible = uiState.isCustomBilling,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        CustomBillingForm(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.padding(top = 36.dp),
                        )
                    }
                }
            }

            BottomActionBar {
                if (uiState.isCustomBilling) {
                    KioskButton(
                        text = stringResource(R.string.customer_confirm),
                        onClick = { viewModel.submitCustomCustomer(onCustomerConfirmed) },
                        dimmed = !uiState.isValid,
                        trailing = {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(40.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    KioskButton(
                        text = stringResource(R.string.customer_continue_final),
                        onClick = { viewModel.selectConsumidorFinal(onCustomerConfirmed) },
                        icon = Icons.Rounded.Person,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Landscape invoice entry in three columns: the two choices, the fields with the confirm button and
 * the keypad, so nothing has to scroll under a bottom bar on a 1080 dp tall screen.
 */
@Composable
private fun LandscapeInvoiceForm(
    uiState: CustomerIdUiState,
    viewModel: CustomerIdViewModel,
    onCustomerConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPassport = uiState.docType == DocTypes[PASSPORT_INDEX]
    Column(modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.customer_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Inner padding keeps card shadows inside each scroll container's clip.
            Column(
                modifier =
                    Modifier.weight(
                        LANDSCAPE_SIDE_WEIGHT,
                    ).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CustomerOptionCard(
                    title = stringResource(R.string.customer_final_title),
                    subtitle = stringResource(R.string.customer_final_desc),
                    icon = Icons.Rounded.Person,
                    isSelected = false,
                    compact = true,
                    onClick = {
                        viewModel.toggleCustomBilling(false)
                        viewModel.selectConsumidorFinal(onCustomerConfirmed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                CustomerOptionCard(
                    title = stringResource(R.string.customer_custom_title),
                    subtitle = stringResource(R.string.customer_custom_desc),
                    icon = Icons.Rounded.ReceiptLong,
                    isSelected = true,
                    compact = true,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BillingFields(uiState = uiState, viewModel = viewModel)
                KioskButton(
                    text = stringResource(R.string.customer_confirm),
                    onClick = { viewModel.submitCustomCustomer(onCustomerConfirmed) },
                    dimmed = !uiState.isValid,
                    trailing = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(40.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.weight(LANDSCAPE_SIDE_WEIGHT).padding(16.dp)) {
                if (!isPassport) {
                    NumericKeypad(
                        onDigit = viewModel::onKeypadInput,
                        onBackspace = viewModel::onKeypadBackspace,
                        extraKey = "-",
                        onExtraKey = { viewModel.onKeypadInput('-') },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** First question, as on fast-food kiosks: most customers answer "No, gracias" (Consumidor Final). */
@Composable
private fun InvoiceQuestion(
    onNoThanks: () -> Unit,
    onYes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(180.dp).background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = colors.primary, modifier = Modifier.size(100.dp))
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.customer_question),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.customer_question_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(56.dp))
        Row(
            modifier = Modifier.centeredMaxWidth(QuestionButtonsMaxWidth),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            KioskButton(
                text = stringResource(R.string.customer_no_thanks),
                onClick = onNoThanks,
                style = KioskButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            KioskButton(
                text = stringResource(R.string.customer_yes),
                onClick = onYes,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val QuestionButtonsMaxWidth = 900.dp

@Composable
private fun BottomActionBar(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(0.dp),
        border = if (LocalHighContrast.current) BorderStroke(3.dp, colors.onSurface) else null,
    ) {
        Box(modifier = Modifier.centeredMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
            content()
        }
    }
}

@Composable
private fun CustomerOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val disc = if (isSelected) KioskColors.ctaBrush else SolidColor(colors.secondaryContainer)
    val discSize by animateDpAsState(if (compact) 96.dp else 136.dp, label = "option_disc")
    KioskCard(modifier = modifier, selected = isSelected, onClick = onClick) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 140.dp else 220.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(discSize).background(disc, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.onPrimary else colors.onSecondaryContainer,
                    modifier = Modifier.size(discSize * ICON_RATIO),
                )
            }
            Spacer(modifier = Modifier.width(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun CustomBillingForm(
    uiState: CustomerIdUiState,
    viewModel: CustomerIdViewModel,
    modifier: Modifier = Modifier,
) {
    val isPassport = uiState.docType == DocTypes[PASSPORT_INDEX]
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BillingFields(uiState = uiState, viewModel = viewModel)
        if (!isPassport) {
            NumericKeypad(
                onDigit = viewModel::onKeypadInput,
                onBackspace = viewModel::onKeypadBackspace,
                extraKey = "-",
                onExtraKey = { viewModel.onKeypadInput('-') },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Document type, document number (+ DV), name and the validation message. */
@Composable
private fun BillingFields(
    uiState: CustomerIdUiState,
    viewModel: CustomerIdViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isPassport = uiState.docType == DocTypes[PASSPORT_INDEX]

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        KioskSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.customer_doc_cedula) to Icons.Rounded.Badge,
                    stringResource(R.string.customer_doc_ruc) to Icons.Rounded.Business,
                    stringResource(R.string.customer_doc_passport) to Icons.Rounded.Flight,
                ),
            selectedIndex = DocTypes.indexOf(uiState.docType).coerceAtLeast(0),
            onSelect = { index -> viewModel.onDocTypeChanged(DocTypes[index]) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (isPassport) {
            OutlinedTextField(
                value = uiState.docNumber,
                onValueChange = viewModel::onDocNumberChanged,
                label = { Text(stringResource(R.string.customer_doc_id)) },
                textStyle = MaterialTheme.typography.headlineMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = MaterialTheme.shapes.medium,
                colors = kioskFieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KeypadField(
                    label = stringResource(R.string.customer_doc_id),
                    value = uiState.docNumber,
                    active = uiState.activeField == CustomerField.DOC_NUMBER,
                    onClick = { viewModel.onActiveFieldChanged(CustomerField.DOC_NUMBER) },
                    modifier = Modifier.weight(1f),
                )
                if (uiState.hasDv) {
                    KeypadField(
                        label = stringResource(R.string.customer_dv),
                        value = uiState.dv,
                        active = uiState.activeField == CustomerField.DV,
                        onClick = { viewModel.onActiveFieldChanged(CustomerField.DV) },
                        modifier = Modifier.width(200.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text(stringResource(R.string.customer_name), style = MaterialTheme.typography.labelMedium) },
            textStyle = MaterialTheme.typography.headlineMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = MaterialTheme.shapes.medium,
            colors = kioskFieldColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )

        if (uiState.errorMessage != null) {
            Text(
                text =
                    stringResource(
                        if (uiState.docNumber.isBlank()) R.string.customer_error_doc else R.string.customer_error_name,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun KeypadField(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(if (active) colors.primary else colors.outline, label = "keypad_field_border")
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier =
            modifier
                .heightIn(min = 120.dp)
                .kioskPressable(pressedScale = 0.98f, onClick = onClick)
                .background(colors.surface, shape)
                .border(if (active) 5.dp else 2.dp, borderColor, shape)
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = if (active) colors.primary else colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (active) "$value|" else value.ifEmpty { "—" },
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Text fields styled like the keypad fields: white, brand outline when focused. */
@Composable
private fun kioskFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
    )
