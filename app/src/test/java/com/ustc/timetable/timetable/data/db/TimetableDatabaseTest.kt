package com.ustc.timetable.timetable.data.db

import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableDatabaseTest {

    private lateinit var db: TimetableDatabase
    private val bundledId = OfficialProfileLoader.BUNDLED_PROFILE_ID
    private val t1 = Instant.ofEpochMilli(1_000_000)
    private val t2 = Instant.ofEpochMilli(2_000_000)
    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = bundledId, now = t1)

    @Before fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java)
            .allowMainThreadQueries().build()
        db.scheduleProfileDao().insert(ScheduleProfileEntity(bundledId, "test fixture", true, "[]"))
        db.semesterDao().insert(Mappers.toEntity(sem))
    }

    @After fun tearDown() {
        db.close()
    }

    // ---- fixtures ----

    private fun snapshotA(): Pair<List<Course>, List<CourseMeeting>> {
        val course = Course(CourseId("c1"), sem.id, "name:高等无机化学", "CHEM5013P", "高等无机化学", 3.0, null)
        val meetings = listOf(
            CourseMeeting(MeetingId("m1"), CourseId("c1"), 5, 3, 5, WeekPattern.range(2, 6), "TH-B301", listOf("吴长征")),
            CourseMeeting(MeetingId("m2"), CourseId("c1"), 5, 3, 5, WeekPattern.range(7, 12), "TH-B301", listOf("刘斯")),
        )
        return listOf(course) to meetings
    }

    private fun seedA() = runBlocking {
        val (courses, meetings) = snapshotA()
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp1", t1)
    }

    private fun manualItem() = ManualScheduleItem(
        ManualItemId("i1"), sem.id, "组会", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
        WeekPattern.range(3, 12), "教室A", "带教材", t1, t1,
    )

    // ---- 基础 roundtrip / replacement ----

    @Test fun roundtrip_semester_courses_meetings_manual() = runBlocking {
        val (courses, meetings) = snapshotA()
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp1", t1)
        db.manualItemDao().insert(Mappers.toEntity(manualItem()))
        assertEquals(1, db.courseDao().coursesForSemester(sem.id.value).size)
        assertEquals(2, db.courseDao().meetingsForSemester(sem.id.value).size)
        assertEquals(1, db.manualItemDao().itemsForSemester(sem.id.value).size)
        val loaded = db.semesterDao().byId(sem.id.value)!!
        assertEquals("fp1", loaded.sourceFingerprint)
        assertEquals(t1.toEpochMilli(), loaded.lastSyncedAtEpochMilli)
    }

    @Test fun replaceSchoolData_swaps_school_rows_preserves_manual() = runBlocking {
        seedA()
        db.manualItemDao().insert(Mappers.toEntity(manualItem()))
        val (coursesB, meetingsB) = snapshotA().let { (c, m) ->
            c.map { it.copy(name = "高等无机化学(新)") } to m.map { it.copy(location = "TH-C204") }
        }
        db.applySchoolSnapshot(sem.id, coursesB, meetingsB, "fp2", t2)
        val meetings = db.courseDao().meetingsForSemester(sem.id.value)
        assertEquals(2, meetings.size)
        assertTrue(meetings.all { it.location == "TH-C204" })
        assertEquals(1, db.manualItemDao().itemsForSemester(sem.id.value).size)
        assertEquals("fp2", db.semesterDao().byId(sem.id.value)!!.sourceFingerprint)
    }

    // ---- A5 correction 3: 原子失败必须证明旧快照原样保留 ----

    @Test fun applySchoolSnapshot_failure_preserves_previous_snapshot_exactly() = runBlocking {
        seedA()
        db.manualItemDao().insert(Mappers.toEntity(manualItem()))
        // snapshot B：通过边界校验（courseId ∈ 提供的集合、semesterId 匹配、全部 SCHOOL），
        // 但两条 course 重复主键 → SQL 执行阶段 UNIQUE 约束失败 → 整个事务回滚
        val coursesB = listOf(
            Course(CourseId("cb1"), sem.id, "k1", "K1", "课一", 2.0, null),
            Course(CourseId("cb1"), sem.id, "k2", "K2", "课二", null, null),
        )
        val meetingsB = listOf(
            CourseMeeting(MeetingId("mb1"), CourseId("cb1"), 1, 1, 2, WeekPattern.range(1, 4), "LOC", listOf("师")),
        )
        assertThrows(Exception::class.java) {
            runBlocking { db.applySchoolSnapshot(sem.id, coursesB, meetingsB, "fpX", t2) }
        }
        // 旧快照 A 原样保留
        val courses = db.courseDao().coursesForSemester(sem.id.value)
        assertEquals(listOf(Mappers.toEntity(snapshotA().first.first(), "s1")), courses)
        val meetings = db.courseDao().meetingsForSemester(sem.id.value).map { Mappers.toDomain(it) }
        assertEquals(snapshotA().second, meetings)
        assertTrue(meetings.all { it.location == "TH-B301" })
        val semEntity = db.semesterDao().byId(sem.id.value)!!
        assertEquals("fp1", semEntity.sourceFingerprint)
        assertEquals(t1.toEpochMilli(), semEntity.lastSyncedAtEpochMilli)
        assertEquals(1, db.manualItemDao().itemsForSemester(sem.id.value).count())
    }

    // ---- A5 correction 4: 输入边界校验先于 destructive delete ----

    @Test fun missing_semester_rejected_without_writes() = runBlocking {
        seedA()
        val ghost = SemesterId("nope")
        val courses = listOf(Course(CourseId("cx"), ghost, "k", "CODE", "课", null, null))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { db.applySchoolSnapshot(ghost, courses, emptyList(), "fp", t2) }
        }
        assertEquals(1, db.courseDao().coursesForSemester(sem.id.value).size)
        assertEquals("fp1", db.semesterDao().byId(sem.id.value)!!.sourceFingerprint)
    }

    @Test fun course_semester_mismatch_rejected_without_deleting_old_snapshot() = runBlocking {
        seedA()
        val courses = listOf(Course(CourseId("cb1"), SemesterId("s2"), "k", "CODE", "课", null, null))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { db.applySchoolSnapshot(sem.id, courses, emptyList(), "fpX", t2) }
        }
        assertEquals(2, db.courseDao().meetingsForSemester(sem.id.value).size)
        assertEquals("fp1", db.semesterDao().byId(sem.id.value)!!.sourceFingerprint)
    }

    @Test fun meeting_outside_supplied_course_set_rejected_without_deleting_old_snapshot() = runBlocking {
        seedA()
        val courses = listOf(Course(CourseId("cb1"), sem.id, "k", "CODE", "课", null, null))
        val meetings = listOf(
            CourseMeeting(MeetingId("mb1"), CourseId("cOther"), 1, 1, 2, WeekPattern.range(1, 4), "LOC", listOf("师")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { db.applySchoolSnapshot(sem.id, courses, meetings, "fpX", t2) }
        }
        assertEquals(2, db.courseDao().meetingsForSemester(sem.id.value).size)
        assertEquals("fp1", db.semesterDao().byId(sem.id.value)!!.sourceFingerprint)
    }

    // ---- A5 correction 5: setExclusiveAcademicCurrent 安全性 ----

    @Test fun setExclusiveAcademicCurrent_switches_flags() = runBlocking {
        db.semesterDao().insert(Mappers.toEntity(sem.copy(id = SemesterId("s2"), isCurrentAcademicSemester = false)))
        db.semesterDao().setExclusiveAcademicCurrent("s2")
        assertEquals(false, db.semesterDao().byId("s1")!!.isCurrentAcademicSemester)
        assertEquals(true, db.semesterDao().byId("s2")!!.isCurrentAcademicSemester)
        assertEquals(1, db.semesterDao().allByStartDateDesc().count { it.isCurrentAcademicSemester })
    }

    @Test fun setExclusiveAcademicCurrent_unknown_id_preserves_existing_current() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { db.semesterDao().setExclusiveAcademicCurrent("nope") }
        }
        assertEquals(true, db.semesterDao().byId("s1")!!.isCurrentAcademicSemester)
    }

    // ---- A5 correction 6: converters / mappers 全类型 round-trip ----

    @Test fun converters_roundtrip_all_supported_types() {
        val c = Converters()
        val wp = WeekPattern.parse("2-6,8")
        assertEquals(wp, c.fromWeekPatternMask(c.toWeekPatternMask(wp)))
        val time = LocalTime.of(14, 20)
        assertEquals(time, c.fromMinutes(c.toMinutes(time)))
        val date = LocalDate.of(2026, 8, 31)
        assertEquals(date, c.fromEpochDay(c.toEpochDay(date)))
        val instant = Instant.ofEpochMilli(1_700_000_123_456)
        assertEquals(instant, c.fromEpochMilli(c.toEpochMilli(instant)))
        val teachers = listOf("吴长征", "刘斯")
        assertEquals(teachers, c.stringToTeachers(c.teachersToString(teachers)))
        assertEquals(emptyList<String>(), c.stringToTeachers(c.teachersToString(emptyList())))
    }

    @Test fun mappers_roundtrip_preserves_all_domain_fields() {
        val semester = sem.copy(lastSyncedAt = t2, sourceFingerprint = "fp")
        assertEquals(semester, Mappers.toDomain(Mappers.toEntity(semester)))
        assertEquals(sem, Mappers.toDomain(Mappers.toEntity(sem)))  // lastSyncedAt=null 亦保留
        val courseA = snapshotA().first.first()
        assertEquals(courseA, Mappers.toDomain(Mappers.toEntity(courseA, "s1"), sem.id))
        val noCredit = Course(CourseId("c2"), sem.id, "name:线性代数", "MATH1001", "线性代数", null, "通修")
        assertEquals(null, Mappers.toDomain(Mappers.toEntity(noCredit, "s1"), sem.id).credits)
        val meetings = snapshotA().second
        assertEquals(meetings, meetings.map { Mappers.toDomain(Mappers.toEntity(it)) })
        assertEquals(manualItem(), Mappers.toDomain(Mappers.toEntity(manualItem())))
    }

    @Test fun mapper_unknown_term_throws() {
        val e = Mappers.toEntity(sem).copy(term = "WINTER")
        assertThrows(IllegalArgumentException::class.java) { Mappers.toDomain(e) }
    }

    @Test fun mapper_unknown_source_throws() {
        val ce = Mappers.toEntity(snapshotA().first.first(), "s1").copy(source = "REMOTE")
        assertThrows(IllegalArgumentException::class.java) { Mappers.toDomain(ce, sem.id) }
    }

    // ---- FK schema proof ----

    @Test fun course_rejects_missing_semester_fk() {
        val orphan = Mappers.toEntity(
            Course(CourseId("cx"), SemesterId("ghost"), "k", "CODE", "课", null, null), "ghost",
        )
        assertThrows(Exception::class.java) {
            runBlocking { db.courseDao().insertCourses(listOf(orphan)) }
        }
    }
}
