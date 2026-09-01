package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.ustc.timetable.notification.NotificationPermissionController
import com.ustc.timetable.scheduleprofile.ProfileEditorScreen
import com.ustc.timetable.settings.NotificationsEnabledChecker
import com.ustc.timetable.settings.SettingsRoute
import com.ustc.timetable.settings.SettingsViewModel
import com.ustc.timetable.timetable.ui.TimetableRoute
import com.ustc.timetable.timetable.ui.TimetableViewModel
import com.ustc.timetable.timetable.ui.minuteTicks
import java.time.Clock

class MainActivity : ComponentActivity() {
    private enum class Destination { TIMETABLE, SETTINGS, PROFILE_EDITOR }

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
                var destination by rememberSaveable { mutableStateOf(Destination.TIMETABLE) }
                BackHandler(enabled = destination != Destination.TIMETABLE) {
                    destination = if (destination == Destination.PROFILE_EDITOR) Destination.SETTINGS else Destination.TIMETABLE
                }
                when (destination) {
                    Destination.TIMETABLE -> TimetableRoute(
                        viewModel = viewModel,
                        manualRepository = container.manual,
                        clock = clock,
                        onSettingsClick = { destination = Destination.SETTINGS },
                    )
                    Destination.SETTINGS -> SettingsRoute(
                        viewModel = settingsViewModel,
                        onBack = { destination = Destination.TIMETABLE },
                        onOpenProfile = { destination = Destination.PROFILE_EDITOR },
                    )
                    Destination.PROFILE_EDITOR -> {
                        val profile = settingsViewModel.state.collectAsState().value.workingProfile
                        if (profile != null) {
                            ProfileEditorScreen(
                                profile = profile,
                                onSave = { periods -> settingsViewModel.saveWorkingPeriods(periods); destination = Destination.SETTINGS },
                                onBack = { destination = Destination.SETTINGS },
                            )
                        }
                    }
                }
            }
        }
    }
}
