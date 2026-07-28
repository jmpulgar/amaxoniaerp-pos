package com.amaxonia.pos.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.ui.theme.PosExtraShapes

/**
 * Category selector shared by phone (horizontal chip row) and tablet-landscape (vertical rail) —
 * same data and colors, only the LazyRow/LazyColumn axis differs.
 *
 * On phones, only the first [maxVisible] departments are shown plus a trailing "Más" chip that
 * triggers [onMoreClick] (e.g. opening the existing full department bottom sheet). When [vertical],
 * the full list is shown since a side rail scrolls comfortably on its own.
 */
@Composable
// Parámetros mantienen API Compose compartida por diseños horizontal y vertical.
@Suppress("LongParameterList")
fun CategoryChipRow(
    departments: List<Department>,
    selectedDepartmentId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
    maxVisible: Int = 6,
) {
    val visibleDepartments = if (vertical) departments else departments.take(maxVisible)
    val hasOverflow = !vertical && onMoreClick != null && departments.size > maxVisible

    if (vertical) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChip(
                    label = "Todos",
                    selected = selectedDepartmentId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(visibleDepartments, key = { it.id }) { dept ->
                CategoryChip(
                    label = dept.name,
                    selected = selectedDepartmentId == dept.id,
                    onClick = { onSelect(dept.id) },
                )
            }
        }
    } else {
        LazyRow(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChip(
                    label = "Todos",
                    selected = selectedDepartmentId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(visibleDepartments, key = { it.id }) { dept ->
                CategoryChip(
                    label = dept.name,
                    selected = selectedDepartmentId == dept.id,
                    onClick = { onSelect(dept.id) },
                )
            }
            if (hasOverflow) {
                item {
                    CategoryChip(label = "Más", selected = false, onClick = { onMoreClick?.invoke() })
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = PosExtraShapes.Pill,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}
