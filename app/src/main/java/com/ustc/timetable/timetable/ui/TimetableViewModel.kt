package com.ustc.timetable.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.TimetableRepository
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekCalculator
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.PlacedBlock
import com.ustc.timetable.timetable.layout.TimedBlock
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

/** B3 authoritative UI state（frozen §5.1 + B3 gate）。isAcademicCurrentViewed 与 canSyncViewed 语义分离。 */
data class TimetableUiState(
    val semester: Semester? = null,
    val viewedWeek: Int = 1,
    val weekDates: LocalDateRange? = null,
    val profile: ScheduleProfile? = null,
    val placedSchool: List<PlacedBlock> = emptyList(),
    val placedManual: List<PlacedBlock> = emptyList(),
    val showNonCurrentWeek: Boolean = false,
    val today: LocalDate? = null,
    val nowLine: LocalTime? = null,
    val isAcademicCurrentViewed: Boolean = false,
    val canSyncViewed: Boolean = false,
    val isLoading: Boolean = true,
)

/** viewed semester 选择优先级（A6 语义）：valid viewedId → academic-current → latest startDate → null。只读。 */
internal fun selectViewedSemester(semesters: List<Semester>, viewedId: String?): Semester? {
    viewedId?.let { id -> semesters.firstOrNull { it.id.value == id }?.let { return it } }
    semesters.firstOrNull { it.isCurrentAcademicSemester }?.let { return it }
    return semesters.maxByOrNull { it.startDate }
}

/** 周过滤（frozen §4.5）：false → 只留 contains(viewedWeek)；true → 全保留（淡化交 B2）。 */
internal fun weekFilter(blocks: List<TimedBlock>, viewedWeek: Int, showNonCurrentWeek: Boolean): List<TimedBlock> =
    if (showNonCurrentWeek) blocks else blocks.filter { it.weeks.contains(viewedWeek) }

/** “现在”线三条件（frozen §4.6）：当前学期 && 查看周==自然周 && today ∈ weekDates；否则 null。不检查 portalLinked。 */
object NowLinePolicy {
    fun line(
        semester: Semester?,
        viewedWeek: Int,
        weekDates: LocalDateRange?,
        today: LocalDate,
        now: LocalTime,
    ): LocalTime? {
        if (semester == null || weekDates == null) return null
        if (!semester.isCurrentAcademicSemester) return null
        if (WeekCalculator.weekNumberOn(today, semester) != viewedWeek) return null
        if (today !in weekDates) return null
        return now
    }
}

/** domain → TimedBlock 的最小 internal 实现；不新增 domain entity。 */
internal data class UiTimedBlock(
    override val colorKey: String,
    override val meetingId: MeetingId?,
    override val manualItemId: ManualItemId?,
    override val weekday: Int,
    override val start: LocalTime,
    override val endInclusive: LocalTime,
    override val weeks: WeekPattern,
    override val title: String,
    override val location: String,
    override val teacherNames: List<String>,
) : TimedBlock

/** 学校 meeting → TimedBlock；时间换算唯一 authority = 学期绑定 profile。 */
internal fun schoolTimedBlock(
    semesterId: SemesterId,
    course: Course,
    meeting: CourseMeeting,
    profile: ScheduleProfile,
): UiTimedBlock {
    val range = profile.timeRange(meeting.startPeriod, meeting.endPeriod)
    return UiTimedBlock(
        colorKey = "${semesterId.value}:${course.sourceCourseKey}",
        meetingId = meeting.id,
        manualItemId = null,
        weekday = meeting.weekday,
        start = range.start,
        endInclusive = range.endInclusive,
        weeks = meeting.weekPattern,
        title = course.name,
        location = meeting.location,
        teacherNames = meeting.teacherNames,
    )
}

/** 手动项 → TimedBlock；保留任意分钟，不反向吸附节次。 */
internal fun manualTimedBlock(item: ManualScheduleItem): UiTimedBlock = UiTimedBlock(
    colorKey = "manual:${item.id.value}",
    meetingId = null,
    manualItemId = item.id,
    weekday = item.weekday,
    start = item.startTime,
    endInclusive = item.endTime,
    weeks = item.weekPattern,
    title = item.title,
    location = item.location.orEmpty(),
    teacherNames = emptyList(),
)

/** 每分钟 tick：订阅立即 emit 当前时刻，之后约每分钟；collector 取消即停止。 */
internal fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.instant()
        emit(now)
        val nextMinuteMs = (now.toEpochMilli() / 60_000 + 1) * 60_000
        delay(nextMinuteMs - now.toEpochMilli())
    }
}

