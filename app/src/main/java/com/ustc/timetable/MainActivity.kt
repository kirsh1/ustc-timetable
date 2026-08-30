package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
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
        setContent {
            MaterialTheme {
                TimetableRoute(viewModel)
            }
        }
    }
}
