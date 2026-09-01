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
import kotlinx.coroutines.launch

/** 学校课程详情 read-only projection（C3）：点击的 meeting + 该课程全部 meeting。 */
data class CourseDetailUiModel(
    val course: Course,
    val selectedMeeting: CourseMeeting,
    val allMeetings: List<CourseMeeting>,
)

/** 完整安排的确定序（C3 correction 7）：业务字段优先，本地随机 MeetingId 仅作最终 tiebreak。 */
internal fun detailMeetingOrder(): Comparator<CourseMeeting> = compareBy(
    { it.weekday },
    { it.startPeriod },
    { it.endPeriod },
    { it.weekPattern.mask },
    { it.location },
    { it.teacherNames.joinToString("\u0000") },
    { it.id.value },
)

/** 未过滤的学校快照 → read-only detail projection；orphan meeting 跳过，不构造 fake course。 */
internal fun buildCourseDetailsByMeetingId(
    courses: List<Course>,
    meetings: List<CourseMeeting>,
): Map<MeetingId, CourseDetailUiModel> {
    val coursesById = courses.associateBy { it.id }
    val meetingsByCourse = meetings.groupBy { it.courseId }
    return meetings.mapNotNull { meeting ->
        val course = coursesById[meeting.courseId] ?: return@mapNotNull null
        meeting.id to CourseDetailUiModel(
            course = course,
            selectedMeeting = meeting,
            allMeetings = meetingsByCourse.getValue(meeting.courseId).sortedWith(detailMeetingOrder()),
        )
    }.toMap()
}

/** 单周 page projection（C1 correction 3）：每页自带 weekDates/两源 placed 块/nowLine。 */
data class TimetableWeekPageUiState(
    val week: Int,
    val weekDates: LocalDateRange,
    val placedSchool: List<PlacedBlock>,
    val placedManual: List<PlacedBlock>,
    val nowLine: LocalTime?,
)

/** B3/C1 authoritative UI state。weekPages 是唯一 layout 真相；其余为 derived getters，无第二份 field。 */
data class TimetableUiState(
    val semester: Semester? = null,
    val viewedWeek: Int = 1,
    val naturalWeek: Int? = null,
    val profile: ScheduleProfile? = null,
    val weekPages: List<TimetableWeekPageUiState> = emptyList(),
    val courseDetailsByMeetingId: Map<MeetingId, CourseDetailUiModel> = emptyMap(),
    val availableSemesters: List<Semester> = emptyList(),
    val showNonCurrentWeek: Boolean = false,
    val today: LocalDate? = null,
    val isAcademicCurrentViewed: Boolean = false,
    val canSyncViewed: Boolean = false,
    val isLoading: Boolean = true,
) {
    val viewedPage: TimetableWeekPageUiState? get() = weekPages.getOrNull(viewedWeek - 1)
    val weekDates: LocalDateRange? get() = viewedPage?.weekDates
    val placedSchool: List<PlacedBlock> get() = viewedPage?.placedSchool.orEmpty()
    val placedManual: List<PlacedBlock> get() = viewedPage?.placedManual.orEmpty()
    val nowLine: LocalTime? get() = viewedPage?.nowLine
}

/** 显式选周带学期身份（C1 correction 2）：学期切换后旧 selection 自动失效。 */
internal data class RequestedWeek(
    val semesterId: SemesterId,
    val week: Int,
)

/**
 * 越界默认周（C1 correction 1，修正冻结文字的反向 clamp）：
 * 教学周内 → 自然周；早于教学周（未来学期）→ 1；晚于教学周（历史学期）→ totalWeeks。
 * authority 仅 week1Start + totalWeeks（经 weekNumberOn），不用 startDate/endDate。
 */
internal fun defaultViewedWeek(today: LocalDate, semester: Semester): Int {
    WeekCalculator.weekNumberOn(today, semester)?.let { return it }
    return if (today < semester.week1Start) 1 else semester.totalWeeks
}

/** viewed semester 选择优先级（A6 语义）：valid viewedId → academic-current → latest startDate → null。只读。 */
internal fun selectViewedSemester(semesters: List<Semester>, viewedId: String?): Semester? {
    viewedId?.let { id -> semesters.firstOrNull { it.id.value == id }?.let { return it } }
    semesters.firstOrNull { it.isCurrentAcademicSemester }?.let { return it }
    return semesters.maxByOrNull { it.startDate }
}

