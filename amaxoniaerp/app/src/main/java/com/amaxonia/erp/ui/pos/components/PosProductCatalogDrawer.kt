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
import androidx.compose.material.icons.filled.Fastfood
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.ui.components.isLargeScreen
import com.amaxonia.erp.ui.components.isExtraLargeScreen
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
    val isLarge = isLargeScreen()
    val isExtraLarge = isExtraLargeScreen()

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
                .padding(if (isLarge) 10.dp else 8.dp),
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
                        .size(if (isLarge) 10.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FlowCatalogTokens.Brand),
                )
                Text(
                    text = "Catálogo de Productos",
                    fontSize = if (isLarge) 15.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowCatalogTokens.Ink,
                )
                Text(
                    text = "(${products.size})",
                    fontSize = if (isLarge) 12.5.sp else 11.sp,
                    color = FlowCatalogTokens.Text2,
                )
            }

            Spacer(modifier = Modifier.height(if (isLarge) 6.dp else 4.dp))

            // --- 2. Buscador (.rest-catalogo__buscar: 42-44dp en pantallas grandes) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLarge) 44.dp else 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlowCatalogTokens.Canvas)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = FlowCatalogTokens.Text3,
                    modifier = Modifier.size(if (isLarge) 20.dp else 17.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar en catálogo...",
                            color = FlowCatalogTokens.Text3,
                            fontSize = if (isLarge) 13.5.sp else 12.5.sp,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = FlowCatalogTokens.Ink,
                            fontSize = if (isLarge) 13.5.sp else 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(FlowCatalogTokens.Brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(if (isLarge) 22.dp else 18.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            tint = FlowCatalogTokens.Text2,
                            modifier = Modifier.size(if (isLarge) 15.dp else 13.dp),
                        )
                    }
                }
            }

            // --- 3. Carrusel de Categorías (.rest-cat con borde lateral de 4dp y paleta de colores) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = if (isLarge) 8.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Chip "Todos"
                RestCatChip(
                    text = "Todos",
                    isSelected = selectedDepartmentId == null,
                    color = FlowCatalogTokens.ColorTodos,
                    isLarge = isLarge,
                    onClick = { onDepartmentSelected(null) },
                )

                // Chips dinámicos por departamento visible
                departments.forEach { dept ->
                    val color = FlowCatalogTokens.getColorForDepartment(dept.id, departments)
                    RestCatChip(
                        text = dept.displayName,
                        isSelected = selectedDepartmentId == dept.id,
                        color = color,
                        isLarge = isLarge,
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
                            modifier = Modifier.size(if (isLarge) 30.dp else 24.dp),
                        )
                        Text(
                            text = "Cargando productos...",
                            fontSize = if (isLarge) 13.sp else 11.5.sp,
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
                        fontSize = if (isLarge) 13.5.sp else 12.sp,
                        color = FlowCatalogTokens.Text3,
                    )
                }
            } else {
                val productColors = androidx.compose.runtime.remember(products, departments) {
                    products.associate { product ->
                        product.id to FlowCatalogTokens.getColorForProduct(product.department, departments)
                    }
                }
                val cardMinSize = when {
                    isExtraLarge -> 145.dp
                    isLarge -> 130.dp
                    else -> 100.dp
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cardMinSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 6.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 6.dp),
                ) {
                    items(products, key = { it.id }) { product ->
                        val catColor = productColors[product.id] ?: FlowCatalogTokens.ColorTodos
                        ProductGridCard(
                            product = product,
                            catColor = catColor,
                            isLarge = isLarge,
                            onClick = { onAddToCart(product) },
                        )
                    }
                }
            }

            // --- 5. Barra de Paginación en Píldora ---
            if (totalPages > 1 || currentPage > 1) {
                Spacer(modifier = Modifier.height(if (isLarge) 6.dp else 4.dp))
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = if (isLarge) 2.dp else 1.dp),
                        ) {
                            IconButton(
                                onClick = onPrevPage,
                                enabled = currentPage > 1 && !isLoading,
                                modifier = Modifier.size(if (isLarge) 28.dp else 24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Página previa",
                                    tint = if (currentPage > 1) FlowCatalogTokens.Ink else FlowCatalogTokens.Text3,
                                    modifier = Modifier.size(if (isLarge) 16.dp else 13.dp),
                                )
                            }

                            Text(
                                text = "Página $currentPage de $totalPages",
                                fontSize = if (isLarge) 12.sp else 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FlowCatalogTokens.Text2,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )

                            IconButton(
                                onClick = onNextPage,
                                enabled = currentPage < totalPages && !isLoading,
                                modifier = Modifier.size(if (isLarge) 28.dp else 24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Página siguiente",
                                    tint = if (currentPage < totalPages) FlowCatalogTokens.Ink else FlowCatalogTokens.Text3,
                                    modifier = Modifier.size(if (isLarge) 16.dp else 13.dp),
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
    isLarge: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(if (isLarge) 38.dp else 34.dp)
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
            .padding(horizontal = if (isLarge) 12.dp else 10.dp),
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
                        .size(width = 4.dp, height = if (isLarge) 16.dp else 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.accent),
                )
            }
            Text(
                text = text,
                fontSize = if (isLarge) 13.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else FlowCatalogTokens.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProductCardTag(
    product: Product,
    isLarge: Boolean = false,
) {
    val tagTextSize = if (isLarge) 10.5.sp else 9.sp
    val iconSize = if (isLarge) 12.dp else 10.dp
    val paddingValues = if (isLarge) PaddingValues(horizontal = 6.dp, vertical = 2.dp) else PaddingValues(horizontal = 5.dp, vertical = 1.5.dp)

    when {
        product.hasVariants -> {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFF4F0FD),
                border = BorderStroke(0.5.dp, Color(0xFFDDD6FE)),
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = "${product.variantsTotal} var.",
                    fontSize = tagTextSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6D28D9),
                    maxLines = 1,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
        product.isComboArticle -> {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFF8EB),
                border = BorderStroke(0.5.dp, Color(0xFFFDE7B8)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(paddingValues),
                ) {
                    Icon(
                        imageVector = Icons.Default.Fastfood,
                        contentDescription = null,
                        tint = Color(0xFF92400E),
                        modifier = Modifier.size(iconSize),
                    )
                    Text(
                        text = "Combo",
                        fontSize = tagTextSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF92400E),
                        maxLines = 1,
                    )
                }
            }
        }
        product.hasOptions -> {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFEEF5FF),
                border = BorderStroke(0.5.dp, Color(0xFFDCEBFF)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(paddingValues),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF201B82),
                        modifier = Modifier.size(iconSize),
                    )
                    Text(
                        text = "Opciones",
                        fontSize = tagTextSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF201B82),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de producto (.rest-prod de POS/main.css):
 * - Borde 1dp #E4E9F0, radio 12dp, fondo blanco.
 * - Cabecera de 64dp (o 90dp en pantallas grandes) teñida con el fondo suave de la categoría (--c-suave).
 * - Letra inicial grande como marca de agua en el centro con opacidad .55.
 * - Si tiene imagen, se monta sobre el fondo.
 * - Badge flotante en la esquina superior izquierda de la imagen (Combo, Opciones, Variantes).
 * - Nombre de producto con altura adecuada en color Ink.
 * - Precio en color de acento de la categoría (--c) a ancho completo, sin partirse.
 */
