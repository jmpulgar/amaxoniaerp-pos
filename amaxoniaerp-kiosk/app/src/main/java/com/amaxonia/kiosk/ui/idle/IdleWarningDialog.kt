package com.amaxonia.kiosk.ui.idle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.CountdownRing
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.theme.KioskDialog
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

private const val URGENT_SECONDS = 5

@Composable
fun IdleWarningDialog(
    remainingSeconds: Int,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    totalSeconds: Int = DEFAULT_WARNING_TIMEOUT_SECONDS,
) {
    val colors = MaterialTheme.colorScheme
    KioskDialog(
        onDismissRequest = onContinue,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
    ) {
        Surface(
            modifier = modifier.widthIn(max = 860.dp).padding(horizontal = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surface,
            border = if (LocalHighContrast.current) BorderStroke(4.dp, colors.onSurface) else null,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.idle_dialog_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(36.dp))
                CountdownRing(
                    progress = if (totalSeconds > 0) remainingSeconds / totalSeconds.toFloat() else 0f,
                    size = 260.dp,
                    strokeWidth = 22.dp,
                    color = if (remainingSeconds <= URGENT_SECONDS) colors.error else colors.primary,
                ) {
                    Text(
                        text = remainingSeconds.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, lineHeight = 116.sp),
                        color = colors.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.idle_dialog_message, remainingSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(40.dp))
                KioskButton(
                    text = stringResource(R.string.idle_dialog_continue),
                    onClick = onContinue,
                    icon = Icons.Rounded.TouchApp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                KioskButton(
                    text = stringResource(R.string.idle_dialog_cancel),
                    onClick = onCancel,
                    style = KioskButtonStyle.Secondary,
                    contentColor = colors.error,
                    height = KioskTouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
