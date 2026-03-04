package com.amaxonia.pos.data.remote

import com.amaxonia.pos.BuildConfig

/**
 * URL base del backend (única para Venezuela y Panamá).
 *
 * Se toma de BuildConfig:
 * - Debug (local): http://10.0.2.2:8080/
 * - Release (producción): https://api.listoerp.app/
 */
object ApiConfig {
    val baseUrl: String = BuildConfig.BASE_URL
}
