package com.amaxonia.kiosk.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

const val NAV_ANIMATION_MS = 320
private const val PARTIAL_SLIDE_DIVISOR = 4
private const val BOUNDARY_SCALE_IN = 0.92f
private const val BOUNDARY_SCALE_OUT = 1.04f

private fun <T> navSpec() = tween<T>(durationMillis = NAV_ANIMATION_MS, easing = FastOutSlowInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSessionBoundary(): Boolean =
    initialState.destination.route in KioskDestinations.sessionBoundaryRoutes ||
        targetState.destination.route in KioskDestinations.sessionBoundaryRoutes

/** Forward: new screen slides in from the right with a fade; Attract/OrderNumber fade + scale. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskEnterTransition(): EnterTransition =
    if (isSessionBoundary()) {
        fadeIn(navSpec()) + scaleIn(navSpec(), initialScale = BOUNDARY_SCALE_IN)
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpec()) + fadeIn(navSpec())
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskExitTransition(): ExitTransition =
    if (isSessionBoundary()) {
        fadeOut(navSpec()) + scaleOut(navSpec(), targetScale = BOUNDARY_SCALE_OUT)
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, navSpec()) { it / PARTIAL_SLIDE_DIVISOR } +
            fadeOut(navSpec())
    }

/** Back: the previous screen slides back in from the left. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskPopEnterTransition(): EnterTransition =
    if (isSessionBoundary()) {
        fadeIn(navSpec()) + scaleIn(navSpec(), initialScale = BOUNDARY_SCALE_IN)
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpec()) { it / PARTIAL_SLIDE_DIVISOR } +
            fadeIn(navSpec())
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.kioskPopExitTransition(): ExitTransition =
    if (isSessionBoundary()) {
        fadeOut(navSpec()) + scaleOut(navSpec(), targetScale = BOUNDARY_SCALE_OUT)
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navSpec()) + fadeOut(navSpec())
    }

/** Clears the whole back stack and shows [route] as the only destination (Attract, Login, CajaSetup, OrderNumber). */
fun NavController.navigateAsRoot(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
