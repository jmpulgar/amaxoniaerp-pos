package com.amaxonia.kiosk.ui.accessibility

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.theme.KioskColors

private val ACCESSIBLE_TOP_OFFSET = 380.dp
private val ZERO_OFFSET = 0.dp

/**
 * "Pantalla baja": pushes the whole screen down by [ACCESSIBLE_TOP_OFFSET] so every action is
 * reachable from a wheelchair. The freed top area shows a calm status panel instead of a blank gap;
 * the content gets exactly the remaining height, so screens must lay out with weights/scrolling.
 */
@Composable
fun AccessibleContainer(
    isAccessibleMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val topOffset by animateDpAsState(
        targetValue = if (isAccessibleMode) ACCESSIBLE_TOP_OFFSET else ZERO_OFFSET,
        label = "accessible_top_offset",
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (topOffset > ZERO_OFFSET) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(topOffset)
                        .clipToBounds()
                        .background(KioskColors.softBrush),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Accessible,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Text(
                        text = stringResource(R.string.a11y_reach_mode_active),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        Icons.Rounded.KeyboardDoubleArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }
}
