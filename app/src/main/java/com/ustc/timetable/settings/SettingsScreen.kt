package com.ustc.timetable.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class SettingsCallbacks(
    val onBack: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onToggleShowNonCurrent: (Boolean) -> Unit = {},
    val onToggleWeeklySync: (Boolean) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRelogin: () -> Unit = {},
    val onClearLogin: () -> Unit = {},
    val onNotificationHint: () -> Unit = {},
    val onRestoreDefault: () -> Unit = {},
    val onApplyWorking: () -> Unit = {},
)

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onRequestRelogin: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationPermissionResult()
    }
    LaunchedEffect(Unit) { viewModel.onSyncSectionEntered() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.RequestNotificationPermission -> {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    viewModel.onNotificationPermissionRequestLaunched()
                }
                SettingsEvent.OpenNotificationSettings -> context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
                SettingsEvent.RequestRelogin -> onRequestRelogin()
            }
        }
    }
    SettingsScreen(
        state,
        SettingsCallbacks(
            onBack = onBack,
            onOpenProfile = onOpenProfile,
            onToggleShowNonCurrent = viewModel::onToggleShowNonCurrentWeek,
            onToggleWeeklySync = viewModel::onToggleWeeklySync,
            onSyncNow = viewModel::onSyncNow,
            onRelogin = viewModel::onReloginClick,
            onClearLogin = viewModel::onClearLogin,
            onNotificationHint = viewModel::onNotificationHintClick,
            onRestoreDefault = viewModel::restoreWorkingDefault,
            onApplyWorking = viewModel::applyWorkingToAcademicCurrent,
        ),
    )
}

@Composable
fun SettingsScreen(state: SettingsUiState, callbacks: SettingsCallbacks) {
    LazyColumn(Modifier.fillMaxSize().testTag("settings_list").padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("settings_back").clickable(onClick = callbacks.onBack).padding(end = 16.dp))
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
        }
        item { SectionTitle("课表") }
        item { SettingRow("学校作息时间", state.workingProfile?.name ?: "—", "working_profile", callbacks.onOpenProfile) }
        item { ToggleRow("显示非当前周课程", state.showNonCurrentWeek, "show_noncurrent", callbacks.onToggleShowNonCurrent) }
        item { if (state.canApplyWorkingToAcademicCurrent) SettingRow("应用为当前学期作息", "", "apply_working", callbacks.onApplyWorking) }
        item { SettingRow("恢复学校默认", "", "restore_default", callbacks.onRestoreDefault) }

        item { SectionTitle("同步") }
        item { SettingRow("上次同步时间", state.lastSyncText, "last_sync") }
        item { ToggleRow("每周静默同步", state.weeklySyncEnabled, "weekly_sync", callbacks.onToggleWeeklySync) }
        item { SettingRow("立即同步", if (state.syncNowEnabled) "" else "当前不可用", "sync_now", if (state.syncNowEnabled) callbacks.onSyncNow else null) }
        if (state.showNotificationDeniedHint) {
            item { SettingRow("通知权限未授予，课表变化将不会提醒", "", "notification_hint", callbacks.onNotificationHint) }
        }

        item { SectionTitle("学校账户") }
        item { SettingRow("登录状态", state.loginText, "login_status") }
        item { SettingRow("重新登录", if (state.reloginEnabled) "" else "当前不可用", "relogin", if (state.reloginEnabled) callbacks.onRelogin else null) }
        item { SettingRow("清除登录状态", "", "clear_login", callbacks.onClearLogin) }

        item { SectionTitle("关于") }
        item { SettingRow("数据与版本", "本地课表数据", "data_version") }
        item { SettingRow("App 版本", state.appVersion, "app_version") }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))

@Composable private fun SettingRow(title: String, value: String, tag: String, onClick: (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth().testTag(tag).then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(vertical = 12.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title); Spacer(Modifier.weight(1f)); if (value.isNotEmpty()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ToggleRow(title: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title); Switch(checked, onChecked, modifier = Modifier.testTag(tag))
    }
}
