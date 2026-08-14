package com.amaxonia.pos.ui.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ReportButtons(
    state: CierreCajaReadyState,
    actions: CierreCajaActions,
) {
    val enabled = !state.isClosing && !state.isPrintingReportX && !state.isPrintingReportZ
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
  ReportXButton(
      isPrinting = state.isPrintingReportX,
      enabled = enabled,
      onClick = actions.onPrintReportX,
  )
        }
        Box(modifier = Modifier.weight(1f)) {
  ReportZButton(
      isPrinting = state.isPrintingReportZ,
      enabled = enabled,
      onClick = actions.onPrintReportZ,
  )
        }
    }
}

@Composable
private fun ReportXButton(
    isPrinting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors =
  ButtonDefaults.outlinedButtonColors(
      contentColor = MaterialTheme.colorScheme.primary,
      disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
  ),
    ) {
        if (isPrinting) {
  CircularProgressIndicator(
      strokeWidth = 2.dp,
      modifier = Modifier.size(18.dp),
      color = MaterialTheme.colorScheme.primary,
  )
        } else {
  Icon(
      Icons.AutoMirrored.Rounded.Assignment,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
  )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
  if (isPrinting) "Imprimiendo..." else "Reporte X",
  fontWeight = FontWeight.Bold,
  fontSize = 14.sp,
        )
    }
}

@Composable
private fun ReportZButton(
    isPrinting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors =
  ButtonDefaults.outlinedButtonColors(
      contentColor = MaterialTheme.colorScheme.error,
      disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
  ),
    ) {
        if (isPrinting) {
  CircularProgressIndicator(
      strokeWidth = 2.dp,
      modifier = Modifier.size(18.dp),
      color = MaterialTheme.colorScheme.error,
  )
        } else {
  Icon(
      Icons.Rounded.Description,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
  )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
  if (isPrinting) "Imprimiendo..." else "Reporte Z",
  fontWeight = FontWeight.Bold,
  fontSize = 14.sp,
        )
    }
}
