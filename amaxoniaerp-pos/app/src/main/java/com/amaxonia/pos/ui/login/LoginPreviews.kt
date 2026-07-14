package com.amaxonia.pos.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.amaxonia.pos.ui.theme.PosTheme

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
internal fun LoginContentPreview() {
    PosTheme {
        LoginContent(
            state = LoginState(username = "operator"),
            onAction = {},
            onBack = {},
        )
    }
}
