package com.amaxonia.pos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AmaxoniaDropdown
import com.amaxonia.pos.ui.common.components.AmaxoniaInput
import com.amaxonia.pos.ui.common.components.AmaxoniaMoneyInput
import com.amaxonia.pos.ui.common.components.FormSection
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.BgLightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: String?,
    viewModel: ProductFormViewModel = injectedViewModel { ProductFormViewModel(DependencyContainer.productRepository) },
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val product = state.product

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        containerColor = BgLightGray,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Editar Producto" else "Nuevo Producto", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.saveProduct(onSaveSuccess) }) {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Icon(Icons.Default.Save, null, tint = AmaxoniaBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLightGray)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.error ?: "Error desconocido",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            FormSection(title = "Datos Generales") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmaxoniaInput("Código", product.code, Modifier.weight(1f)) { viewModel.updateField { copy(code = it) } }
                    AmaxoniaInput("Referencia", product.reference, Modifier.weight(1f)) { viewModel.updateField { copy(reference = it) } }
                }
                AmaxoniaInput("Descripción", product.description) { viewModel.updateField { copy(description = it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmaxoniaInput("Cod. Barras 1", product.barcode1, Modifier.weight(1f)) { viewModel.updateField { copy(barcode1 = it) } }
                    AmaxoniaInput("Cod. Barras 2", product.barcode2, Modifier.weight(1f)) { viewModel.updateField { copy(barcode2 = it) } }
                }
                AmaxoniaInput("Cod. Barras 3", product.barcode3) { viewModel.updateField { copy(barcode3 = it) } }
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                ) {
                    Text("Seleccionar Foto", color = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FormSection(title = "Categorización") {
                val mockOptions = listOf("Opción 1", "Opción 2")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        AmaxoniaDropdown("Departamento", mockOptions, product.department) { viewModel.updateField { copy(department = it) } }
                    }
                    Box(Modifier.weight(1f)) {
                        AmaxoniaDropdown("Sección", mockOptions, product.section) { viewModel.updateField { copy(section = it) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        AmaxoniaDropdown("Familia", mockOptions, product.family) { viewModel.updateField { copy(family = it) } }
                    }
                    Box(Modifier.weight(1f)) {
                        AmaxoniaDropdown("Sub Familia", mockOptions, product.subFamily) { viewModel.updateField { copy(subFamily = it) } }
                    }
                }
                AmaxoniaDropdown("Marca", mockOptions, product.brand) { viewModel.updateField { copy(brand = it) } }
                AmaxoniaDropdown("Línea", mockOptions, product.line) { viewModel.updateField { copy(line = it) } }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FormSection(title = "Costos e Impuestos") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(0.4f)) {
                        AmaxoniaDropdown("Exento", listOf("SI", "NO"), if (product.isExempt) "SI" else "NO") {
                            viewModel.updateField { copy(isExempt = it == "SI") }
                        }
                    }
                    Box(Modifier.weight(0.4f)) {
                        AmaxoniaMoneyInput("ITBMS %", product.taxRate) { viewModel.updateField { copy(taxRate = it) } }
                    }
                    IconButton(
                        onClick = { viewModel.recalculateAllPrices() },
                        modifier = Modifier.padding(top = 18.dp).background(AmaxoniaBlue, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Calculate, null, tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Costos", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AmaxoniaBlue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmaxoniaMoneyInput("Actual ($)", product.costActual, Modifier.weight(1f)) { viewModel.updateField { copy(costActual = it) } }
                    AmaxoniaMoneyInput("Promedio ($)", product.costAverage, Modifier.weight(1f)) { viewModel.updateField { copy(costAverage = it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmaxoniaMoneyInput("Anterior ($)", product.costPrevious, Modifier.weight(1f)) { viewModel.updateField { copy(costPrevious = it) } }
                    AmaxoniaMoneyInput("Procesado ($)", product.costProcessed, Modifier.weight(1f)) { viewModel.updateField { copy(costProcessed = it) } }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmaxoniaMoneyInput("Comisión %", product.commissionPercent, Modifier.weight(1f)) { viewModel.updateField { copy(commissionPercent = it) } }
                    AmaxoniaMoneyInput("Costo Franco", product.costFranco, Modifier.weight(1f)) { viewModel.updateField { copy(costFranco = it) } }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Precios por Unidad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AmaxoniaBlue, modifier = Modifier.padding(8.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("", Modifier.width(24.dp))
                        Text("Util %", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("P. + Util", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Total", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider()
                    product.prices.forEachIndexed { index, priceRow ->
                        PriceRowItem(
                            label = priceRow.label,
                            utility = priceRow.utilityPercent,
                            pricePlusUtility = priceRow.pricePlusUtility,
                            total = priceRow.pricePlusTax,
                            onUtilityChange = { val value = it.toDoubleOrNull() ?: 0.0; viewModel.updatePriceRow(index) { copy(utilityPercent = value) } },
                        )
                        if (index < product.prices.lastIndex) HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun PriceRowItem(
    label: String,
    utility: Double,
    pricePlusUtility: Double,
    total: Double,
    onUtilityChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = AmaxoniaBlue, modifier = Modifier.width(24.dp), fontSize = 18.sp)
        CompactNumericInput(
            value = utility.toString(),
            onValueChange = onUtilityChange,
            modifier = Modifier.weight(1f)
        )
        Text(text = String.format("%.2f", pricePlusUtility), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
        Text(text = String.format("%.2f", total), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = AmaxoniaBlue, fontSize = 13.sp)
    }
}

@Composable
fun CompactNumericInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(BgLightGray, RoundedCornerShape(4.dp))
            .padding(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 13.sp)
    )
}
