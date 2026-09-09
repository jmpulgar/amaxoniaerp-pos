package com.amaxonia.pos.ui.products

/**
 * IMPORTANTE (decisión D1, 2026-09-04): el alta/edición de productos desde el
 * POS quedó DESHABILITADA — el catálogo se administra exclusivamente desde el
 * ERP/web. Esta pantalla quedó fuera del grafo de navegación
 * (AppNavigation.productsDestinations) y no debe re-conectarse sin una
 * decisión de producto que reabra el flujo POS→server.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.FormSection
import com.amaxonia.pos.ui.common.components.PosDropdown
import com.amaxonia.pos.ui.common.components.PosMoneyInput
import com.amaxonia.pos.ui.common.components.PosTextInput
import com.amaxonia.pos.ui.common.injectedViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    viewModel: ProductFormViewModel = injectedViewModel { AppGraph.products.productFormViewModel() },
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val product = state.product

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProductFormTopAppBar(
                state = state,
                onBack = onBack,
                onSave = { viewModel.saveProduct(onSaveSuccess) },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ProductFormError(it) }

            GeneralProductDataSection(product, viewModel)
            ProductCategorizationSection(state, viewModel)
            CostsAndTaxesSection(product, viewModel)

            PriceTable(
                prices = product.prices,
                onUtilityChange = { index, value ->
                    viewModel.updatePriceRow(index) { copy(utilityPercent = value.toDoubleOrNull() ?: 0.0) }
                },
            )

            SaveProductButton(
                isEditMode = state.isEditMode,
                isSaving = state.isSaving,
                onClick = { viewModel.saveProduct(onSaveSuccess) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormTopAppBar(
    state: ProductFormState,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = if (state.isEditMode) "Editar producto" else "Nuevo producto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(48.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Guardar producto",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Códigos, descripción, barras y foto del producto. */
@Composable
private fun GeneralProductDataSection(
    product: Product,
    viewModel: ProductFormViewModel,
) {
    FormSection(title = "Datos generales") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextInput("Código", product.code, Modifier.weight(1f)) {
                viewModel.updateField { copy(code = it) }
            }
            PosTextInput("Referencia", product.reference, Modifier.weight(1f)) {
                viewModel.updateField { copy(reference = it) }
            }
        }
        PosTextInput("Descripción", product.description) {
            viewModel.updateField { copy(description = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextInput("Cód. barras 1", product.barcode1, Modifier.weight(1f)) {
                viewModel.updateField { copy(barcode1 = it) }
            }
            PosTextInput("Cód. barras 2", product.barcode2, Modifier.weight(1f)) {
                viewModel.updateField { copy(barcode2 = it) }
            }
        }
        PosTextInput("Cód. barras 3", product.barcode3) {
            viewModel.updateField { copy(barcode3 = it) }
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = "Seleccionar foto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Jerarquía comercial: departamento, sección, familia, subfamilia, marca y línea. */
@Composable
private fun ProductCategorizationSection(
    state: ProductFormState,
    viewModel: ProductFormViewModel,
) {
    val product = state.product
    FormSection(title = "Categorización") {
        CategoryFirstRow(state, product, viewModel)
        CategorySecondRow(state, product, viewModel)
        PosDropdown(
            "Marca",
            state.brands.map { it.name },
            selectedOption = selectedName(state.brands, product.brand),
        ) { selected ->
            state.brands
                .firstOrNull { it.name == selected }
                ?.id
                ?.let(viewModel::onBrandChanged)
        }
        PosDropdown(
            "Línea",
            state.lines.map { it.name },
            selectedOption = selectedName(state.lines, product.line),
            enabled = product.brand.isNotBlank(),
        ) { selected ->
            state.lines
                .firstOrNull { it.name == selected }
                ?.id
                ?.let(viewModel::onLineChanged)
        }
    }
}

/** Departamentos y secciones. */
@Composable
private fun CategoryFirstRow(
    state: ProductFormState,
    product: Product,
    viewModel: ProductFormViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            PosDropdown(
                "Departamento",
                state.departments.map { it.name },
                selectedOption = selectedName(state.departments, product.department),
            ) { selected ->
                state.departments
                    .firstOrNull { it.name == selected }
                    ?.id
                    ?.let(viewModel::onDepartmentChanged)
            }
        }
        Box(Modifier.weight(1f)) {
            PosDropdown(
                "Sección",
                state.sections.map { it.name },
                selectedOption = selectedName(state.sections, product.section),
                enabled = product.department.isNotBlank(),
            ) { selected ->
                state.sections
                    .firstOrNull { it.name == selected }
                    ?.id
                    ?.let(viewModel::onSectionChanged)
            }
        }
    }
}

/** Familias y subfamilias. */
@Composable
private fun CategorySecondRow(
    state: ProductFormState,
    product: Product,
    viewModel: ProductFormViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            PosDropdown(
                "Familia",
                state.families.map { it.name },
                selectedOption = selectedName(state.families, product.family),
                enabled = product.section.isNotBlank(),
            ) { selected ->
                state.families
                    .firstOrNull { it.name == selected }
                    ?.id
                    ?.let(viewModel::onFamilyChanged)
            }
        }
        Box(Modifier.weight(1f)) {
            PosDropdown(
                "Subfamilia",
                state.subFamilies.map { it.name },
                selectedOption = selectedName(state.subFamilies, product.subFamily),
                enabled = product.family.isNotBlank(),
            ) { selected ->
                state.subFamilies.firstOrNull { it.name == selected }?.id?.let { id ->
                    viewModel.updateField { copy(subFamily = id.toString()) }
                }
            }
        }
    }
}

