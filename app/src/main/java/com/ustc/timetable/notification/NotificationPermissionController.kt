package com.ustc.timetable.notification

import android.os.Build
import com.ustc.timetable.timetable.data.SettingsStore
import kotlinx.coroutines.flow.first

class NotificationPermissionController(
    private val settings: SettingsStore,
) {
    suspend fun shouldRequestNow(areNotificationsEnabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 33 || areNotificationsEnabled) return false
        return !settings.notificationRequestShown.first()
    }

    suspend fun markRequested() {
        settings.markNotificationRequestShown()
    }
}
