package com.ustc.timetable.timetable.data

import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class RepositoriesTest {

    private val bundledId = "profile.bundled.ustc.2026-autumn"
    private val t1 = Instant.ofEpochMilli(1_000_000)
    private val t2 = Instant.ofEpochMilli(2_000_000)
    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = bundledId, now = t1)
    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var timetable: TimetableRepository
    private lateinit var manual: ManualItemRepository
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java)
            .allowMainThreadQueries().build()
        db.scheduleProfileDao().insert(ScheduleProfileEntity(bundledId, "fixture", true, "[]"))
        val dir = Files.createTempDirectory("a6-repos")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { dir.resolve("settings.preferences_pb").toFile() }),
        )
        val bundled = com.ustc.timetable.scheduleprofile.OfficialProfileLoader.load(ctx)
        profiles = ScheduleProfileRepository(db, settings, bundled)
        semesters = SemesterRepository(db, profiles)
        timetable = TimetableRepository(db)
        manual = ManualItemRepository(db, Clock.fixed(t1, java.time.ZoneOffset.UTC))
    }

    @After fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private fun seedTwoSemesters() = runBlocking {
        db.semesterDao().insert(
            Mappers.toEntity(
                SemesterDefaults.AUTUMN_2026(id = "s-hist", profileId = bundledId, now = t1)
                    .copy(isCurrentAcademicSemester = false, portalLinked = true, startDate = java.time.LocalDate.of(2025, 8, 25)),
            ),
        )
        db.semesterDao().insert(
            Mappers.toEntity(
                SemesterDefaults.AUTUMN_2026(id = "s-cur", profileId = bundledId, now = t1)
                    .copy(portalLinked = false),  // 纯手动 academic-current
            ),
        )
    }

    // ---- viewed / academic-current 分离（A5 correction 语境下的 A6 接口） ----

    @Test fun valid_viewed_history_wins_without_changing_academic_current() = runBlocking {
        seedTwoSemesters()
        val viewed = semesters.viewedOrDefault("s-hist")
        assertEquals("s-hist", viewed!!.id.value)
        assertEquals(true, db.semesterDao().byId("s-cur")!!.isCurrentAcademicSemester)
        assertEquals(false, db.semesterDao().byId("s-hist")!!.isCurrentAcademicSemester)
    }

    @Test fun null_viewed_falls_back_to_manual_academic_current() = runBlocking {
        seedTwoSemesters()
        assertEquals("s-cur", semesters.viewedOrDefault(null)!!.id.value)
    }

    @Test fun invalid_viewed_falls_back_to_academic_current() = runBlocking {
        seedTwoSemesters()
        assertEquals("s-cur", semesters.viewedOrDefault("nope")!!.id.value)
    }

    @Test fun no_current_falls_back_to_latest() = runBlocking {
        seedTwoSemesters()
        db.semesterDao().clearAcademicCurrentFlags()
        assertEquals("s-cur", semesters.viewedOrDefault(null)!!.id.value)  // startDate 较晚者
    }

    @Test fun empty_database_returns_null() = runBlocking {
        assertNull(semesters.viewedOrDefault(null))
        assertNull(semesters.viewedOrDefault("s-hist"))
    }

    @Test fun viewed_setting_does_not_touch_academic_current_database_state() = runBlocking {
        seedTwoSemesters()
        settings.setViewedSemesterId("s-hist")
        assertEquals("s-hist", semesters.viewedOrDefault(settings.viewedSemesterId.first())!!.id.value)
        assertEquals(true, db.semesterDao().byId("s-cur")!!.isCurrentAcademicSemester)
    }

    // ---- createLocalSemester：克隆 + exclusive 切换原子；强制 manual provenance ----

    @Test fun createLocalSemester_clones_profile_and_switches_academic_current_atomically() = runBlocking {
        val bundled = com.ustc.timetable.scheduleprofile.OfficialProfileLoader.load(RuntimeEnvironment.getApplication())
        // 先有一个既有 academic-current 学期，证明 exclusive 切换
        db.semesterDao().insert(
            Mappers.toEntity(SemesterDefaults.AUTUMN_2026(id = "s-old", profileId = bundledId, now = t1)),
        )
        val before = profileRowCount()
        val created = semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026(id = "s-new", profileId = bundledId, now = t1),
            bundled,
        )
        assertEquals(before + 1, profileRowCount())                          // 恰好一个 clone 行
        val row = db.semesterDao().byId("s-new")!!
        assertTrue(row.profileId != bundledId)                                // 私有克隆而非 bundled 行
        assertEquals(false, row.portalLinked)
        assertEquals(null, row.sourceFingerprint)
        assertEquals(true, row.isCurrentAcademicSemester)
        assertEquals(false, db.semesterDao().byId("s-old")!!.isCurrentAcademicSemester)
        val cloneRow = db.scheduleProfileDao().byId(row.profileId)!!
        assertEquals(false, cloneRow.isBundledOfficial)
    }

    @Test fun createLocalSemester_failure_leaves_no_orphan_profile() = runBlocking {
        db.semesterDao().insert(
            Mappers.toEntity(SemesterDefaults.AUTUMN_2026(id = "dup", profileId = bundledId, now = t1)),
        )
        val before = profileRowCount()
        // 同 id 重复插入在 SQL 阶段触发 UNIQUE 约束（clone 已插入、exclusive 未执行）→ 整个事务回滚
        assertThrows(Exception::class.java) {
            runBlocking {
                semesters.createLocalSemester(
                    SemesterDefaults.AUTUMN_2026(id = "dup", profileId = bundledId, now = t1),
                    com.ustc.timetable.scheduleprofile.OfficialProfileLoader.load(RuntimeEnvironment.getApplication()),
                )
            }
        }
        assertEquals(before, profileRowCount())                              // 无孤儿 profile
        assertEquals(true, db.semesterDao().byId("dup")!!.isCurrentAcademicSemester)  // exclusive 未执行，原 current 不变
    }

    @Test fun createLocalSemester_forces_manual_provenance() = runBlocking {
        val forged = SemesterDefaults.AUTUMN_2026(id = "s-forged", profileId = bundledId, now = t1)
            .copy(portalLinked = true, sourceFingerprint = "fp-forged", isCurrentAcademicSemester = false)
        val created = semesters.createLocalSemester(forged, com.ustc.timetable.scheduleprofile.OfficialProfileLoader.load(RuntimeEnvironment.getApplication()))
        assertEquals(false, created.portalLinked)
        assertEquals(null, created.sourceFingerprint)
        assertEquals(true, created.isCurrentAcademicSemester)
    }


    private suspend fun profileRowCount(): Int = db.scheduleProfileDao().all().size

    // ---- TimetableRepository：一致快照 ----

    private fun snapshotCourses(name: String) = listOf(
        Course(CourseId("c1"), SemesterId("s1"), "name:高等无机化学", "CHEM5013P", name, 3.0, null),
    )

    private fun snapshotMeetings(location: String) = listOf(
        CourseMeeting(MeetingId("m1"), CourseId("c1"), 5, 3, 5, WeekPattern.range(2, 6), location, listOf("刘斯")),
    )

    @Test fun observeSchool_emits_consistent_course_meeting_snapshot_after_replacement() = runBlocking {
        db.semesterDao().insert(Mappers.toEntity(sem))
        db.applySchoolSnapshot(sem.id, snapshotCourses("高等无机化学"), snapshotMeetings("TH-B301"), "fp1", t1)
        val emissions = mutableListOf<Pair<List<Course>, List<CourseMeeting>>>()
        val collector = launch { timetable.observeSchool(sem.id).take(2).toList(emissions) }
        withTimeout(10_000) { while (emissions.size < 1) delay(20) }
        db.applySchoolSnapshot(sem.id, snapshotCourses("高等无机化学(新)"), snapshotMeetings("TH-C204"), "fp2", t2)
        withTimeout(10_000) { while (emissions.size < 2) delay(20) }
        collector.cancel()

        val (coursesA, meetingsA) = emissions[0]
        assertEquals("高等无机化学", coursesA.first().name)
        assertEquals("TH-B301", meetingsA.first().location)
        val (coursesB, meetingsB) = emissions[1]
        assertEquals("高等无机化学(新)", coursesB.first().name)   // new course + new meeting
        assertEquals("TH-C204", meetingsB.first().location)        // 不允许 new course + old meeting
        assertEquals(1, meetingsB.size)                            // 不允许 empty transient
    }

    // ---- ManualItemRepository：clock 归属 + 缺失 id 失败 ----

    @Test fun manual_crud_roundtrip() = runBlocking {
        val item = ManualScheduleItem(
            ManualItemId("i1"), sem.id, "组会", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
            WeekPattern.range(3, 12), "教室A", null, t1, t1,
        )
        manual.add(item)
        assertEquals(listOf(item), db.manualItemDao().itemsForSemester(sem.id.value).map(Mappers::toDomain))
        manual.delete(ManualItemId("i1"))
        assertEquals(0, db.manualItemDao().itemsForSemester(sem.id.value).size)
    }

    @Test fun manual_update_sets_updatedAt_from_repository_clock() = runBlocking {
        val item = ManualScheduleItem(
            ManualItemId("i2"), sem.id, "组会", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
            WeekPattern.range(3, 12), null, null, t1, t1,
        )
        manual.add(item)
        manual.update(item.copy(title = "组会(改)", endTime = LocalTime.of(17, 0)))
        val loaded = Mappers.toDomain(db.manualItemDao().byId("i2")!!)
        assertEquals("组会(改)", loaded.title)
        assertEquals(LocalTime.of(17, 0), loaded.endTime)
        assertEquals(t1, loaded.updatedAt)  // repository clock = fixed(t1)
    }

    @Test fun manual_update_preserves_createdAt() = runBlocking {
        val createdAt = Instant.ofEpochMilli(42)
        val item = ManualScheduleItem(
            ManualItemId("i3"), sem.id, "组会", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
            WeekPattern.range(3, 12), null, null, createdAt, t1,
        )
        manual.add(item)
        manual.update(item.copy(title = "改"))
        val row = db.manualItemDao().byId("i3")!!
        assertEquals(createdAt.toEpochMilli(), row.createdAtEpochMilli)
    }

    @Test fun manual_update_missing_id_fails() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                manual.update(
                    ManualScheduleItem(
                        ManualItemId("ghost"), sem.id, "x", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
                        WeekPattern.of(5), null, null, t1, t1,
                    ),
                )
            }
        }
    }
}
