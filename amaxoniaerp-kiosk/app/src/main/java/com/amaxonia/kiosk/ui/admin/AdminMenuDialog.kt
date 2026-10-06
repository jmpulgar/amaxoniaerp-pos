package com.amaxonia.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.kioskPressable

private const val DISABLED_ALPHA = 0.4f

@Composable
fun AdminMenuDialog(
    isLockTaskActive: Boolean,
    hasLastOrder: Boolean,
    onRePair: () -> Unit,
    onTestPrint: () -> Unit,
    onReprintLastReceipt: () -> Unit,
    onToggleLockTask: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier = modifier.widthIn(max = 820.dp).padding(horizontal = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surface,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(104.dp).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Build, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(56.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.admin_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.admin_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(32.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AdminActionRow(text = stringResource(R.string.admin_repair), icon = Icons.Rounded.QrCode, onClick = onRePair)
                    AdminActionRow(text = stringResource(R.string.admin_test_print), icon = Icons.Rounded.Print, onClick = onTestPrint)
                    AdminActionRow(
                        text = stringResource(R.string.admin_reprint),
                        icon = Icons.Rounded.Receipt,
                        enabled = hasLastOrder,
                        onClick = onReprintLastReceipt,
                    )
                    AdminActionRow(
                        text = stringResource(if (isLockTaskActive) R.string.admin_exit_kiosk else R.string.admin_enter_kiosk),
                        icon = if (isLockTaskActive) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        onClick = onToggleLockTask,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                KioskButton(
                    text = stringResource(R.string.admin_close),
                    onClick = onDismiss,
                    style = KioskButtonStyle.Secondary,
                    icon = Icons.AutoMirrored.Rounded.ExitToApp,
                    height = KioskTouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AdminActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .kioskPressable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = KioskTouchTarget).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
