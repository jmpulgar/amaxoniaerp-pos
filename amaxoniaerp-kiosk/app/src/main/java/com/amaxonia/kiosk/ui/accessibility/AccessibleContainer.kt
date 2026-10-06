package com.amaxonia.kiosk.ui.accessibility

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ACCESSIBLE_TOP_OFFSET = 380.dp
private val ZERO_OFFSET = 0.dp

@Composable
fun AccessibleContainer(
    isAccessibleMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val topPadding by animateDpAsState(
        targetValue = if (isAccessibleMode) ACCESSIBLE_TOP_OFFSET else ZERO_OFFSET,
        label = "accessible_top_offset",
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (isAccessibleMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♿ Modo de pantalla baja activo",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
        ) {
            content()
        }
    }
}
