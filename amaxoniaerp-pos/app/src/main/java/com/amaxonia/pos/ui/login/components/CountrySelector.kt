package com.amaxonia.pos.ui.login.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Componente de selección de país para la pantalla de login.
 * Muestra un dropdown con los países disponibles y sus banderas.
 */
@Composable
fun CountrySelector(
    selectedCountry: ServerCountry,
    onCountrySelected: (ServerCountry) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val countries = remember { ServerCountries.getAvailable() }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "${selectedCountry.flagEmoji} ${selectedCountry.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Seleccionar país",
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            countries.forEach { country ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${country.flagEmoji} ${country.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onCountrySelected(country)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CountrySelectorPreview() {
    PosTheme {
        CountrySelector(
            selectedCountry = ServerCountries.AVAILABLE[0],
            onCountrySelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CountrySelectorPanamaPreview() {
    PosTheme {
        CountrySelector(
            selectedCountry = ServerCountries.AVAILABLE[1],
            onCountrySelected = {},
        )
    }
}
