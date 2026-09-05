package com.ustc.timetable.settings

import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.school.ustc.auth.SessionBlob
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import java.time.Instant
import com.ustc.timetable.appearance.AppearanceMode

internal fun shouldShowNotificationDeniedHint(sdk: Int, notificationsEnabled: Boolean, requestShown: Boolean): Boolean =
    sdk >= 33 && !notificationsEnabled && requestShown

fun interface WeeklySyncScheduling { fun setEnabled(enabled: Boolean) }
fun interface NotificationsEnabledChecker { fun areEnabled(): Boolean }
fun interface SettingsManualSync { fun start() }

interface SettingsSessionAccess {
    suspend fun load(): SessionBlob?
    suspend fun clear()
}

sealed interface SettingsEvent {
    data object RequestNotificationPermission : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
    data object RequestRelogin : SettingsEvent
    data object RequestImport : SettingsEvent
}

sealed interface SchoolLoginUiState {
    data object NotLoggedIn : SchoolLoginUiState
    data class LoggedIn(val capturedAt: Instant) : SchoolLoginUiState
    data object Expired : SchoolLoginUiState
}

data class SettingsUiState(
    val exactOverlapMode: com.ustc.timetable.timetable.ui.ExactOverlapMode = com.ustc.timetable.timetable.ui.ExactOverlapMode.SPLIT,
    val coursePaletteSeed: Long = com.ustc.timetable.timetable.data.DEFAULT_COURSE_PALETTE_SEED,
    val wallpaperVisibilityPercent: Int = com.ustc.timetable.timetable.data.DEFAULT_WALLPAPER_VISIBILITY_PERCENT,
    val appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    val timetableWallpaperUri: String? = null,
    val wallpaperUnavailable: Boolean = false,
    val showNonCurrentWeek: Boolean = false,
    val weeklySyncEnabled: Boolean = true,
    val notificationRequestShown: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val showNotificationDeniedHint: Boolean = false,
    val lastSyncText: String = "—",
    val loginState: SchoolLoginUiState = SchoolLoginUiState.NotLoggedIn,
    val loginText: String = "未登录",
    val sessionLoading: Boolean = true,
    val workingProfile: ScheduleProfile? = null,
    val syncNowEnabled: Boolean = false,
    val hasPortalLinkedCurrentSemester: Boolean = false,
    val reloginEnabled: Boolean = false,
    val canApplyWorkingToAcademicCurrent: Boolean = false,
    val semestersLoaded: Boolean = false,
    val availableSemesters: List<Semester> = emptyList(),
    val viewedSemesterId: SemesterId? = null,
    val appVersion: String = "",
)
