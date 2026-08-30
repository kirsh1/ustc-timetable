package com.ustc.timetable.timetable.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App 范围唯一的 settings DataStore（A6 授权的 minimal helper 文件）。
 * 由 AppContainer 调用一次；测试使用自己的临时文件与 scope。
 */
internal fun createSettingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { File(context.filesDir, "datastore/ustc.settings.preferences_pb") },
    )
