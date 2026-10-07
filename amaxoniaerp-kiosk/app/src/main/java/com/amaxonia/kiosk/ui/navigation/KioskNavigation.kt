package com.amaxonia.kiosk.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

// Short and decelerating: the next screen is on glass almost at once, so the kiosk feels instant.
const val NAV_ANIMATION_MS = 200
private const val NAV_EXIT_MS = 120

/** Screens only travel a short distance (1/12 of the width) instead of sliding across the whole kiosk. */
private const val SLIDE_DIVISOR = 12
private const val BOUNDARY_SCALE_IN = 0.97f
private const val BOUNDARY_SCALE_OUT = 1.02f

private fun <T> navSpec() = tween<T>(durationMillis = NAV_ANIMATION_MS, easing = LinearOutSlowInEasing)

private fun <T> exitSpec() = tween<T>(durationMillis = NAV_EXIT_MS, easing = FastOutLinearInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSessionBoundary(): Boolean =
    initialState.destination.route in KioskDestinations.sessionBoundaryRoutes ||
        targetState.destination.route in KioskDestinations.sessionBoundaryRoutes

/** Forward: new screen fades in with a short nudge from the right; Attract/OrderNumber fade + subtle scale. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskEnterTransition(): EnterTransition =
    if (isSessionBoundary()) {
        fadeIn(navSpec()) + scaleIn(navSpec(), initialScale = BOUNDARY_SCALE_IN)
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpec()) { it / SLIDE_DIVISOR } +
            fadeIn(navSpec())
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskExitTransition(): ExitTransition =
    if (isSessionBoundary()) {
        fadeOut(exitSpec()) + scaleOut(exitSpec(), targetScale = BOUNDARY_SCALE_OUT)
    } else {
        fadeOut(exitSpec())
    }

/** Back: the previous screen fades back in with a short nudge from the left. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskPopEnterTransition(): EnterTransition =
    if (isSessionBoundary()) {
        fadeIn(navSpec()) + scaleIn(navSpec(), initialScale = BOUNDARY_SCALE_IN)
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpec()) { it / SLIDE_DIVISOR } +
            fadeIn(navSpec())
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskPopExitTransition(): ExitTransition =
    if (isSessionBoundary()) {
        fadeOut(exitSpec()) + scaleOut(exitSpec(), targetScale = BOUNDARY_SCALE_OUT)
    } else {
        fadeOut(exitSpec())
    }

/** Clears the whole back stack and shows [route] as the only destination (Attract, Login, CajaSetup, OrderNumber). */
fun NavController.navigateAsRoot(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
