package com.ustc.timetable.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

private enum class AppDestination { TIMETABLE, SETTINGS, PROFILE_EDITOR }

@Composable
fun AppRoot(
    gate: FirstLaunchGate,
    firstLaunchContent: @Composable () -> Unit,
    timetableContent: @Composable (onOpenSettings: () -> Unit) -> Unit,
    settingsContent: @Composable (onBack: () -> Unit, onOpenProfile: () -> Unit) -> Unit,
    profileEditorContent: @Composable (onBack: () -> Unit) -> Unit,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.TIMETABLE) }
    LaunchedEffect(gate) {
        if (gate == FirstLaunchGate.Ready) destination = AppDestination.TIMETABLE
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(safeDrawingInsets)
            .testTag("app_safe_content"),
    ) {
        when (gate) {
            FirstLaunchGate.Loading -> Box(
                modifier = Modifier.fillMaxSize().testTag("app_root_loading"),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            FirstLaunchGate.Empty -> firstLaunchContent()
            FirstLaunchGate.Ready -> {
                BackHandler(enabled = destination != AppDestination.TIMETABLE) {
                    destination = if (destination == AppDestination.PROFILE_EDITOR) AppDestination.SETTINGS else AppDestination.TIMETABLE
                }
                when (destination) {
                    AppDestination.TIMETABLE -> timetableContent { destination = AppDestination.SETTINGS }
                    AppDestination.SETTINGS -> settingsContent(
                        { destination = AppDestination.TIMETABLE },
                        { destination = AppDestination.PROFILE_EDITOR },
                    )
                    AppDestination.PROFILE_EDITOR -> profileEditorContent { destination = AppDestination.SETTINGS }
                }
            }
        }
    }
}
