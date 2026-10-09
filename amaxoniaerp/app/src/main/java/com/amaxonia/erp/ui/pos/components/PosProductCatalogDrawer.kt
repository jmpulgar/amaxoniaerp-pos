package com.amaxonia.erp.ui.pos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowTableBorder
import com.amaxonia.erp.ui.theme.FlowTableTextDark
import java.util.Locale

@Composable
fun PosProductCatalogDrawer(
    isOpen: Boolean,
    products: List<Product>,
    departments: List<DepartmentDto>,
    selectedDepartmentId: Int?,
    searchQuery: String,
    isLoading: Boolean = false,
    currentPage: Int = 1,
    totalPages: Int = 1,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onOpenOptions: (Product) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(6.dp),
        ) {
            // --- 1. Cabecera del Catálogo: Título (24dp) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FlowBrandBlue)
                )
                Text(
                    text = "Catálogo de Productos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowTableTextDark,
                )
                Text(
                    text = "(${products.size})",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B),
                )
            }

            // --- 2. Buscador de Productos Compacto (30dp) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar en catálogo...",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                        ),
                        cursorBrush = SolidColor(FlowBrandBlue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            // --- 3. Carrusel de Categorías / Departamentos (26dp) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedDepartmentId == null,
                    onClick = { onDepartmentSelected(null) },
                    label = { Text("Todos", fontSize = 10.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FlowBrandBlue,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF1F5F9),
                        labelColor = Color(0xFF334155),
                    ),
                )

                departments.forEach { dept ->
                    FilterChip(
                        selected = selectedDepartmentId == dept.id,
                        onClick = { onDepartmentSelected(dept.id) },
                        label = { Text(dept.name, fontSize = 10.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(24.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FlowBrandBlue,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = Color(0xFF334155),
                        ),
                    )
                }
            }

            // --- 4. Grid de Productos de Alta Densidad (Adaptive 88dp) ---
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            color = FlowBrandBlue,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Cargando productos...",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                        )
                    }
                }
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No se encontraron productos",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductGridCard(
                            product = product,
                            onClick = { onAddToCart(product) },
                            onOpenOptions = { onOpenOptions(product) },
                        )
                    }
                }
            }

            // --- 5. Barra de Paginación en Píldora (estilo modal) ---
            if (totalPages > 1 || currentPage > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        color = Color.White,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            IconButton(
                                onClick = onPrevPage,
                                enabled = currentPage > 1 && !isLoading,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Página previa",
                                    tint = if (currentPage > 1) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(13.dp),
                                )
                            }

                            Text(
                                text = "Página $currentPage de $totalPages",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )

                            IconButton(
                                onClick = onNextPage,
                                enabled = currentPage < totalPages && !isLoading,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Página siguiente",
                                    tint = if (currentPage < totalPages) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGridCard(
    product: Product,
    onClick: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, FlowTableBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            // Imagen del Producto Compacta (52dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(4.dp))
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
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Nombre / Descripción del Producto (2 líneas, 10.5sp)
            Text(
                text = product.description,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = FlowTableTextDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.height(26.dp),
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Fila Inferior: Botón Opciones + Precio Destacado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(3.dp))
                        .clickable { onOpenOptions() }
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Opciones", tint = Color(0xFF64748B), modifier = Modifier.size(9.dp))
                        Text(text = "Opc", fontSize = 8.5.sp, color = Color(0xFF64748B))
                    }
                }

                Text(
                    text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowBrandBlue,
                )
            }
        }
    }
}
