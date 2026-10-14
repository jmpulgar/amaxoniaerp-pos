package com.amaxonia.erp.ui.pos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, FlowCatalogTokens.Line, RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
        ) {
            // --- 1. Cabecera del Catálogo Flow ERP ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FlowCatalogTokens.Brand),
                )
                Text(
                    text = "Catálogo de Productos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowCatalogTokens.Ink,
                )
                Text(
                    text = "(${products.size})",
                    fontSize = 11.sp,
                    color = FlowCatalogTokens.Text2,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- 2. Buscador (.rest-catalogo__buscar: 40dp, fondo #F4F5F8, radio 10dp) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlowCatalogTokens.Canvas)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = FlowCatalogTokens.Text3,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar en catálogo...",
                            color = FlowCatalogTokens.Text3,
                            fontSize = 12.5.sp,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = FlowCatalogTokens.Ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(FlowCatalogTokens.Brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            tint = FlowCatalogTokens.Text2,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            // --- 3. Carrusel de Categorías (.rest-cat con borde lateral de 4dp y paleta de 6 colores) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Chip "Todos"
                RestCatChip(
                    text = "Todos",
                    isSelected = selectedDepartmentId == null,
                    color = FlowCatalogTokens.ColorTodos,
                    onClick = { onDepartmentSelected(null) },
                )

                // Chips dinámicos por departamento visible
                departments.forEach { dept ->
                    val color = FlowCatalogTokens.getColorForDepartment(dept.id, departments)
                    RestCatChip(
                        text = dept.displayName,
                        isSelected = selectedDepartmentId == dept.id,
                        color = color,
                        onClick = { onDepartmentSelected(dept.id) },
                    )
                }
            }

            // --- 4. Grid de Productos (.rest-prod: tarjeta con cabecera de color suave + letra marca de agua) ---
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
                            color = FlowCatalogTokens.Brand,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Cargando productos...",
                            fontSize = 11.5.sp,
                            color = FlowCatalogTokens.Text2,
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
                        text = "Sin productos para mostrar.",
                        fontSize = 12.sp,
                        color = FlowCatalogTokens.Text3,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(products, key = { it.id }) { product ->
                        val catColor = FlowCatalogTokens.getColorForProduct(product.department, departments)
                        ProductGridCard(
                            product = product,
                            catColor = catColor,
                            onClick = { onAddToCart(product) },
                        )
                    }
                }
            }

            // --- 5. Barra de Paginación en Píldora ---
            if (totalPages > 1 || currentPage > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, FlowCatalogTokens.Line),
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
                                    tint = if (currentPage > 1) FlowCatalogTokens.Ink else FlowCatalogTokens.Text3,
                                    modifier = Modifier.size(13.dp),
                                )
                            }

                            Text(
                                text = "Página $currentPage de $totalPages",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlowCatalogTokens.Text2,
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
                                    tint = if (currentPage < totalPages) FlowCatalogTokens.Ink else FlowCatalogTokens.Text3,
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

/**
 * Botón de categoría (.rest-cat de POS/main.css):
 * - Borde 1dp solid #E4E9F0, con barra lateral izquierda de 4dp del color de la categoría.
 * - Radio: 10dp.
 * - Inactivo: Fondo blanco, texto Ink (#0F0C3D).
 * - Activo: Fondo sólido de la categoría, texto blanco.
 */
@Composable
private fun RestCatChip(
    text: String,
    isSelected: Boolean,
    color: FlowCatalogTokens.CategoryColor,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(color.accent)
                        .border(1.dp, color.accent, shape)
                } else {
                    Modifier
                        .background(Color.White)
                        .border(1.dp, FlowCatalogTokens.Line, shape)
                },
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Barra indicadora izquierda de 4dp cuando no está seleccionada
            if (!isSelected) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.accent),
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else FlowCatalogTokens.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProductCardTag(product: Product) {
    when {
        product.hasOptions -> {
            // .fcp-tarjeta-tag de flow-app.css: bg #EEF5FF, color #201B82, radius 999px, font 10.5sp 800
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFEEF5FF))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF201B82),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "Opciones",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF201B82),
                        maxLines = 1,
                    )
                }
            }
        }
        product.isComboArticle -> {
            // .fcp-tarjeta-tag--combo de flow-app.css: bg #FFF8EB, color #92400E
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFF8EB))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Combo",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF92400E),
                    maxLines = 1,
                )
            }
        }
        product.hasVariants -> {
            // .fcp-tarjeta-tag--variantes de flow-app.css: bg #F4F0FD, color #6D28D9
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFF4F0FD))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${product.variantsTotal} variantes",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6D28D9),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Tarjeta de producto (.rest-prod de POS/main.css):
 * - Borde 1dp #E4E9F0, radio 12dp, fondo blanco.
 * - Cabecera de 64dp teñida con el fondo suave de la categoría (--c-suave).
 * - Letra inicial grande como marca de agua en el centro con opacidad .55.
 * - Si tiene imagen, se monta sobre el fondo.
 * - Nombre de producto con máximo 2 líneas en color Ink.
 * - Precio en color de acento de la categoría (--c) con font-weight 800.
 */
@Composable
private fun ProductGridCard(
    product: Product,
    catColor: FlowCatalogTokens.CategoryColor,
    onClick: () -> Unit,
) {
    val initialLetter = product.description.trim().firstOrNull()?.uppercase() ?: "P"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FlowCatalogTokens.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Imagen del Producto (.rest-prod__img: 64dp, fondo suave + marca de agua)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(catColor.softBg),
                contentAlignment = Alignment.Center,
            ) {
                // Letra inicial centrada (marca de agua en color categoría)
                Text(
                    text = initialLetter,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = catColor.accent.copy(alpha = 0.55f),
                )

                if (product.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = product.photoUrl,
                        contentDescription = product.description,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                // Nombre / Descripción del Producto (.rest-prod__nombre: 2 líneas, 11.5sp, peso 600)
                Text(
                    text = product.description,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FlowCatalogTokens.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    modifier = Modifier.height(28.dp),
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Fila Inferior: Tag Informativo (si aplica) + Precio (.rest-prod__precio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductCardTag(product = product)

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = catColor.accent,
                    )
                }
            }
        }
    }
}
