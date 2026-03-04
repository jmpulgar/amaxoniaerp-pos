package com.amaxonia.pos.ui.clients

import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.model.Country

data class ClientFormState(
    val client: Client = Client(),
    val clientTypes: List<ClientTypeOption> = emptyList(),
    val countries: List<Country> = emptyList(),
    val addressLevel1Options: List<AddressLevel> = emptyList(),
    val addressLevel2Options: List<AddressLevel> = emptyList(),
    val addressLevel3Options: List<AddressLevel> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val error: String? = null
)
