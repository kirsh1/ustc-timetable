package com.ustc.timetable.appearance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WallpaperRuntimeState {
    private val mutableUnavailableUri = MutableStateFlow<String?>(null)
    val unavailableUri: StateFlow<String?> = mutableUnavailableUri.asStateFlow()

    fun reportUnavailable(uri: String) { mutableUnavailableUri.value = uri }
    fun clear(uri: String? = null) {
        if (uri == null || mutableUnavailableUri.value == uri) mutableUnavailableUri.value = null
    }
}
