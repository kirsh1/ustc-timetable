package com.ustc.timetable.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.school.ustc.auth.WebViewLoginContract
import com.ustc.timetable.semester.SemesterSwitcherSheet
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.ManualSyncState
import com.ustc.timetable.timetable.ui.AuthExpiredDialog
import com.ustc.timetable.timetable.ui.handleManualSyncLoginResult
import com.ustc.timetable.ui.AppIcons
import com.ustc.timetable.ui.theme.LocalTimetableSpacing
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.appearance.AndroidWallpaperUriGrants
import com.ustc.timetable.appearance.WallpaperSelection
import com.ustc.timetable.appearance.WallpaperRuntimeState

data class SettingsCallbacks(
    val onBack: () -> Unit = {},
    val onOpenSemesterSwitcher: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onToggleShowNonCurrent: (Boolean) -> Unit = {},
    val onToggleWeeklySync: (Boolean) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRelogin: () -> Unit = {},
    val onClearLogin: () -> Unit = {},
    val onNotificationHint: () -> Unit = {},
    val onRestoreDefault: () -> Unit = {},
    val onApplyWorking: () -> Unit = {},
    val onAppearanceSelected: (AppearanceMode) -> Unit = {},
    val onChooseWallpaper: () -> Unit = {},
    val onClearWallpaper: () -> Unit = {},
)

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    manualSyncController: ManualSyncController? = null,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onRequestRelogin: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val unavailableWallpaperUri by WallpaperRuntimeState.unavailableUri.collectAsState()
    val manualSyncState = manualSyncController?.state?.collectAsState()?.value ?: ManualSyncState.Idle
    val context = LocalContext.current
    val wallpaperGrants = remember(context) { AndroidWallpaperUriGrants(context.contentResolver) }
    var semesterSheetOpen by remember { mutableStateOf(false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val selected = WallpaperSelection.resolve(state.timetableWallpaperUri, uri?.toString(), wallpaperGrants)
        if (selected != null && selected != state.timetableWallpaperUri) {
            WallpaperRuntimeState.clear()
            viewModel.onWallpaperSelected(selected)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationPermissionResult()
    }
    val reloginLauncher = rememberLauncherForActivityResult(WebViewLoginContract()) { successful ->
        if (successful) viewModel.onReloginResult(true)
        manualSyncController?.let { handleManualSyncLoginResult(it, successful) }
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
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
                SettingsEvent.RequestRelogin -> onRequestRelogin?.invoke() ?: reloginLauncher.launch(Unit)
            }
        }
    }
    SettingsScreen(
        state = state.copy(wallpaperUnavailable = state.timetableWallpaperUri != null && unavailableWallpaperUri == state.timetableWallpaperUri),
        callbacks = SettingsCallbacks(
            onBack = onBack,
            onOpenSemesterSwitcher = { semesterSheetOpen = true },
            onOpenProfile = onOpenProfile,
            onToggleShowNonCurrent = viewModel::onToggleShowNonCurrentWeek,
            onToggleWeeklySync = viewModel::onToggleWeeklySync,
            onSyncNow = viewModel::onSyncNow,
            onRelogin = viewModel::onReloginClick,
            onClearLogin = viewModel::onClearLogin,
            onNotificationHint = viewModel::onNotificationHintClick,
            onRestoreDefault = viewModel::restoreWorkingDefault,
            onApplyWorking = viewModel::applyWorkingToAcademicCurrent,
            onAppearanceSelected = viewModel::onAppearanceModeSelected,
            onChooseWallpaper = { wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClearWallpaper = {
                WallpaperSelection.clear(state.timetableWallpaperUri, wallpaperGrants)
                WallpaperRuntimeState.clear()
                viewModel.onWallpaperCleared()
            },
        ),
        manualSyncState = manualSyncState,
    )
    val viewed = state.viewedSemesterId
    if (semesterSheetOpen && viewed != null) {
        SemesterSwitcherSheet(
            semesters = state.availableSemesters,
            viewedSemesterId = viewed,
            onSelect = {
                semesterSheetOpen = false
                viewModel.onSemesterSelected(it)
            },
            onDismiss = { semesterSheetOpen = false },
        )
    }
    if (manualSyncState == ManualSyncState.AwaitingReauth && manualSyncController != null) {
        AuthExpiredDialog(
            onCancel = manualSyncController::onCancelAuthExpired,
            onRelogin = { reloginLauncher.launch(Unit) },
        )
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    manualSyncState: ManualSyncState = ManualSyncState.Idle,
) {
    var themeSheetOpen by remember { mutableStateOf(false) }
    var wallpaperSheetOpen by remember { mutableStateOf(false) }
    val spacing = LocalTimetableSpacing.current
    val viewedSemester = state.availableSemesters.firstOrNull { it.id == state.viewedSemesterId }
        ?: state.availableSemesters.firstOrNull { it.isCurrentAcademicSemester }
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings_list").padding(horizontal = spacing.pageHorizontal),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = callbacks.onBack, modifier = Modifier.testTag("settings_back")) {
                    Icon(AppIcons.Back, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            SettingsSection("外观", "appearance", AppIcons.Appearance) {
                SettingsNavigationRow(
                    "主题",
                    appearanceLabel(state.appearanceMode),
                    "appearance_theme",
                ) { themeSheetOpen = true }
                SettingsDivider()
                SettingsNavigationRow(
                    "课表壁纸",
                    when {
                        state.wallpaperUnavailable -> "壁纸不可用，请重新选择"
                        state.timetableWallpaperUri == null -> "未设置"
                        else -> "已选择"
                    },
                    "timetable_wallpaper",
                ) { if (state.timetableWallpaperUri == null) callbacks.onChooseWallpaper() else wallpaperSheetOpen = true }
            }
        }
        item {
            SettingsSection("课表", "timetable", AppIcons.Schedule) {
                SettingsNavigationRow("当前查看学期", viewedSemester?.displayName ?: "—", "viewed_semester", callbacks.onOpenSemesterSwitcher)
                SettingsDivider()
                SettingsNavigationRow("学校作息时间", state.workingProfile?.name ?: "—", "working_profile", callbacks.onOpenProfile)
                SettingsDivider()
                SettingsToggleRow("显示非当前周课程", state.showNonCurrentWeek, "show_noncurrent", callbacks.onToggleShowNonCurrent)
                if (state.canApplyWorkingToAcademicCurrent) {
                    SettingsDivider()
                    SettingsActionRow("应用为当前学期作息", "apply_working", callbacks.onApplyWorking)
                }
                SettingsDivider()
                SettingsActionRow("恢复学校默认", "restore_default", callbacks.onRestoreDefault)
            }
        }
        item {
            SettingsSection("同步", "sync", AppIcons.Sync) {
                SettingsInfoRow("上次同步时间", state.lastSyncText, "last_sync")
                SettingsDivider()
                SettingsToggleRow("每周静默同步", state.weeklySyncEnabled, "weekly_sync", callbacks.onToggleWeeklySync)
                SettingsDivider()
                SettingsSyncRow(state.syncNowEnabled, manualSyncState, callbacks.onSyncNow)
                if (state.showNotificationDeniedHint) {
                    SettingsDivider()
                    SettingsNavigationRow(
                        "通知权限未授予，课表变化将不会提醒",
                        "点击打开系统通知设置",
                        "notification_hint",
                        callbacks.onNotificationHint,
                    )
                }
            }
        }
        item {
            SettingsSection("学校账户", "account", AppIcons.Account) {
                SettingsInfoRow("登录状态", state.loginText, "login_status")
                SettingsDivider()
                SettingsNavigationRow("重新登录", if (state.reloginEnabled) null else "当前不可用", "relogin", if (state.reloginEnabled) callbacks.onRelogin else null)
                SettingsDivider()
                SettingsDangerRow("清除登录状态", "clear_login", callbacks.onClearLogin)
            }
        }
        item {
            SettingsSection("关于", "about", AppIcons.Info) {
                SettingsInfoRow("数据与版本", "本地课表数据", "data_version")
                SettingsDivider()
                SettingsInfoRow("App 版本", state.appVersion, "app_version")
            }
        }
    }
    if (themeSheetOpen) {
        ModalBottomSheet(onDismissRequest = { themeSheetOpen = false }) {
            Text("主题", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            AppearanceMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(appearanceLabel(mode)) },
                    leadingContent = { RadioButton(selected = state.appearanceMode == mode, onClick = null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        callbacks.onAppearanceSelected(mode)
                        themeSheetOpen = false
                    }.testTag("appearance_option:${mode.name}"),
                )
            }
        }
    }
    if (wallpaperSheetOpen) {
        ModalBottomSheet(onDismissRequest = { wallpaperSheetOpen = false }) {
            SettingsActionRow("更换壁纸", "replace_wallpaper") { wallpaperSheetOpen = false; callbacks.onChooseWallpaper() }
            SettingsActionRow("清除壁纸", "clear_wallpaper") { wallpaperSheetOpen = false; callbacks.onClearWallpaper() }
        }
    }
}