/**
 * 首页 VM（frozen §5.1）：viewedSemesterId + observeSemesters() 响应式选择；
 * school+manual 合流后一次联合 place；nowTicks 驱动 today/nowLine。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(
    private val semestersRepo: SemesterRepository,
    private val timetableRepo: TimetableRepository,
    private val manualRepo: ManualItemRepository,
    private val profilesRepo: ScheduleProfileRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    nowTicks: Flow<Instant>,
) : ViewModel() {

    /** null = 用户尚未显式选周 → 用自然周。 */
    private val requestedWeek = MutableStateFlow<Int?>(null)

    private data class SemesterData(
        val semester: Semester?,
        val school: Pair<List<Course>, List<CourseMeeting>>,
        val manual: List<ManualScheduleItem>,
        val profile: ScheduleProfile?,
    )

    private val viewedSemester: Flow<Semester?> =
        combine(settings.viewedSemesterId, semestersRepo.observeSemesters()) { id, list ->
            selectViewedSemester(list, id)
        }

    private val semesterData: Flow<SemesterData> = viewedSemester.flatMapLatest { sem ->
        if (sem == null) {
            flowOf(
                SemesterData(
                    semester = null,
                    school = emptyList<Course>() to emptyList<CourseMeeting>(),
                    manual = emptyList(),
                    profile = null,
                ),
            )
        } else {
            combine(
                timetableRepo.observeSchool(sem.id),
                manualRepo.observe(sem.id),
                profilesRepo.observeForSemester(sem.id),
            ) { school, manual, profile -> SemesterData(sem, school, manual, profile) }
        }
    }

    val state: StateFlow<TimetableUiState> = combine(
        semesterData,
        settings.showNonCurrentWeek,
        requestedWeek,
        nowTicks,
    ) { data, showNonCurrent, reqWeek, tick ->
        buildState(data, showNonCurrent, reqWeek, tick)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    private fun buildState(data: SemesterData, showNonCurrentWeek: Boolean, reqWeek: Int?, tick: Instant): TimetableUiState {
        val zoned = tick.atZone(clock.zone)
        val today = zoned.toLocalDate()
        val now = zoned.toLocalTime()
        val semester = data.semester
        if (semester == null || data.profile == null) {
            return TimetableUiState(showNonCurrentWeek = showNonCurrentWeek, today = today, isLoading = false)
        }
        val natural = WeekCalculator.weekNumberOn(today, semester)
        val viewedWeek = (reqWeek ?: natural ?: 1).coerceIn(1, semester.totalWeeks)
        val weekDates = WeekCalculator.weekRange(semester, viewedWeek)

        val coursesById = data.school.first.associateBy { it.id }
        val schoolBlocks = data.school.second.mapNotNull { m ->
            coursesById[m.courseId]?.let { schoolTimedBlock(semester.id, it, m, data.profile) }
        }
        val manualBlocks = data.manual.map(::manualTimedBlock)

        // 唯一联合布局：过滤 → 合并 → 单次 place → 按 identity 拆分（frozen §4.2 重叠并排不覆盖）
        val profile = data.profile
        val axis = WeeklyTimetableLayout.axisOf(profile)
        val placed = WeeklyTimetableLayout.place(
            weekFilter(schoolBlocks + manualBlocks, viewedWeek, showNonCurrentWeek),
            axis,
        )
        val placedSchool = placed.filter { it.block.meetingId != null }
        val placedManual = placed.filter { it.block.manualItemId != null }

        return TimetableUiState(
            semester = semester,
            viewedWeek = viewedWeek,
            weekDates = weekDates,
            profile = profile,
            placedSchool = placedSchool,
            placedManual = placedManual,
            showNonCurrentWeek = showNonCurrentWeek,
            today = today,
            nowLine = NowLinePolicy.line(semester, viewedWeek, weekDates, today, now),
            isAcademicCurrentViewed = semester.isCurrentAcademicSemester,
            canSyncViewed = semester.isCurrentAcademicSemester && semester.portalLinked,
            isLoading = false,
        )
    }

    fun onWeekSelected(week: Int) {
        val semester = state.value.semester ?: return
        requestedWeek.value = week.coerceIn(1, semester.totalWeeks)
    }

    fun onNextWeek() = shiftWeek(1)

    fun onPrevWeek() = shiftWeek(-1)

    private fun shiftWeek(delta: Int) {
        val semester = state.value.semester ?: return
        val target = (state.value.viewedWeek + delta).coerceIn(1, semester.totalWeeks)
        requestedWeek.value = target
    }
}
