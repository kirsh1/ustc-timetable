package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ustc.timetable.notification.NotificationPermissionController
import com.ustc.timetable.scheduleprofile.ProfileEditorScreen
import com.ustc.timetable.settings.NotificationsEnabledChecker
import com.ustc.timetable.settings.SettingsRoute
import com.ustc.timetable.settings.SettingsViewModel
import com.ustc.timetable.timetable.ui.AppRoot
import com.ustc.timetable.timetable.ui.FirstLaunchRoute
import com.ustc.timetable.timetable.ui.FirstLaunchViewModel
import com.ustc.timetable.timetable.ui.TimetableRoute
import com.ustc.timetable.timetable.ui.TimetableViewModel
import com.ustc.timetable.timetable.ui.minuteTicks
import java.time.Clock

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as TimetableApp).container
    private val clock: Clock = Clock.systemDefaultZone()
    private val activityViewModelFactory: ViewModelProvider.Factory by lazy {
        MainActivityViewModelFactory(
            container = container,
            clock = clock,
            notificationsEnabled = {
                NotificationManagerCompat.from(this).areNotificationsEnabled()
            },
        )
    }
    private val timetableViewModel: TimetableViewModel by viewModels { activityViewModelFactory }
    private val firstLaunchViewModel: FirstLaunchViewModel by viewModels { activityViewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { activityViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        // the lifecycle owner. Activity destruction now clears all three viewModelScopes.
        val timetable = timetableViewModel
        val firstLaunch = firstLaunchViewModel
        val settings = settingsViewModel
        setContent {
            MaterialTheme {
                val firstLaunchState by firstLaunch.state.collectAsState()
                AppRoot(
                    gate = firstLaunchState.gate,
                    firstLaunchContent = { FirstLaunchRoute(firstLaunch) },
                    timetableContent = { openSettings -> TimetableRoute(
                        viewModel = timetable,
                        manualRepository = container.manual,
                        clock = clock,
                        onSettingsClick = openSettings,
                    ) },
                    settingsContent = { back, openProfile -> SettingsRoute(
                        viewModel = settings,
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
            loginImportLauncher = null,
        )
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            settings = container.settings,
            profiles = container.profiles,
            semesters = container.semesters,
            permission = NotificationPermissionController(container.settings),
            notifications = NotificationsEnabledChecker(notificationsEnabled),
            session = null,
            weeklyScheduling = null,
            manualSync = null,
            reloginRuntimeAvailable = false,
            zoneId = java.time.ZoneId.systemDefault(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        else -> throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
    } as T
}
