package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TimetableApp).container
        val clock = Clock.systemDefaultZone()
        val viewModel = TimetableViewModel(
            semestersRepo = container.semesters,
            timetableRepo = container.timetable,
            manualRepo = container.manual,
            profilesRepo = container.profiles,
            settings = container.settings,
            clock = clock,
            nowTicks = minuteTicks(clock),
        )
        val firstLaunchViewModel = FirstLaunchViewModel(
            semesters = container.semesters,
            settings = container.settings,
            bundledOfficial = container.bundledOfficial,
            clock = clock,
            loginImportLauncher = null,
        )
        val settingsViewModel = SettingsViewModel(
            settings = container.settings,
            profiles = container.profiles,
            semesters = container.semesters,
            permission = NotificationPermissionController(container.settings),
            notifications = NotificationsEnabledChecker { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            session = null,
            weeklyScheduling = null,
            manualSync = null,
            reloginRuntimeAvailable = false,
            zoneId = java.time.ZoneId.systemDefault(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        setContent {
            MaterialTheme {
                val firstLaunchState by firstLaunchViewModel.state.collectAsState()
                AppRoot(
                    gate = firstLaunchState.gate,
                    firstLaunchContent = { FirstLaunchRoute(firstLaunchViewModel) },
                    timetableContent = { openSettings -> TimetableRoute(
                        viewModel = viewModel,
                        manualRepository = container.manual,
                        clock = clock,
                        onSettingsClick = openSettings,
                    ) },
                    settingsContent = { back, openProfile -> SettingsRoute(
                        viewModel = settingsViewModel,
                        onBack = back,
                        onOpenProfile = openProfile,
                    ) },
                    profileEditorContent = { back ->
                        val profile = settingsViewModel.state.collectAsState().value.workingProfile
                        if (profile != null) {
                            ProfileEditorScreen(
                                profile = profile,
                                onSave = { periods -> settingsViewModel.saveWorkingPeriods(periods); back() },
                                onBack = back,
                            )
                        }
                    },
                )
            }
        }
    }
}
