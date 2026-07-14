package com.amaxonia.pos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.FormSection
import com.amaxonia.pos.ui.common.components.PosDropdown
import com.amaxonia.pos.ui.common.components.PosMoneyInput
import com.amaxonia.pos.ui.common.components.PosTextInput
import com.amaxonia.pos.ui.common.injectedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    viewModel: ProductFormViewModel = injectedViewModel { ProductFormViewModel(DependencyContainer.productRepository) },
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val product = state.product
    val departmentNames = state.departments.map { it.name }
    val sectionNames = state.sections.map { it.name }
    val familyNames = state.families.map { it.name }
    val subFamilyNames = state.subFamilies.map { it.name }
    val brandNames = state.brands.map { it.name }
    val lineNames = state.lines.map { it.name }

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Editar Producto" else "Nuevo Producto", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.saveProduct(onSaveSuccess) }) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
        ) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = state.error ?: "Error desconocido",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            FormSection(title = "Datos Generales") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosTextInput("Código", product.code, Modifier.weight(1f)) { viewModel.updateField { copy(code = it) } }
                    PosTextInput("Referencia", product.reference, Modifier.weight(1f)) { viewModel.updateField { copy(reference = it) } }
                }
                PosTextInput("Descripción", product.description) { viewModel.updateField { copy(description = it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosTextInput("Cod. Barras 1", product.barcode1, Modifier.weight(1f)) { viewModel.updateField { copy(barcode1 = it) } }
                    PosTextInput("Cod. Barras 2", product.barcode2, Modifier.weight(1f)) { viewModel.updateField { copy(barcode2 = it) } }
                }
                PosTextInput("Cod. Barras 3", product.barcode3) { viewModel.updateField { copy(barcode3 = it) } }
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text("Seleccionar Foto", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FormSection(title = "Categorización") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        PosDropdown(
                            "Departamento",
                            departmentNames,
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
                            sectionNames,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        PosDropdown(
                            "Familia",
                            familyNames,
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
                            "Sub Familia",
                            subFamilyNames,
                            selectedOption = selectedName(state.subFamilies, product.subFamily),
                            enabled = product.family.isNotBlank(),
                        ) { selected ->
                            state.subFamilies.firstOrNull { it.name == selected }?.id?.let {
                                viewModel.updateField { copy(subFamily = it.toString()) }
                            }
                        }
                    }
                }
                PosDropdown(
                    "Marca",
                    brandNames,
                    selectedOption = selectedName(state.brands, product.brand),
                ) { selected ->
                    state.brands
                        .firstOrNull { it.name == selected }
                        ?.id
                        ?.let(viewModel::onBrandChanged)
                }
                PosDropdown(
                    "Línea",
                    lineNames,
                    selectedOption = selectedName(state.lines, product.line),
                    enabled = product.brand.isNotBlank(),
                ) { selected ->
                    state.lines
                        .firstOrNull { it.name == selected }
                        ?.id
                        ?.let(viewModel::onLineChanged)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FormSection(title = "Costos e Impuestos") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(0.4f)) {
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
                    Box(Modifier.weight(0.4f)) {
                        PosMoneyInput("IVA %", product.taxRate, showZero = true) {
                            viewModel.updateField { copy(taxRate = if (isExempt) 0.0 else it) }
                            viewModel.recalculateAllPrices()
                        }
                    }
                    IconButton(
                        onClick = { viewModel.recalculateAllPrices() },
                        modifier = Modifier.padding(top = 18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Calculate, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Costos", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosMoneyInput("Comisión %", product.commissionPercent, Modifier.weight(1f), showZero = true) {
                        viewModel.updateField { copy(commissionPercent = it) }
                    }
                    PosMoneyInput(
                        "Costo Franco",
                        product.costFranco,
                        Modifier.weight(1f),
                        showZero = true,
                    ) { viewModel.updateField { copy(costFranco = it) } }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Precios por Unidad",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("", Modifier.width(24.dp))
                        Text("Precio", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Util %", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("P. + Util", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("P. + Imp", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider()
                    product.prices.forEachIndexed { index, priceRow ->
                        PriceRowItem(
                            label = priceRow.label,
                            price = priceRow.price,
                            utility = priceRow.utilityPercent,
                            pricePlusUtility = priceRow.pricePlusUtility,
                            total = priceRow.pricePlusTax,
                            onUtilityChange = {
                                val value =
                                    it.toDoubleOrNull() ?: 0.0
                                viewModel.updatePriceRow(index) { copy(utilityPercent = value) }
                            },
                        )
                        if (index <
                            product.prices.lastIndex
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

private fun selectedName(
    options: List<Department>,
    selectedId: String,
): String {
    val id = selectedId.toIntOrNull() ?: return ""
    return options.firstOrNull { it.id == id }?.name ?: ""
}

@Composable
fun PriceRowItem(
    label: String,
    price: Double,
    utility: Double,
    pricePlusUtility: Double,
    total: Double,
    onUtilityChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
            fontSize = 18.sp,
        )
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.2f", price),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )
        CompactNumericInput(
            value = utility.toString(),
            onValueChange = onUtilityChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.2f", pricePlusUtility),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.2f", total),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
        )
    }
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
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 13.sp),
    )
}
