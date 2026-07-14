package com.amaxonia.pos.ui.common

import com.amaxonia.pos.domain.model.Client

// Esto permite usar .fullName en cualquier lugar donde tengas un objeto Client
val Client.fullName: String
    get() = if (lastName.isNotBlank()) "$firstName $lastName" else firstName
