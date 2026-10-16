package com.amaxonia.erp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores 1:1 Flow ERP (flow-tokens.css y flow-app.css)
val FlowPrependBg = Color(0xFF5C6BC0)       // --flx-5c6bc0 (índigo clásico de prepend Amaxonia)
val FlowInputText = Color(0xFF0F172A)      // --fl-ink
val FlowInputBorder = Color(0xFFD5DDE8)    // --fl-line-strong
val FlowInputBg = Color(0xFFFFFFFF)        // --fl-surface
val FlowPlaceholder = Color(0xFF94A3B8)    // --fl-text-3
val FlowPortletBorder = Color(0xFFE2E8F0)  // --fl-line
val FlowPortletHeadBg = Color(0xFFF8FAFC)  // Cabecera secundaria

/**
 * Componente 1:1 de input-group con prepend (.input-group.m-input-group--square):
 * [ Etiqueta + Ícono ] [ Campo de texto editable ]
 */
@Composable
fun FlowInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    placeholder: String = "",
    readOnly: Boolean = false,
    prependBg: Color = FlowPrependBg,
    prependWidth: Dp = 90.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.height(44.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // PREPEND BOX (.input-group-prepend > .input-group-text)
        Box(
            modifier =
                Modifier
                    .width(prependWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(prependBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.02.sp,
                )
                if (icon != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // INPUT FIELD (.form-control.m-input)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(
                        if (trailingContent == null) {
                            RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        },
                    )
                    .background(if (readOnly) Color(0xFFF1F5F9) else FlowInputBg)
                    .border(
                        BorderStroke(1.dp, FlowInputBorder),
                        if (trailingContent == null) {
                            RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        },
                    )
                    .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = FlowPlaceholder,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = readOnly,
                textStyle =
                    TextStyle(
                        color = FlowInputText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                cursorBrush = SolidColor(Color(0xFF2488FA)),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
        }

        // Contenido opcional al final (ej. botón generar clave)
        if (trailingContent != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
            ) {
                trailingContent()
            }
        }
    }
}

/**
 * Componente 1:1 de selector desplegable con prepend Flow ERP (.m-select2)
 */
@Composable
fun <T> FlowSelectGroup(
    label: String,
    selectedOption: T,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    optionLabel: (T) -> String = { it.toString() },
    prependBg: Color = FlowPrependBg,
    prependWidth: Dp = 90.dp,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.height(44.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // PREPEND BOX
        Box(
            modifier =
                Modifier
                    .width(prependWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(prependBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (icon != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // SELECT CONTAINER
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(FlowInputBg)
                    .border(BorderStroke(1.dp, FlowInputBorder), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionLabel(selectedOption),
                    color = FlowInputText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = FlowPlaceholder,
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                fontSize = 13.sp,
                                fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == selectedOption) Color(0xFF2488FA) else FlowInputText,
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sub-tarjeta interna del modal Flow ERP (.m-portlet .m-portlet--full-height)
 */
@Composable
fun FlowSectionPortlet(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FlowPortletBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabecera de la sección
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(FlowPortletHeadBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = Color(0xFF1E293B),
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FlowPortletBorder))

            // Cuerpo de la sección
            Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                content()
            }
        }
    }
}
