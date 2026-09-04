package com.ustc.timetable.timetable.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ustc.timetable.appearance.AppearanceMode

/**
 * 冻结设置键与默认值（SPEC §9.2）。activeWorkingProfileId 为 null 时解释为 bundled official。
 * 单一 DataStore 实例由 AppContainer 经 createSettingsDataStore 提供。
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val VIEWED_SEMESTER_ID = stringPreferencesKey("viewed_semester_id")
        val SHOW_NON_CURRENT_WEEK = booleanPreferencesKey("show_non_current_week")
        val WEEKLY_SYNC_ENABLED = booleanPreferencesKey("weekly_sync_enabled")
        val ACTIVE_WORKING_PROFILE_ID = stringPreferencesKey("active_working_profile_id")
        val NEED_REAUTH = booleanPreferencesKey("need_reauth")
        val LAST_SYNC_FINISHED_AT = longPreferencesKey("last_sync_finished_at")
        val NOTIFICATIONS_REQUEST_SHOWN = booleanPreferencesKey("notifications_request_shown")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val TIMETABLE_WALLPAPER_URI = stringPreferencesKey("timetable_wallpaper_uri")
    }

    val viewedSemesterId: Flow<String?> = dataStore.data.map { it[Keys.VIEWED_SEMESTER_ID] }
    val showNonCurrentWeek: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_NON_CURRENT_WEEK] ?: false }
    val weeklySyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.WEEKLY_SYNC_ENABLED] ?: true }
    val activeWorkingProfileId: Flow<String?> = dataStore.data.map { it[Keys.ACTIVE_WORKING_PROFILE_ID] }
    val needReauth: Flow<Boolean> = dataStore.data.map { it[Keys.NEED_REAUTH] ?: false }
    val lastSyncFinishedAt: Flow<Long?> = dataStore.data.map { it[Keys.LAST_SYNC_FINISHED_AT] }
    val notificationRequestShown: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_REQUEST_SHOWN] ?: false }
    val appearanceMode: Flow<AppearanceMode> = dataStore.data.map { preferences ->
        preferences[Keys.APPEARANCE_MODE]
            ?.let { stored -> AppearanceMode.entries.firstOrNull { it.name == stored } }
            ?: AppearanceMode.LIGHT
    }
    val timetableWallpaperUri: Flow<String?> = dataStore.data.map { it[Keys.TIMETABLE_WALLPAPER_URI] }

    suspend fun setViewedSemesterId(id: String) = dataStore.edit { it[Keys.VIEWED_SEMESTER_ID] = id }

    suspend fun setShowNonCurrentWeek(v: Boolean) = dataStore.edit { it[Keys.SHOW_NON_CURRENT_WEEK] = v }

    suspend fun setWeeklySyncEnabled(v: Boolean) = dataStore.edit { it[Keys.WEEKLY_SYNC_ENABLED] = v }

    suspend fun setActiveWorkingProfileId(id: String) = dataStore.edit { it[Keys.ACTIVE_WORKING_PROFILE_ID] = id }

    /** working 指针清除 = 回到 bundled official（SPEC §3.5.1 恢复默认语义）。 */
    suspend fun clearActiveWorkingProfileId() = dataStore.edit { it.remove(Keys.ACTIVE_WORKING_PROFILE_ID) }

    suspend fun setNeedReauth(v: Boolean) = dataStore.edit { it[Keys.NEED_REAUTH] = v }

    suspend fun setLastSyncFinishedAt(at: Long) = dataStore.edit { it[Keys.LAST_SYNC_FINISHED_AT] = at }

    suspend fun markNotificationRequestShown() = dataStore.edit { it[Keys.NOTIFICATIONS_REQUEST_SHOWN] = true }

    suspend fun setAppearanceMode(mode: AppearanceMode) = dataStore.edit { it[Keys.APPEARANCE_MODE] = mode.name }

    suspend fun setTimetableWallpaperUri(uri: String?) = dataStore.edit {
        if (uri == null) it.remove(Keys.TIMETABLE_WALLPAPER_URI) else it[Keys.TIMETABLE_WALLPAPER_URI] = uri
    }
}