@Composable
private fun ProductGridCard(
    product: Product,
    catColor: FlowCatalogTokens.CategoryColor,
    isLarge: Boolean = false,
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
            // Imagen del Producto (.rest-prod__img: 90dp en pantallas grandes, fondo suave + marca de agua)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLarge) 90.dp else 64.dp)
                    .background(catColor.softBg),
                contentAlignment = Alignment.Center,
            ) {
                // Letra inicial centrada (marca de agua en color categoría)
                Text(
                    text = initialLetter,
                    fontSize = if (isLarge) 38.sp else 26.sp,
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

                // Badge flotante superpuesto en la esquina superior izquierda
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (isLarge) 5.dp else 4.dp),
                ) {
                    ProductCardTag(product = product, isLarge = isLarge)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLarge) 8.dp else 6.dp, vertical = if (isLarge) 6.dp else 5.dp),
            ) {
                // Nombre / Descripción del Producto (.rest-prod__nombre: 2 líneas, altura cómoda)
                Text(
                    text = product.description,
                    fontSize = if (isLarge) 13.sp else 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FlowCatalogTokens.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isLarge) 16.sp else 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLarge) 34.dp else 28.dp),
                )

                Spacer(modifier = Modifier.height(if (isLarge) 4.dp else 3.dp))

                // Precio a ancho completo (.rest-prod__precio)
                Text(
                    text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                    fontSize = if (isLarge) 15.5.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = catColor.accent,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
