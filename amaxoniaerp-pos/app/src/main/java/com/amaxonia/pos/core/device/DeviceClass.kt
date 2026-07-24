package com.amaxonia.pos.core.device

/**
 * Coarse device form-factor used to decide orientation lock and adaptive layouts.
 * Classification is based on smallestScreenWidthDp, which stays stable across
 * rotation (unlike current width), so it is safe to use for an orientation decision.
 */
enum class DeviceClass { PHONE, TABLET }

const val TABLET_SW_BREAKPOINT_DP = 600

fun deviceClassFor(smallestScreenWidthDp: Int): DeviceClass =
    if (smallestScreenWidthDp >= TABLET_SW_BREAKPOINT_DP) DeviceClass.TABLET else DeviceClass.PHONE