internal fun appearanceLabel(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.LIGHT -> "浅色"
    AppearanceMode.DARK -> "深色"
    AppearanceMode.SYSTEM -> "跟随系统"
}

@Composable
private fun SettingsSection(title: String, tag: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = LocalTimetableSpacing.current.sectionGap)) {
        Row(
            Modifier.padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("settings_group:$tag"),
        ) { Column { content() } }
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(
    modifier = Modifier.padding(start = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun SettingsNavigationRow(title: String, supporting: String?, tag: String, onClick: (() -> Unit)?) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { value -> ({ Text(value) }) },
        trailingContent = if (onClick == null) null else ({ Icon(AppIcons.NavigateNext, null) }),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).then(clickModifier),
    )
}

@Composable
private fun SettingsInfoRow(title: String, value: String, tag: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    )
}

@Composable
private fun SettingsActionRow(title: String, tag: String, onClick: () -> Unit) =
    SettingsNavigationRow(title, null, tag, onClick)

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked, onChecked, modifier = Modifier.testTag(tag)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

@Composable
private fun SettingsSyncRow(enabled: Boolean, syncState: ManualSyncState, onClick: () -> Unit) {
    val syncing = syncState == ManualSyncState.Syncing
    ListItem(
        headlineContent = { Text("立即同步") },
        supportingContent = if (enabled) null else ({ Text("当前不可用") }),
        trailingContent = {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(22.dp).testTag("sync_progress"), strokeWidth = 2.dp)
            } else if (enabled) {
                IconButton(onClick = onClick, modifier = Modifier.testTag("sync_action")) {
                    Icon(AppIcons.Refresh, contentDescription = "立即同步")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("sync_now")
            .then(if (enabled && !syncing) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun SettingsDangerRow(title: String, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.error) },
        leadingContent = { Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(onClick = onClick),
    )
}
