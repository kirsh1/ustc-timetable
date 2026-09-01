package com.ustc.timetable.manual

import androidx.lifecycle.ViewModel
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Clock
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.min

enum class WeekMode {
    CURRENT_ONLY,
    CONTINUOUS,
    CUSTOM,
}

data class EditorDraft(
    val title: String = "",
    val location: String = "",
    val note: String = "",
    val weekday: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val mode: WeekMode = WeekMode.CURRENT_ONLY,
    val continuousStart: Int,
    val continuousEnd: Int,
    val customWeeks: Set<Int> = emptySet(),
)

sealed interface ManualEditorInitial {
    val viewedWeek: Int

    data class New(
        val weekday: Int,
        val startTime: LocalTime,
        override val viewedWeek: Int,
    ) : ManualEditorInitial

    data class Edit(
        val item: ManualScheduleItem,
        override val viewedWeek: Int,
    ) : ManualEditorInitial
}

class ManualItemEditorViewModel(
    private val manual: ManualItemRepository,
    private val semester: Semester,
    private val profile: ScheduleProfile,
    private val initial: ManualEditorInitial,
    private val clock: Clock,
) : ViewModel() {

    private val existing: ManualScheduleItem? = (initial as? ManualEditorInitial.Edit)?.item

    private val _draft = MutableStateFlow(initialDraft())
    val draft: StateFlow<EditorDraft> = _draft.asStateFlow()

    val canDelete: Boolean
        get() = existing != null

    fun update(transform: (EditorDraft) -> EditorDraft) {
        _draft.update(transform)
    }

    fun validationError(): String? =
        validateEditorDraft(_draft.value, semester, profile, initial.viewedWeek)

    fun buildItem(): ManualScheduleItem {
        val draft = _draft.value
        val error = validateEditorDraft(draft, semester, profile, initial.viewedWeek)
        check(error == null) { error ?: "invalid editor draft" }

        val weekPattern = when (draft.mode) {
            WeekMode.CURRENT_ONLY -> WeekPattern.of(initial.viewedWeek)
            WeekMode.CONTINUOUS -> WeekPattern.range(draft.continuousStart, draft.continuousEnd)
            WeekMode.CUSTOM -> WeekPattern.of(*draft.customWeeks.sorted().toIntArray())
        }
        val now = clock.instant()
        val old = existing
        return ManualScheduleItem(
            id = old?.id ?: ManualItemId(UUID.randomUUID().toString()),
            semesterId = old?.semesterId ?: semester.id,
            title = draft.title.trim(),
            weekday = draft.weekday,
            startTime = draft.startTime,
            endTime = draft.endTime,
            weekPattern = weekPattern,
            location = draft.location.trim().ifBlank { null },
            note = draft.note.trim().ifBlank { null },
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            source = ItemSource.MANUAL,
        )
    }

    suspend fun save() {
        val item = buildItem()
        if (existing == null) manual.add(item) else manual.update(item)
    }

    suspend fun delete() {
        val item = existing ?: error("new editor cannot delete")
        check(manual.delete(item.id) == 1) { "manual item missing: ${item.id}" }
    }

    private fun initialDraft(): EditorDraft = when (val value = initial) {
        is ManualEditorInitial.New -> EditorDraft(
            weekday = value.weekday,
            startTime = value.startTime,
            endTime = defaultEnd(value.startTime, profile.dayWindow().endInclusive),
            mode = WeekMode.CURRENT_ONLY,
            continuousStart = value.viewedWeek,
            continuousEnd = value.viewedWeek,
        )

        is ManualEditorInitial.Edit -> editDraft(value)
    }

    private fun editDraft(value: ManualEditorInitial.Edit): EditorDraft {
        val item = value.item
        require(item.semesterId == semester.id) {
            "manual item semester ${item.semesterId} does not match editor semester ${semester.id}"
        }
        require(item.source == ItemSource.MANUAL) { "editor only accepts MANUAL items" }

        val selected = (1..semester.totalWeeks).filter(item.weekPattern::contains)
        require((semester.totalWeeks + 1..63).none(item.weekPattern::contains)) {
            "manual item has weeks outside semester: ${item.weekPattern.format()}"
        }
        val contiguous = selected.size >= 2 && selected == (selected.first()..selected.last()).toList()
        val mode = when {
            selected.size == 1 && selected.single() == value.viewedWeek -> WeekMode.CURRENT_ONLY
            contiguous -> WeekMode.CONTINUOUS
            else -> WeekMode.CUSTOM
        }
        return EditorDraft(
            title = item.title,
            location = item.location.orEmpty(),
            note = item.note.orEmpty(),
            weekday = item.weekday,
            startTime = item.startTime,
            endTime = item.endTime,
            mode = mode,
            continuousStart = selected.first(),
            continuousEnd = selected.last(),
            customWeeks = selected.toSet(),
        )
    }
}

private fun defaultEnd(start: LocalTime, endInclusive: LocalTime): LocalTime {
    val candidateSecond = start.toSecondOfDay().toLong() + 45L * 60L
    val boundedSecond = min(candidateSecond, endInclusive.toSecondOfDay().toLong())
    return LocalTime.ofSecondOfDay(boundedSecond)
}

internal fun validateEditorDraft(
    draft: EditorDraft,
    semester: Semester,
    profile: ScheduleProfile,
    viewedWeek: Int,
): String? {
    if (draft.title.trim().isEmpty()) return "请填写标题"
    if (draft.weekday !in 1..7) return "请选择星期"
    if (draft.endTime <= draft.startTime) return "结束需晚于开始"
    val dayWindow = profile.dayWindow()
    if (draft.startTime !in dayWindow || draft.endTime !in dayWindow) return "时间需在作息窗口内"
    return when (draft.mode) {
        WeekMode.CURRENT_ONLY ->
            if (viewedWeek !in 1..semester.totalWeeks) "周次无效" else null

        WeekMode.CONTINUOUS -> when {
            draft.continuousStart !in 1..semester.totalWeeks ||
                draft.continuousEnd !in 1..semester.totalWeeks -> "周次无效"
            draft.continuousStart > draft.continuousEnd -> "连续周次起始需不晚于结束"
            else -> null
        }

        WeekMode.CUSTOM -> when {
            draft.customWeeks.isEmpty() -> "请选择周次"
            draft.customWeeks.any { it !in 1..semester.totalWeeks } -> "周次无效"
            else -> null
        }
    }
}