/** Impuestos y costos del producto. */
@Composable
private fun CostsAndTaxesSection(
    product: Product,
    viewModel: ProductFormViewModel,
) {
    FormSection(title = "Costos e impuestos") {
        ExemptTaxRow(product, viewModel)
        Text(
            text = "Costos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosMoneyInput("Actual ($)", product.costActual, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(costActual = it) }
                viewModel.recalculateAllPrices()
            }
            PosMoneyInput("Promedio ($)", product.costAverage, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(costAverage = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosMoneyInput("Anterior ($)", product.costPrevious, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(costPrevious = it) }
            }
            PosMoneyInput("Procesado ($)", product.costProcessed, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(costProcessed = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosMoneyInput("Comisión %", product.commissionPercent, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(commissionPercent = it) }
            }
            PosMoneyInput("Costo franco", product.costFranco, Modifier.weight(1f), showZero = true) {
                viewModel.updateField { copy(costFranco = it) }
            }
        }
    }
}

/** Selector de exención + IVA y botón de recálculo. */
@Composable
private fun ExemptTaxRow(
    product: Product,
    viewModel: ProductFormViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            PosDropdown("Exento", listOf("SI", "NO"), if (product.isExempt) "SI" else "NO") {
                val isExempt = it == "SI"
                viewModel.updateField {
                    copy(
                        isExempt = isExempt,
                        taxRate = if (isExempt) 0.0 else taxRate,
                    )
                }
                viewModel.recalculateAllPrices()
            }
        }
        Box(Modifier.weight(1f)) {
            PosMoneyInput("IVA %", product.taxRate, showZero = true) {
                viewModel.updateField { copy(taxRate = if (isExempt) 0.0 else it) }
                viewModel.recalculateAllPrices()
            }
        }
        IconButton(
            onClick = viewModel::recalculateAllPrices,
            modifier =
                Modifier
                    .padding(top = 18.dp)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = "Recalcular precios",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SaveProductButton(
    isEditMode: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (isEditMode) "Guardar cambios" else "Guardar producto",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProductFormError(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PriceTable(
    prices: List<PriceLevel>,
    onUtilityChange: (Int, String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Precios por unidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            PriceHeader()
            HorizontalDivider()
            prices.forEachIndexed { index, priceRow ->
                PriceRowItem(
                    values =
                        PriceRowValues(
                            label = priceRow.label,
                            price = priceRow.price,
                            utility = priceRow.utilityPercent,
                            pricePlusUtility = priceRow.pricePlusUtility,
                            total = priceRow.pricePlusTax,
                        ),
                    onUtilityChange = { onUtilityChange(index, it) },
                )
                if (index < prices.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun PriceHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("", Modifier.width(24.dp))
        PriceHeaderText("Precio", Modifier.weight(1f))
        PriceHeaderText("Util %", Modifier.weight(1f))
        PriceHeaderText("P. + util", Modifier.weight(1f))
        PriceHeaderText("P. + imp", Modifier.weight(1f))
    }
}

@Composable
private fun PriceHeaderText(
    text: String,
    modifier: Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun selectedName(
    options: List<Department>,
    selectedId: String,
): String {
    val id = selectedId.toIntOrNull() ?: return ""
    return options.firstOrNull { it.id == id }?.name.orEmpty()
}

/** Valores mostrados en una fila de la tabla de precios por nivel. */
internal data class PriceRowValues(
    val label: String,
    val price: Double,
    val utility: Double,
    val pricePlusUtility: Double,
    val total: Double,
)

@Composable
internal fun PriceRowItem(
    values: PriceRowValues,
    onUtilityChange: (String) -> Unit,
) {
    val label = values.label
    val price = values.price
    val utility = values.utility
    val pricePlusUtility = values.pricePlusUtility
    val total = values.total
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        PriceAmount(price, Modifier.weight(1f))
        CompactNumericInput(
            value = utility.toString(),
            onValueChange = onUtilityChange,
            modifier = Modifier.weight(1f),
        )
        PriceAmount(pricePlusUtility, Modifier.weight(1f))
        PriceAmount(total, Modifier.weight(1f), emphasized = true)
    }
}

@Composable
private fun PriceAmount(
    value: Double,
    modifier: Modifier,
    emphasized: Boolean = false,
) {
    AdaptiveAmountText(
        text = String.format(Locale.getDefault(), "%.2f", value),
        modifier = modifier,
        baseStyle =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        options =
            com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                minFontSizeSp = 9f,
            ),
    )
}

@Composable
fun CompactNumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
    )
}
