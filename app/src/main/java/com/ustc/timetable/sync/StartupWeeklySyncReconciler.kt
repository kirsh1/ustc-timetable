package com.ustc.timetable.sync

import com.ustc.timetable.settings.WeeklySyncScheduling
import com.ustc.timetable.timetable.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StartupWeeklySyncReconciler(
    private val settings: SettingsStore,
    private val scheduling: WeeklySyncScheduling,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            scheduling.setEnabled(settings.weeklySyncEnabled.first())
        }
    }
}
