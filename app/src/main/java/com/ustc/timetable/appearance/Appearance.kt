package com.ustc.timetable.appearance

import androidx.compose.ui.graphics.Color

enum class AppearanceMode { LIGHT, DARK, SYSTEM }

enum class ResolvedAppearance { LIGHT, DARK }

fun resolveAppearance(mode: AppearanceMode, systemDark: Boolean): ResolvedAppearance = when (mode) {
    AppearanceMode.LIGHT -> ResolvedAppearance.LIGHT
    AppearanceMode.DARK -> ResolvedAppearance.DARK
    AppearanceMode.SYSTEM -> if (systemDark) ResolvedAppearance.DARK else ResolvedAppearance.LIGHT
}

fun wallpaperScrim(appearance: ResolvedAppearance): Color = when (appearance) {
    ResolvedAppearance.LIGHT -> Color.White.copy(alpha = 0.76f)
    ResolvedAppearance.DARK -> Color.Black.copy(alpha = 0.66f)
}

fun wallpaperImageAlpha(visibilityPercent: Int): Float = visibilityPercent.coerceIn(0, 100) / 100f

fun wallpaperScrim(appearance: ResolvedAppearance, visibilityPercent: Int): Color {
    val safety = wallpaperScrim(appearance)
    return safety.copy(alpha = safety.alpha * wallpaperImageAlpha(visibilityPercent))
}
