package com.amaxonia.pos.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
inline fun <reified T : ViewModel> injectedViewModel(noinline factory: () -> T): T =
    viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = factory() as VM
            },
    )
