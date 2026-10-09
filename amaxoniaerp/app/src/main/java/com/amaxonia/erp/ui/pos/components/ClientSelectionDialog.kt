package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch
import kotlin.math.ceil
import kotlin.math.max

private enum class ClientModalViewMode {
    GRID,
    TABLE,
}

@Composable
fun ClientSelectionDialog(
    currentClient: Client?,
    onSearch: suspend (String) -> List<Client>,
    onClientSelected: (Client) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Client>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(ClientModalViewMode.TABLE) }
    var currentPage by remember { mutableIntStateOf(1) }
    var selectedClientForAccept by remember { mutableStateOf<Client?>(null) }

    val pageSize = 12

    LaunchedEffect(searchQuery) {
        isSearching = true
        searchResults = onSearch(searchQuery)
        isSearching = false
        currentPage = 1
    }

    val totalPages = max(1, ceil(searchResults.size.toDouble() / pageSize.toDouble()).toInt())
    val paginatedClients = remember(searchResults, currentPage) {
        val start = (currentPage - 1) * pageSize
        searchResults.drop(start).take(pageSize)
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
                // 1. BARRA DE BÚSQUEDA 1:1 WEB (.producto-buscar)
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
                                text = "Buscar Cliente",
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
                                .clickable { /* Trigger search */ },
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

                        // Selector de Vista Grid (Cuadrícula)
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight()
                                .background(if (viewMode == ClientModalViewMode.GRID) Color(0xFF0097A7) else Color(0xFF00C5DC))
                                .clickable { viewMode = ClientModalViewMode.GRID },
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
                                .background(if (viewMode == ClientModalViewMode.TABLE) Color(0xFF0097A7) else Color(0xFF00C5DC))
                                .clickable { viewMode = ClientModalViewMode.TABLE },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Vista Tabla",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Botón Aceptar (Verde #28A745)
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF28A745))
                                .clickable {
                                    val target = selectedClientForAccept ?: paginatedClients.firstOrNull()
                                    if (target != null) {
                                        onClientSelected(target)
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

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // ACCESO DIRECTO RÁPIDO: CONSUMIDOR FINAL (CF)
                // ==========================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cf = Client(id = "0", code = "CF", name = "CONSUMIDOR FINAL", identification = "CF")
                            onClientSelected(cf)
                            onDismiss()
                        },
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentClient?.code == "CF" || currentClient == null) Color(0xFFE0F7FA) else Color(0xFFF8FAFC),
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (currentClient?.code == "CF" || currentClient == null) Color(0xFF00BCD4) else Color(0xFFE2E8F0),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00BCD4)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "CONSUMIDOR FINAL (CF)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF00838F),
                            )
                        }
                        Text(
                            text = "Rápido / Por Defecto",
                            fontSize = 11.sp,
                            color = Color(0xFF00838F),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 2. CUERPO: LISTADO DE CLIENTES
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (isSearching) {
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
                    } else if (searchResults.isEmpty()) {
                        // Estado Sin Resultados
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No se encontraron clientes para \"$searchQuery\"" else "No hay clientes registrados",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (viewMode == ClientModalViewMode.GRID) {
                        // ==========================================
                        // VISTA CUADRÍCULA (Grid de Clientes)
                        // ==========================================
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(paginatedClients, key = { it.id.ifBlank { it.code } }) { client ->
                                val isSelected = selectedClientForAccept == client || currentClient?.id == client.id

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedClientForAccept = client
                                            onClientSelected(client)
                                            onDismiss()
                                        },
                                    shape = RoundedCornerShape(8.dp),
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
                                            .padding(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "#${client.code.ifBlank { client.id }}",
                                                color = Color(0xFF607D8B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = client.identification.ifBlank { "-" },
                                                color = Color(0xFF757575),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = client.fullName.uppercase(),
                                            color = Color(0xFF212529),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        if (client.phone.isNotBlank() || client.email.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = client.phone.ifBlank { client.email },
                                                color = Color(0xFF757575),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        if (client.address.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = client.address,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ==========================================
                        // VISTA TABLA 1:1 WEB (cliente-listado)
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
                                Text("CÉDULA / RUC", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.width(95.dp))
                                Text("NOMBRE DEL CLIENTE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.weight(1.2f))
                                Text("DIRECCIÓN", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.weight(1f))
                                Text("TELÉFONO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), modifier = Modifier.width(85.dp))
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Filas con efecto cebra (#F6F6F6 y blanco)
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(paginatedClients, key = { _, c -> c.id.ifBlank { c.code } }) { index, client ->
                                    val rowBg = if (index % 2 == 0) Color.White else Color(0xFFF6F6F6)
                                    val isSelected = selectedClientForAccept == client || currentClient?.id == client.id

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) Color(0xFFFFFDE7) else rowBg)
                                            .clickable {
                                                selectedClientForAccept = client
                                                onClientSelected(client)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "#${client.code.ifBlank { client.id }}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF607D8B),
                                            modifier = Modifier.width(65.dp),
                                        )
                                        Text(
                                            text = client.identification.ifBlank { "-" },
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569),
                                            modifier = Modifier.width(95.dp),
                                        )
                                        Text(
                                            text = client.fullName.uppercase(),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF212529),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1.2f),
                                        )
                                        Text(
                                            text = client.address.ifBlank { "-" },
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF757575),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = client.phone.ifBlank { "-" },
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF757575),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(85.dp),
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. BARRA DE PAGINACIÓN EN PÍLDORA 1:1 WEB
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
