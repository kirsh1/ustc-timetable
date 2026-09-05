package com.ustc.timetable.settings

data class WallpaperVisibilityState(val persistedPercent: Int, val candidatePercent: Int)

object WallpaperVisibilityEditor {
    fun open(persistedPercent: Int): WallpaperVisibilityState {
        val bounded = persistedPercent.coerceIn(0, 100)
        return WallpaperVisibilityState(bounded, bounded)
    }
    fun preview(state: WallpaperVisibilityState, percent: Int): WallpaperVisibilityState =
        state.copy(candidatePercent = percent.coerceIn(0, 100))
    fun finish(state: WallpaperVisibilityState): WallpaperVisibilityState = open(state.candidatePercent)
    fun cancel(state: WallpaperVisibilityState): WallpaperVisibilityState = open(state.persistedPercent)
}