/** 学期候选排序（C2 correction 4，product contract 仅第一条）：startDate desc；同级 week1Start desc、id asc 保证确定序。 */
internal fun sortSemesterChoices(semesters: List<Semester>): List<Semester> =
    semesters.sortedWith(
        compareByDescending<Semester> { it.startDate }
            .thenByDescending { it.week1Start }
            .thenBy { it.id.value },
    )

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
 * 首页 VM（frozen §5.1 + C1）：响应式 viewed semester；school+manual 原始映射一次、
 * 每周独立 page projection（每周内两源联合 place 恰一次）；周导航纯内存不持久化。
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

    private val requestedWeek = MutableStateFlow<RequestedWeek?>(null)
    private var lastViewedSemesterId: SemesterId? = null

    private data class SemesterData(
        val semester: Semester?,
        val school: Pair<List<Course>, List<CourseMeeting>>,
        val manual: List<ManualScheduleItem>,
        val profile: ScheduleProfile?,
        val available: List<Semester>,
    )

    /** viewed + 候选列表来自同一个 Room emission（C2 correction 4：不产生第二 viewed authority）。 */
    private val selection: Flow<Pair<Semester?, List<Semester>>> =
        combine(settings.viewedSemesterId, semestersRepo.observeSemesters()) { id, list ->
            selectViewedSemester(list, id) to sortSemesterChoices(list)
        }

    private val semesterData: Flow<SemesterData> = selection.flatMapLatest { (sem, available) ->
        if (sem == null) {
            flowOf(
                SemesterData(
                    semester = null,
                    school = emptyList<Course>() to emptyList<CourseMeeting>(),
                    manual = emptyList(),
                    profile = null,
                    available = available,
                ),
            )
        } else {
            combine(
                timetableRepo.observeSchool(sem.id),
                manualRepo.observe(sem.id),
                profilesRepo.observeForSemester(sem.id),
            ) { school, manual, profile -> SemesterData(sem, school, manual, profile, available) }
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

    private fun buildState(data: SemesterData, showNonCurrentWeek: Boolean, reqWeek: RequestedWeek?, tick: Instant): TimetableUiState {
        val zoned = tick.atZone(clock.zone)
        val today = zoned.toLocalDate()
        val now = zoned.toLocalTime()
        val semester = data.semester
        if (semester == null || data.profile == null) {
            return TimetableUiState(
                availableSemesters = data.available,
                showNonCurrentWeek = showNonCurrentWeek,
                today = today,
                isLoading = false,
            )
        }
        // C2 correction 1：viewed semester 真正变化（DataStore 写已生效并传播到 state）→ 清显式选周，
        // 新学期从 defaultViewedWeek 开始；DataStore 写失败/未传播时 requestedWeek 保持，绝不提前跳周。
        if (lastViewedSemesterId != null && lastViewedSemesterId != semester.id) {
            requestedWeek.value = null
        }
        lastViewedSemesterId = semester.id
        val naturalWeek = WeekCalculator.weekNumberOn(today, semester)
        val viewedWeek = (reqWeek?.takeIf { it.semesterId == semester.id }?.week)
            ?: defaultViewedWeek(today, semester)

        // 原始映射只做一次（每周 page 共享同一 raw 集合）
        val coursesById = data.school.first.associateBy { it.id }
        val rawSchool = data.school.second.mapNotNull { m ->
            coursesById[m.courseId]?.let { schoolTimedBlock(semester.id, it, m, data.profile) }
        }
        val rawManual = data.manual.map(::manualTimedBlock)
        val axis = WeeklyTimetableLayout.axisOf(data.profile)

        // C3 correction 6：detail map 由未过滤 school snapshot 构造（不受 viewed week/showNonCurrentWeek 影响）
        val courseDetails = buildCourseDetailsByMeetingId(data.school.first, data.school.second)

        // 每周独立 projection：weekFilter → 单次联合 place → 按 identity 拆分
        val weekPages = (1..semester.totalWeeks).map { week ->
            val placed = WeeklyTimetableLayout.place(
                weekFilter(rawSchool + rawManual, week, showNonCurrentWeek),
                axis,
            )
            TimetableWeekPageUiState(
                week = week,
                weekDates = WeekCalculator.weekRange(semester, week),
                placedSchool = placed.filter { it.block.meetingId != null },
                placedManual = placed.filter { it.block.manualItemId != null },
                nowLine = NowLinePolicy.line(semester, week, WeekCalculator.weekRange(semester, week), today, now),
            )
        }

        return TimetableUiState(
            semester = semester,
            viewedWeek = viewedWeek,
            naturalWeek = naturalWeek,
            profile = data.profile,
            weekPages = weekPages,
            courseDetailsByMeetingId = courseDetails,
            availableSemesters = data.available,
            showNonCurrentWeek = showNonCurrentWeek,
            today = today,
            isAcademicCurrentViewed = semester.isCurrentAcademicSemester,
            canSyncViewed = semester.isCurrentAcademicSemester && semester.portalLinked,
            isLoading = false,
        )
    }

    fun onWeekSelected(week: Int) {
        val semester = state.value.semester ?: return
        requestedWeek.value = RequestedWeek(semester.id, week.coerceIn(1, semester.totalWeeks))
    }

    /**
     * 本地学期切换（C2）：唯一持久化写 = settings.setViewedSemesterId；
     * 同 id/未知 id no-op（不写 dangling id、不重置周）。
     * 显式选周的清空由 viewed-id 变化检测承担（见 buildState），DataStore 写失败时 request 保持。
     */
    fun onSemesterSelected(id: SemesterId) {
        val current = state.value.semester ?: return
        if (current.id == id) return
        if (state.value.availableSemesters.none { it.id == id }) return
        viewModelScope.launch { settings.setViewedSemesterId(id.value) }
    }

    /**
     * C4：唯一 authority 是 SettingsStore Flow；这里只发起持久化写，
     * 不做 optimistic local override，state 仅在 DataStore 真正 emit 后重投影。
     */
    fun onToggleShowNonCurrentWeek(value: Boolean) {
        viewModelScope.launch { settings.setShowNonCurrentWeek(value) }
    }

    fun onNextWeek() = shiftWeek(1)

    fun onPrevWeek() = shiftWeek(-1)

    private fun shiftWeek(delta: Int) {
        val semester = state.value.semester ?: return
        val target = (state.value.viewedWeek + delta).coerceIn(1, semester.totalWeeks)
        requestedWeek.value = RequestedWeek(semester.id, target)
    }
}
