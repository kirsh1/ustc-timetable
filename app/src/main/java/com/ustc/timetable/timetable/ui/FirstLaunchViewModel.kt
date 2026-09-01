package com.ustc.timetable.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface FirstLaunchGate {
    data object Loading : FirstLaunchGate
    data object Empty : FirstLaunchGate
    data object Ready : FirstLaunchGate
}

data class FirstLaunchUiState(
    val gate: FirstLaunchGate = FirstLaunchGate.Loading,
    val isCreatingManualSemester: Boolean = false,
) {
    val actionsEnabled: Boolean get() = gate == FirstLaunchGate.Empty && !isCreatingManualSemester
}

sealed interface FirstLaunchEvent {
    data class ShowSnackbar(val message: String) : FirstLaunchEvent
}

fun interface LoginImportLauncher {
    fun launch()
}

class FirstLaunchViewModel(
    private val semesters: SemesterRepository,
    private val settings: SettingsStore,
    private val bundledOfficial: ScheduleProfile,
    private val clock: Clock,
    private val loginImportLauncher: LoginImportLauncher? = null,
    private val createInitialSemester: suspend (Semester, ScheduleProfile) -> Semester? =
        semesters::createInitialLocalSemesterIfEmpty,
) : ViewModel() {
    private val creating = MutableStateFlow(false)
    private val createMutex = Mutex()
    private val eventChannel = Channel<FirstLaunchEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<FirstLaunchUiState> = combine(
        semesters.observeSemesters(),
        creating,
    ) { rows, isCreating ->
        FirstLaunchUiState(
            gate = if (rows.isEmpty()) FirstLaunchGate.Empty else FirstLaunchGate.Ready,
            isCreatingManualSemester = isCreating,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FirstLaunchUiState(),
    )

    fun onLoginAndImport() {
        if (!state.value.actionsEnabled) return
        val launcher = loginImportLauncher
        if (launcher != null) launcher.launch()
        else eventChannel.trySend(FirstLaunchEvent.ShowSnackbar(IMPORT_UNAVAILABLE_MESSAGE))
    }

    fun onSkipManualCreation() {
        if (!state.value.actionsEnabled || !createMutex.tryLock()) return
        creating.value = true
        viewModelScope.launch {
            try {
                val base = SemesterDefaults.AUTUMN_2026(
                    id = UUID.randomUUID().toString(),
                    profileId = bundledOfficial.id,
                    now = clock.instant(),
                )
                val created = createInitialSemester(base, bundledOfficial)
                if (created != null) settings.setViewedSemesterId(created.id.value)
            } catch (_: Exception) {
                eventChannel.send(FirstLaunchEvent.ShowSnackbar(CREATE_FAILED_MESSAGE))
            } finally {
                creating.value = false
                createMutex.unlock()
            }
        }
    }

    companion object {
        const val IMPORT_UNAVAILABLE_MESSAGE = "导入功能将在门户接入后可用"
        const val CREATE_FAILED_MESSAGE = "创建失败，请重试"
    }
}
