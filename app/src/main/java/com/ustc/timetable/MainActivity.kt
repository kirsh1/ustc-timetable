package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ustc.timetable.notification.NotificationPermissionController
import com.ustc.timetable.scheduleprofile.ProfileEditorScreen
import com.ustc.timetable.settings.NotificationsEnabledChecker
import com.ustc.timetable.settings.SettingsRoute
import com.ustc.timetable.settings.SettingsViewModel
import com.ustc.timetable.settings.SettingsManualSync
import com.ustc.timetable.settings.SettingsSessionAccess
import com.ustc.timetable.settings.WeeklySyncScheduling
import com.ustc.timetable.semester.ImportFlowViewModel
import com.ustc.timetable.school.ustc.auth.WebViewLoginContract
import com.ustc.timetable.sync.SyncScheduler
import com.ustc.timetable.timetable.ui.AppRoot
import com.ustc.timetable.timetable.ui.FirstLaunchRoute
import com.ustc.timetable.timetable.ui.FirstLaunchViewModel
import com.ustc.timetable.timetable.ui.LoginImportLauncher
import com.ustc.timetable.timetable.ui.TimetableRoute
import com.ustc.timetable.timetable.ui.TimetableViewModel
import com.ustc.timetable.timetable.ui.minuteTicks
import java.time.Clock
import kotlinx.coroutines.flow.first
import com.ustc.timetable.ui.theme.AppBackgroundLayer
import com.ustc.timetable.ui.theme.TimetableTheme
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.appearance.ResolvedAppearance
import com.ustc.timetable.appearance.resolveAppearance
import com.ustc.timetable.appearance.WallpaperRuntimeState

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as TimetableApp).container
    private val clock: Clock = Clock.systemDefaultZone()
    private val firstLaunchLoginLauncher = registerForActivityResult(WebViewLoginContract()) { successful ->
        if (successful) importFlowViewModel.startLoginImport()
    }
    private val activityViewModelFactory: ViewModelProvider.Factory by lazy {
        MainActivityViewModelFactory(
            container = container,
            clock = clock,
            notificationsEnabled = {
                NotificationManagerCompat.from(this).areNotificationsEnabled()
            },
            loginImportLauncher = { firstLaunchLoginLauncher.launch(Unit) },
            weeklyScheduling = WeeklySyncScheduling { enabled -> SyncScheduler.setEnabled(this, enabled) },
        )
    }
    private val timetableViewModel: TimetableViewModel by viewModels { activityViewModelFactory }
    private val firstLaunchViewModel: FirstLaunchViewModel by viewModels { activityViewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { activityViewModelFactory }
    private val importFlowViewModel: ImportFlowViewModel by viewModels { activityViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_UstcTimetable)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        // Preserve the existing eager Activity-scoped graph while making ViewModelStore
        // the lifecycle owner. Activity destruction now clears its ViewModelStore scopes.
        val timetable = timetableViewModel
        val firstLaunch = firstLaunchViewModel
        val settings = settingsViewModel
        val importFlow = importFlowViewModel
        setContent {
            val appearanceMode by container.settings.appearanceMode.collectAsState(initial = AppearanceMode.LIGHT)
            val wallpaperUri by container.settings.timetableWallpaperUri.collectAsState(initial = null)
            val wallpaperVisibility by container.settings.wallpaperVisibilityPercent.collectAsState(initial = com.ustc.timetable.timetable.data.DEFAULT_WALLPAPER_VISIBILITY_PERCENT)
            val resolvedAppearance = resolveAppearance(appearanceMode, isSystemInDarkTheme())
            SideEffect {
                val style = if (resolvedAppearance == ResolvedAppearance.DARK) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TimetableTheme(appearanceMode) {
                val firstLaunchState by firstLaunch.state.collectAsState()
                AppRoot(
                    gate = firstLaunchState.gate,
                    firstLaunchContent = { FirstLaunchRoute(firstLaunch, importFlow) },
                    timetableContent = { openSettings ->
                        AppBackgroundLayer(
                            wallpaperUri = wallpaperUri,
                            wallpaperVisibilityPercent = wallpaperVisibility,
                            onWallpaperUnavailable = { wallpaperUri?.let(WallpaperRuntimeState::reportUnavailable) },
                            onWallpaperAvailable = { wallpaperUri?.let(WallpaperRuntimeState::clear) },
                        ) {
                            TimetableRoute(
                                viewModel = timetable,
                                manualRepository = container.manual,
                                clock = clock,
                                manualSyncController = container.ustcPortalRuntime.manualSyncController,
                                onSettingsClick = openSettings,
                            )
                        }
                    },
                    settingsContent = { back, openProfile -> SettingsRoute(
                        viewModel = settings,
                        manualSyncController = container.ustcPortalRuntime.manualSyncController,
                        importFlow = importFlow,
                        onBack = back,
                        onOpenProfile = openProfile,
                    ) },
                    profileEditorContent = { back ->
                        val profile = settings.state.collectAsState().value.workingProfile
                        if (profile != null) {
                            ProfileEditorScreen(
                                profile = profile,
                                onSave = { periods -> settings.saveWorkingPeriods(periods); back() },
                                onBack = back,
                            )
                        }
                    },
                )
            }
        }
    }
}

private class MainActivityViewModelFactory(
    private val container: AppContainer,
    private val clock: Clock,
    private val notificationsEnabled: () -> Boolean,
    private val loginImportLauncher: LoginImportLauncher,
    private val weeklyScheduling: WeeklySyncScheduling,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TimetableViewModel::class.java) -> TimetableViewModel(
            semestersRepo = container.semesters,
            timetableRepo = container.timetable,
            manualRepo = container.manual,
            profilesRepo = container.profiles,
            settings = container.settings,
            clock = clock,
            nowTicks = minuteTicks(clock),
        )
        modelClass.isAssignableFrom(FirstLaunchViewModel::class.java) -> FirstLaunchViewModel(
            semesters = container.semesters,
            settings = container.settings,
            bundledOfficial = container.bundledOfficial,
            clock = clock,
            loginImportLauncher = loginImportLauncher,
        )
        modelClass.isAssignableFrom(ImportFlowViewModel::class.java) -> {
            val runtime = container.ustcPortalRuntime
            ImportFlowViewModel(
                portal = runtime.portalSource,
                selectionParser = runtime.courseParser,
                timetableParser = runtime.timetableParser,
                metaParser = runtime.semesterMetaParser,
                normalizer = runtime.normalizer,
                db = container.db,
                settings = container.settings,
                workingProfile = { container.profiles.observeWorking().first() },
                clock = Clock.systemUTC(),
            )
        }
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            settings = container.settings,
            profiles = container.profiles,
            semesters = container.semesters,
            permission = NotificationPermissionController(container.settings),
            notifications = NotificationsEnabledChecker(notificationsEnabled),
            session = object : SettingsSessionAccess {
                override suspend fun load() = container.ustcPortalRuntime.sessionStore.load()
                override suspend fun clear() = container.ustcPortalRuntime.sessionStore.clear()
            },
            weeklyScheduling = weeklyScheduling,
            manualSync = SettingsManualSync(container.ustcPortalRuntime.manualSyncController::start),
            reloginRuntimeAvailable = true,
            zoneId = java.time.ZoneId.systemDefault(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        else -> throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    } as T
}
