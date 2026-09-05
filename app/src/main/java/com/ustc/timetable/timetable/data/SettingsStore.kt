package com.ustc.timetable.timetable.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.timetable.ui.ExactOverlapMode

const val DEFAULT_COURSE_PALETTE_SEED: Long = 0L
const val DEFAULT_WALLPAPER_VISIBILITY_PERCENT: Int = 65

/**
 * 冻结设置键与默认值（SPEC §9.2）。activeWorkingProfileId 为 null 时解释为 bundled official。
 * 单一 DataStore 实例由 AppContainer 经 createSettingsDataStore 提供。
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val EXACT_OVERLAP_MODE = stringPreferencesKey("exact_overlap_display_mode")
        val VIEWED_SEMESTER_ID = stringPreferencesKey("viewed_semester_id")
        val SHOW_NON_CURRENT_WEEK = booleanPreferencesKey("show_non_current_week")
        val WEEKLY_SYNC_ENABLED = booleanPreferencesKey("weekly_sync_enabled")
        val ACTIVE_WORKING_PROFILE_ID = stringPreferencesKey("active_working_profile_id")
        val NEED_REAUTH = booleanPreferencesKey("need_reauth")
        val LAST_SYNC_FINISHED_AT = longPreferencesKey("last_sync_finished_at")
        val NOTIFICATIONS_REQUEST_SHOWN = booleanPreferencesKey("notifications_request_shown")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val TIMETABLE_WALLPAPER_URI = stringPreferencesKey("timetable_wallpaper_uri")
        val COURSE_PALETTE_SEED = longPreferencesKey("course_palette_seed")
        val WALLPAPER_VISIBILITY_PERCENT = intPreferencesKey("wallpaper_visibility_percent")
    }

    val viewedSemesterId: Flow<String?> = dataStore.data.map { it[Keys.VIEWED_SEMESTER_ID] }
    val exactOverlapMode: Flow<ExactOverlapMode> = dataStore.data.map { preferences ->
        ExactOverlapMode.entries.firstOrNull { it.name == preferences[Keys.EXACT_OVERLAP_MODE] } ?: ExactOverlapMode.SPLIT
    }
    suspend fun setExactOverlapMode(mode: ExactOverlapMode) { dataStore.edit { it[Keys.EXACT_OVERLAP_MODE] = mode.name } }
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
    val coursePaletteSeed: Flow<Long> = dataStore.data.map { it[Keys.COURSE_PALETTE_SEED] ?: DEFAULT_COURSE_PALETTE_SEED }
    val wallpaperVisibilityPercent: Flow<Int> = dataStore.data.map {
        (it[Keys.WALLPAPER_VISIBILITY_PERCENT] ?: DEFAULT_WALLPAPER_VISIBILITY_PERCENT).coerceIn(0, 100)
    }

    suspend fun setCoursePaletteSeed(seed: Long) { dataStore.edit { it[Keys.COURSE_PALETTE_SEED] = seed } }
    suspend fun setWallpaperVisibilityPercent(percent: Int) {
        dataStore.edit { it[Keys.WALLPAPER_VISIBILITY_PERCENT] = percent.coerceIn(0, 100) }
    }

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
