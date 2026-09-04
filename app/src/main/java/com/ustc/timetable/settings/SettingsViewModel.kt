package com.ustc.timetable.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.timetable.notification.NotificationPermissionController
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.school.ustc.auth.SessionBlob
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.domain.SemesterId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ustc.timetable.appearance.AppearanceMode

internal object SettingsTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    fun format(epochMillis: Long, zoneId: ZoneId): String = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
}

class SettingsViewModel(
    private val settings: SettingsStore,
    private val profiles: ScheduleProfileRepository,
    semesters: SemesterRepository,
    private val permission: NotificationPermissionController,
    private val notifications: NotificationsEnabledChecker,
    private val session: SettingsSessionAccess? = null,
    private val weeklyScheduling: WeeklySyncScheduling? = null,
    private val manualSync: SettingsManualSync? = null,
    private val reloginRuntimeAvailable: Boolean = false,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    appVersion: String,
) : ViewModel() {
    private data class Persisted(
        val show: Boolean,
        val weekly: Boolean,
        val reauth: Boolean,
        val last: Long?,
        val requested: Boolean,
        val viewedSemesterId: String? = null,
        val appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
        val wallpaperUri: String? = null,
    )
    private data class SessionStatus(val loading: Boolean, val blob: SessionBlob?)

    private val sessionStatus = MutableStateFlow(SessionStatus(true, null))
    private val notificationsEnabled = MutableStateFlow(notifications.areEnabled())
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val requestMutex = Mutex()
    private var requestEventIssued = false

    private val persistedCore = combine(
        settings.showNonCurrentWeek,
        settings.weeklySyncEnabled,
        settings.needReauth,
        settings.lastSyncFinishedAt,
        settings.notificationRequestShown,
    ) { show, weekly, reauth, last, requested -> Persisted(show, weekly, reauth, last, requested) }
    private val persisted = combine(
        persistedCore,
        settings.viewedSemesterId,
        settings.appearanceMode,
        settings.timetableWallpaperUri,
    ) { value, viewedId, appearance, wallpaper ->
        value.copy(viewedSemesterId = viewedId, appearanceMode = appearance, wallpaperUri = wallpaper)
    }

    val state = combine(persisted, profiles.observeWorking(), semesters.observeSemesters(), sessionStatus, notificationsEnabled) {
            p, working, semesterList, sessionValue, enabled ->
        val login = when {
            p.reauth -> SchoolLoginUiState.Expired
            sessionValue.blob != null -> SchoolLoginUiState.LoggedIn(sessionValue.blob.capturedAt)
            else -> SchoolLoginUiState.NotLoggedIn
        }
        val hasPortalTarget = semesterList.any { it.isCurrentAcademicSemester && it.portalLinked }
        SettingsUiState(
            appearanceMode = p.appearanceMode,
            timetableWallpaperUri = p.wallpaperUri,
            showNonCurrentWeek = p.show,
            weeklySyncEnabled = p.weekly,
            notificationRequestShown = p.requested,
            notificationsEnabled = enabled,
            showNotificationDeniedHint = shouldShowNotificationDeniedHint(Build.VERSION.SDK_INT, enabled, p.requested),
            lastSyncText = p.last?.let { SettingsTimeFormatter.format(it, zoneId) } ?: "—",
            loginState = login,
            loginText = when (login) {
                SchoolLoginUiState.NotLoggedIn -> "未登录"
                SchoolLoginUiState.Expired -> "已失效"
                is SchoolLoginUiState.LoggedIn -> "已登录（${SettingsTimeFormatter.format(login.capturedAt.toEpochMilli(), zoneId)}）"
            },
            sessionLoading = sessionValue.loading,
            workingProfile = working,
            syncNowEnabled = manualSync != null && hasPortalTarget,
            hasPortalLinkedCurrentSemester = hasPortalTarget,
            reloginEnabled = reloginRuntimeAvailable,
            canApplyWorkingToAcademicCurrent = semesterList.any { it.isCurrentAcademicSemester },
            semestersLoaded = true,
            availableSemesters = semesterList,
            viewedSemesterId = p.viewedSemesterId?.let(::SemesterId),
            appVersion = appVersion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(appVersion = appVersion))

    init { refreshSession() }

    fun onToggleShowNonCurrentWeek(value: Boolean) { viewModelScope.launch { settings.setShowNonCurrentWeek(value) } }
    fun onAppearanceModeSelected(mode: AppearanceMode) { viewModelScope.launch { settings.setAppearanceMode(mode) } }
    fun onWallpaperSelected(uri: String) { viewModelScope.launch { settings.setTimetableWallpaperUri(uri) } }
    fun onWallpaperCleared() { viewModelScope.launch { settings.setTimetableWallpaperUri(null) } }

    fun onSemesterSelected(id: SemesterId) {
        if (state.value.availableSemesters.none { it.id == id }) return
        viewModelScope.launch { settings.setViewedSemesterId(id.value) }
    }

    fun onToggleWeeklySync(value: Boolean) {
        viewModelScope.launch {
            settings.setWeeklySyncEnabled(value)
            weeklyScheduling?.setEnabled(value)
            if (value) maybeRequestNotificationPermission()
        }
    }

    fun onSyncSectionEntered() {
        viewModelScope.launch {
            refreshSessionNow()
            maybeRequestNotificationPermission()
        }
    }

    private suspend fun maybeRequestNotificationPermission() = requestMutex.withLock {
        if (!requestEventIssued && permission.shouldRequestNow(notifications.areEnabled())) {
            requestEventIssued = true
            eventChannel.send(SettingsEvent.RequestNotificationPermission)
        }
    }

    fun onNotificationPermissionRequestLaunched() { viewModelScope.launch { permission.markRequested(); refreshNotificationsEnabled() } }
    fun onNotificationPermissionResult() = refreshNotificationsEnabled()
    fun refreshNotificationsEnabled() { notificationsEnabled.value = notifications.areEnabled() }
    fun onNotificationHintClick() { eventChannel.trySend(SettingsEvent.OpenNotificationSettings) }
    fun onSyncNow() { if (state.value.syncNowEnabled) manualSync?.start() }
    fun onReloginClick() { if (reloginRuntimeAvailable) eventChannel.trySend(SettingsEvent.RequestRelogin) }

    fun onReloginResult(successful: Boolean, pendingManualSyncWillResume: Boolean = false) {
        if (!successful) return
        viewModelScope.launch {
            settings.setNeedReauth(false)
            refreshSessionNow()
            if (pendingManualSyncWillResume) return@launch
            if (state.value.hasPortalLinkedCurrentSemester) {
                manualSync?.start()
            } else {
                eventChannel.send(SettingsEvent.RequestImport)
            }
        }
    }

    fun onClearLogin() {
        viewModelScope.launch {
            session?.clear()
            settings.setNeedReauth(false)
            sessionStatus.value = SessionStatus(false, null)
        }
    }

    fun restoreWorkingDefault() { viewModelScope.launch { profiles.restoreWorkingToBundled() } }
    fun applyWorkingToAcademicCurrent() { viewModelScope.launch { profiles.rebindAcademicCurrentSemester() } }
    fun saveWorkingPeriods(periods: List<PeriodTime>) {
        val base = state.value.workingProfile ?: return
        viewModelScope.launch { profiles.saveWorkingEdited(base, periods) }
    }

    private fun refreshSession() { viewModelScope.launch { refreshSessionNow() } }
    private suspend fun refreshSessionNow() { sessionStatus.value = SessionStatus(false, session?.load()) }
}
