package com.ustc.timetable.manual

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.layout.LongPressResolver
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import com.ustc.timetable.timetable.ui.TimetableUiState
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal sealed interface ManualEditorTarget {
    val semesterId: SemesterId
    val profileId: ProfileId
    val viewedWeek: Int

    data class New(
        override val semesterId: SemesterId,
        override val profileId: ProfileId,
        override val viewedWeek: Int,
        val weekday: Int,
        val startTime: LocalTime,
    ) : ManualEditorTarget

    data class Edit(
        override val semesterId: SemesterId,
        override val profileId: ProfileId,
        override val viewedWeek: Int,
        val manualItemId: ManualItemId,
    ) : ManualEditorTarget
}

internal fun createNewManualEditorTarget(
    state: TimetableUiState,
    pageWeek: Int,
    columnFraction: Float,
    yFraction: Float,
): ManualEditorTarget.New? {
    val semester = state.semester ?: return null
    val profile = state.profile ?: return null
    val resolved = LongPressResolver.resolve(
        columnFraction = columnFraction,
        yFraction = yFraction,
        axis = WeeklyTimetableLayout.axisOf(profile),
    )
    return ManualEditorTarget.New(
        semesterId = semester.id,
        profileId = ProfileId(profile.id),
        viewedWeek = pageWeek,
        weekday = resolved.weekday,
        startTime = resolved.snappedStart,
    )
}

internal fun createEditManualEditorTarget(
    state: TimetableUiState,
    id: ManualItemId,
    pageWeek: Int,
): ManualEditorTarget.Edit? {
    val semester = state.semester ?: return null
    val profile = state.profile ?: return null
    val item = state.manualItemsById[id] ?: return null
    if (item.semesterId != semester.id) return null
    return ManualEditorTarget.Edit(
        semesterId = semester.id,
        profileId = ProfileId(profile.id),
        viewedWeek = pageWeek,
        manualItemId = id,
    )
}

internal fun resolveManualEditorInitial(
    target: ManualEditorTarget,
    state: TimetableUiState,
): ManualEditorInitial? {
    val semester = state.semester ?: return null
    val profile = state.profile ?: return null
    if (semester.id != target.semesterId || profile.id != target.profileId.value) return null
    return when (target) {
        is ManualEditorTarget.New -> ManualEditorInitial.New(
            weekday = target.weekday,
            startTime = target.startTime,
            viewedWeek = target.viewedWeek,
        )
        is ManualEditorTarget.Edit -> {
            val item = state.manualItemsById[target.manualItemId] ?: return null
            if (item.semesterId != semester.id) return null
            ManualEditorInitial.Edit(item, target.viewedWeek)
        }
    }
}

internal class ManualOverlayState {
    var selectedSchoolMeetingId by mutableStateOf<MeetingId?>(null)
        private set
    var manualTarget by mutableStateOf<ManualEditorTarget?>(null)
        private set

    fun openSchool(id: MeetingId) {
        manualTarget = null
        selectedSchoolMeetingId = id
    }

    fun openManual(target: ManualEditorTarget) {
        selectedSchoolMeetingId = null
        manualTarget = target
    }

    fun dismissSchool() {
        selectedSchoolMeetingId = null
    }

    fun dismissManual() {
        manualTarget = null
    }
}

internal class ManualEditorSession(
    target: ManualEditorTarget,
    semester: Semester,
    profile: ScheduleProfile,
    existingItem: ManualScheduleItem?,
    manual: ManualItemRepository,
    clock: Clock,
    private val onDismiss: () -> Unit,
) {
    private val operationStarted = AtomicBoolean(false)

    val editor = ManualItemEditorViewModel(
        manual = manual,
        semester = semester,
        profile = profile,
        initial = when (target) {
            is ManualEditorTarget.New -> ManualEditorInitial.New(
                target.weekday,
                target.startTime,
                target.viewedWeek,
            )
            is ManualEditorTarget.Edit -> ManualEditorInitial.Edit(
                requireNotNull(existingItem) { "edit target missing item: ${target.manualItemId}" },
                target.viewedWeek,
            )
        },
        clock = clock,
    )

    suspend fun save(): Boolean = runOnce { editor.save() }

    suspend fun delete(): Boolean = runOnce { editor.delete() }

    private suspend fun runOnce(operation: suspend () -> Unit): Boolean {
        if (!operationStarted.compareAndSet(false, true)) return false
        try {
            operation()
            onDismiss()
            return true
        } catch (error: Throwable) {
            operationStarted.set(false)
            throw error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualEditorHost(
    target: ManualEditorTarget,
    semester: Semester,
    profile: ScheduleProfile,
    existingItem: ManualScheduleItem?,
    manual: ManualItemRepository,
    clock: Clock,
    onDismiss: () -> Unit,
) {
    val session = remember(target) {
        ManualEditorSession(target, semester, profile, existingItem, manual, clock, onDismiss)
    }
    val draft by session.editor.draft.collectAsState()
    val scope = rememberCoroutineScope()
    var operationInFlight by remember(target) { mutableStateOf(false) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    val validationError = session.editor.validationError()

    ManualItemEditorSheet(
        draft = draft,
        viewedWeek = target.viewedWeek,
        totalWeeks = semester.totalWeeks,
        dayWindow = profile.dayWindow(),
        validationError = validationError,
        canDelete = session.editor.canDelete,
        onDraftChange = { next -> session.editor.update { next } },
        onSave = {
            if (!operationInFlight) {
                operationInFlight = true
                scope.launch {
                    try {
                        session.save()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        operationInFlight = false
                    }
                }
            }
        },
        onDeleteRequest = { if (!operationInFlight) confirmDelete = true },
        onDismiss = onDismiss,
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除手动项目") },
            text = { Text("确认删除这个手动项目吗？") },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag("delete_cancel"),
                ) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!operationInFlight) {
                            operationInFlight = true
                            scope.launch {
                                try {
                                    if (session.delete()) confirmDelete = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    operationInFlight = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("delete_confirm"),
                ) { Text("删除") }
            },
        )
    }
}
