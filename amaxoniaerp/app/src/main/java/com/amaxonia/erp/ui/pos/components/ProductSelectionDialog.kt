package com.amaxonia.erp.ui.pos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private enum class ProductModalViewMode {
    GRID,
    TABLE,
}

@Composable
fun ProductSelectionDialog(
    initialQuery: String,
    products: List<Product>,
    departments: List<DepartmentDto>,
    isLoading: Boolean = false,
    onAddToCart: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedDeptId by remember { mutableStateOf<Int?>(null) }
    var selectedTypeFilter by remember { mutableStateOf("TODOS") } // TODOS, PRODUCTO, SERVICIO
    var showAdvancedSearch by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ProductModalViewMode.GRID) }
    var currentPage by remember { mutableIntStateOf(1) }
    var selectedProductForAccept by remember { mutableStateOf<Product?>(null) }

    val pageSize = 12 // Exactamente igual a limit: 12 en Web POS (producto/listado/main.js)

    // Filtrado de productos
    val filteredProducts = remember(searchQuery, selectedDeptId, selectedTypeFilter, products) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        products.filter { p ->
            val matchesQuery = query.isEmpty() ||
                p.description.lowercase(Locale.ROOT).contains(query) ||
                p.code.lowercase(Locale.ROOT).contains(query) ||
                p.barcode1.lowercase(Locale.ROOT).contains(query) ||
                p.barcode2.lowercase(Locale.ROOT).contains(query) ||
                p.reference.lowercase(Locale.ROOT).contains(query) ||
                p.department.lowercase(Locale.ROOT).contains(query)

            val matchesDept = selectedDeptId == null ||
                p.department == selectedDeptId.toString() ||
                p.department.toIntOrNull() == selectedDeptId ||
                departments.firstOrNull { it.id == selectedDeptId }?.let {
                    it.displayName.equals(p.department, ignoreCase = true) ||
                    it.name.equals(p.department, ignoreCase = true)
                } == true

            val matchesType = when (selectedTypeFilter) {
                "PRODUCTO" -> !p.isService
                "SERVICIO" -> p.isService
                else -> true
            }

            matchesQuery && matchesDept && matchesType
        }
    }

    // Resetear página si cambia el filtro
    LaunchedEffect(filteredProducts.size) {
        currentPage = 1
    }

    val totalPages = max(1, ceil(filteredProducts.size.toDouble() / pageSize.toDouble()).toInt())
    val paginatedProducts = remember(filteredProducts, currentPage) {
        val start = (currentPage - 1) * pageSize
        filteredProducts.drop(start).take(pageSize)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val isLandscape = isLandscape()
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.88f else 0.98f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.96f)
                .clip(RoundedCornerShape(8.dp)),
            color = Color.White,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                // ==========================================
                // 1. BARRA DE BÚSQUEDA 1:1 WEB (producto-buscar)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .border(2.dp, Color(0xFF00C5DC), RoundedCornerShape(4.dp))
                        .background(Color.White),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Input de búsqueda
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar Productos / Servicios",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color(0xFF1E293B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(Color(0xFF00C5DC)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .forceShowKeyboardOnTouch(),
                        )
                    }

                    // Botonera de acciones unificada estilo Web Input Group
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Botón Buscar (Lupa)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF00C5DC))
                                .clickable { /* Trigger refresh if needed */ },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Botón Limpiar (Backspace)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF00C5DC))
                                .clickable { searchQuery = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Limpiar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Botón Búsqueda Avanzada (Toggle)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(if (showAdvancedSearch) Color(0xFFFFF59D) else Color(0xFF00C5DC))
                                .clickable { showAdvancedSearch = !showAdvancedSearch },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Búsqueda avanzada",
                                tint = if (showAdvancedSearch) Color(0xFF424242) else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Selector de Vista Grid (Cuadrícula)
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight()
                                .background(if (viewMode == ProductModalViewMode.GRID) Color(0xFF0097A7) else Color(0xFF00C5DC))
                                .clickable { viewMode = ProductModalViewMode.GRID },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Vista Cuadrícula",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Selector de Vista Tabla (Lista)
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight()
                                .background(if (viewMode == ProductModalViewMode.TABLE) Color(0xFF0097A7) else Color(0xFF00C5DC))
                                .clickable { viewMode = ProductModalViewMode.TABLE },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Vista Tabla",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Botón Aceptar (Verde #2ECC71)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF28A745))
                                .clickable {
                                    val target = selectedProductForAccept ?: paginatedProducts.firstOrNull()
                                    if (target != null) {
                                        onAddToCart(target)
                                        onDismiss()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aceptar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Botón Cerrar (Rojo #DC3545)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(Color(0xFFDC3545))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // =======================================================
                // 2. PANEL DE BÚSQUEDA AVANZADA 1:1 WEB (#container-busqueda-avanzada)
                // =======================================================
                AnimatedVisibility(
                    visible = showAdvancedSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFF176), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(Color(0xFFFFF9C4))
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "Parámetros de Busqueda",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Filtro de Tipo: Todos, Productos, Servicios
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "Tipo:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                            listOf("TODOS", "PRODUCTO", "SERVICIO").forEach { tipo ->
                                FilterChip(
                                    selected = selectedTypeFilter == tipo,
                                    onClick = { selectedTypeFilter = tipo },
                                    label = { Text(tipo, fontSize = 10.sp) },
                                    modifier = Modifier.height(26.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00C5DC),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF424242),
                                    ),
                                )
                            }
                        }

                        // Filtro de Departamentos
                        if (departments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = "Depto:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                                FilterChip(
                                    selected = selectedDeptId == null,
                                    onClick = { selectedDeptId = null },
                                    label = { Text("TODOS", fontSize = 10.sp) },
                                    modifier = Modifier.height(26.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00C5DC),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF424242),
                                    ),
                                )
                                departments.forEach { dept ->
                                    FilterChip(
                                        selected = selectedDeptId == dept.id,
                                        onClick = {
                                            selectedDeptId = if (selectedDeptId == dept.id) null else dept.id
                                        },
                                        label = { Text(dept.displayName.uppercase(), fontSize = 10.sp) },
                                        modifier = Modifier.height(26.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF00C5DC),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White,
                                            labelColor = Color(0xFF424242),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 3. CUERPO: LISTADO DE PRODUCTOS (#producto-listado)
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (isLoading) {
                        // Estado de Carga Oficial Web: amaxonia.loading({ message: "Cargando..." })
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF00BCD4),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    text = "Cargando...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else if (filteredProducts.isEmpty()) {
                        // Estado Sin Resultados
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No se encontraron productos para \"$searchQuery\"" else "No hay productos disponibles",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (viewMode == ProductModalViewMode.GRID) {
                        // ==========================================
                        // VISTA CUADRÍCULA 1:1 WEB (.producto-item)
                        // ==========================================
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columnsCount = when {
                                maxWidth >= 840.dp -> 6
                                maxWidth >= 540.dp -> 4
                                else -> 3
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnsCount),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(paginatedProducts, key = { it.id.ifBlank { it.code } }) { product ->
                                val isSelected = selectedProductForAccept == product

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProductForAccept = product
                                            onAddToCart(product)
                                            onDismiss()
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFFFFDE7) else Color.White,
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFFEB3B) else Color(0xFFEFEFEF),
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        // Código en esquina superior izquierda
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "#${product.code.ifBlank { product.id }}",
                                                color = Color(0xFF607D8B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                            )
                                            if (product.isService) {
                                                Text(
                                                    text = "SERV",
                                                    color = Color(0xFF0097A7),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Imagen del producto (height 80dp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF8FAFC)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (product.photoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = product.photoUrl,
                                                    contentDescription = product.description,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = Color(0xFFCBD5E1),
                                                    modifier = Modifier.size(26.dp),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Código de barras / Referencia
                                        val barcodeOrRef = product.barcode1.ifBlank { product.reference }
                                        if (barcodeOrRef.isNotBlank()) {
                                            Text(
                                                text = barcodeOrRef,
                                                color = Color(0xFF757575),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                            )
                                        }

                                        // Nombre en mayúsculas (.nombre)
                                        Text(
                                            text = product.description.uppercase(),
                                            color = Color(0xFF212529),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 13.sp,
                                            modifier = Modifier.height(28.dp),
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Precio prominente en cian var(--flx-00bcd4, #00bcd4)
                                        Text(
                                            text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                                            color = Color(0xFF00BCD4),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )

                                        // Stock en gris (.stock)
                                        Text(
                                            text = "Stock: 1.00",
                                            color = Color(0xFF757575),
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                        // ==========================================
                        // VISTA TABLA 1:1 WEB (.vista-tabla)
                        // ==========================================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp)),
                        ) {
                            // Cabecera de la tabla
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("CÓDIGO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.width(65.dp))
                                Text("BARRA / REF", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.width(95.dp))
                                Text("NOMBRE / DESCRIPCIÓN", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.weight(1f))
                                Text("PRECIO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), textAlign = TextAlign.End, modifier = Modifier.width(85.dp))
                                Text("STOCK", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), textAlign = TextAlign.End, modifier = Modifier.width(55.dp))
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Filas con efecto cebra (#F6F6F6 y blanco)
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(paginatedProducts, key = { _, p -> p.id.ifBlank { p.code } }) { index, product ->
                                    val rowBg = if (index % 2 == 0) Color.White else Color(0xFFF6F6F6)
                                    val isSelected = selectedProductForAccept == product

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) Color(0xFFFFFDE7) else rowBg)
                                            .clickable {
                                                selectedProductForAccept = product
                                                onAddToCart(product)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "#${product.code.ifBlank { product.id }}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF607D8B),
                                            modifier = Modifier.width(65.dp),
                                        )
                                        Text(
                                            text = product.barcode1.ifBlank { product.reference }.ifBlank { "-" },
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF757575),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(95.dp),
                                        )
                                        Text(
                                            text = product.description.uppercase(),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF212529),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00BCD4),
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(85.dp),
                                        )
                                        Text(
                                            text = "1.00",
                                            fontSize = 11.sp,
                                            color = Color(0xFF757575),
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(55.dp),
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 4. BARRA DE PAGINACIÓN EN PÍLDORA 1:1 WEB
                // ==========================================
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        color = Color.White,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 1) currentPage-- },
                                enabled = currentPage > 1,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Página previa",
                                    tint = if (currentPage > 1) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            Text(
                                text = "Página $currentPage de $totalPages",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )

                            IconButton(
                                onClick = { if (currentPage < totalPages) currentPage++ },
                                enabled = currentPage < totalPages,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Página siguiente",
                                    tint = if (currentPage < totalPages) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
