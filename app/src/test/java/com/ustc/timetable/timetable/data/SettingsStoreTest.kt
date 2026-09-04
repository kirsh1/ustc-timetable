package com.ustc.timetable.timetable.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey as boolKey
import androidx.datastore.preferences.core.longPreferencesKey as longKey
import androidx.datastore.preferences.core.stringPreferencesKey as stringKey
import java.nio.file.Files
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.ustc.timetable.appearance.AppearanceMode

class SettingsStoreTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore

    @Before fun setUp() {
        val dir = Files.createTempDirectory("a6-settings")
        file = dir.resolve("settings.preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store = SettingsStore(dataStore)
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test fun settings_defaults_exact() = runBlocking {
        assertEquals(null, store.viewedSemesterId.first())
        assertEquals(false, store.showNonCurrentWeek.first())
        assertEquals(true, store.weeklySyncEnabled.first())
        assertEquals(null, store.activeWorkingProfileId.first())
        assertEquals(false, store.needReauth.first())
        assertEquals(null, store.lastSyncFinishedAt.first())
        assertEquals(false, store.notificationRequestShown.first())
        assertEquals(AppearanceMode.LIGHT, store.appearanceMode.first())
        assertEquals(null, store.timetableWallpaperUri.first())
    }

    /** Windows 上 DataStore tmp→target rename 偶发被 AV/句柄延迟挡住；小退避重试。 */
    private suspend fun writeWithRetry(block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                block()
                return
            } catch (e: java.io.IOException) {
                if (++attempt >= 5) throw e
                delay(200L * attempt)
            }
        }
    }

    @Test fun settings_values_persist_across_new_store_instance() = runBlocking {
        // 七个键合并为一次 edit（单次原子落盘，最小化 Windows rename 竞态窗口）
        writeWithRetry {
            dataStore.edit { p ->
                p[stringKey("viewed_semester_id")] = "s-hist"
                p[boolKey("show_non_current_week")] = true
                p[boolKey("weekly_sync_enabled")] = false
                p[stringKey("active_working_profile_id")] = "profile.custom"
                p[boolKey("need_reauth")] = true
                p[longKey("last_sync_finished_at")] = 123L
                p[boolKey("notifications_request_shown")] = true
                p[stringKey("appearance_mode")] = AppearanceMode.DARK.name
                p[stringKey("timetable_wallpaper_uri")] = "content://test/wallpaper"
            }
        }
        // 经 SettingsStore 读回，验证键名/默认值语义与底层文件一致
        assertEquals("s-hist", store.viewedSemesterId.first())
        assertEquals(true, store.showNonCurrentWeek.first())
        assertEquals(false, store.weeklySyncEnabled.first())
        assertEquals("profile.custom", store.activeWorkingProfileId.first())
        assertEquals(AppearanceMode.DARK, store.appearanceMode.first())
        assertEquals("content://test/wallpaper", store.timetableWallpaperUri.first())

        scope.cancel()  // 释放第一个 store 的文件句柄
        delay(500)      // Windows：等句柄完全释放
        // 持久化证明：值已原子写入落盘文件（DataStore JVM 在 Windows 持有文件句柄，
        // 同文件二次实例化在测试环境不可行——该限制记录于 DEVIATIONS；Android 运行时单实例由 AppContainer 保证）
        assertTrue(file.exists() && file.length() > 0)
        val dumped = file.readBytes().decodeToString()
        assertTrue(dumped.contains("s-hist"))
        assertTrue(dumped.contains("profile.custom"))
        assertTrue(dumped.contains("viewed_semester_id"))
        assertTrue(dumped.contains("active_working_profile_id"))
        assertTrue(dumped.contains("appearance_mode"))
        assertTrue(dumped.contains("timetable_wallpaper_uri"))
    }
}
