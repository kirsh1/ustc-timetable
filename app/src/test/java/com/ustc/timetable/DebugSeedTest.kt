package com.ustc.timetable

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.WeekPattern
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** DebugSeed 位于 src/debug，testDebugUnitTest 编译 debug variant，可直接测试 debug fixture。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DebugSeedTest {

    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var manual: ManualItemRepository
    private val t0 = Instant.parse("2026-09-08T02:00:00Z")
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java).allowMainThreadQueries().build()
        val bundled = OfficialProfileLoader.load(ctx)
        db.scheduleProfileDao().insert(ScheduleProfileEntity(OfficialProfileLoader.BUNDLED_PROFILE_ID, "fixture", true, "[]"))
        val dir = Files.createTempDirectory("b3-seed")
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { dir.resolve("settings.preferences_pb").toFile() }))
        profiles = ScheduleProfileRepository(db, settings, bundled)
        semesters = SemesterRepository(db, profiles)
        manual = ManualItemRepository(db, Clock.fixed(t0, ZoneId.of("Asia/Shanghai")))
    }

    @After fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private suspend fun seed() = DebugSeed.seedIfEmpty(
        semesters = semesters, manual = manual, db = db,
        bundledProfile = OfficialProfileLoader.load(RuntimeEnvironment.getApplication()),
        now = t0,
    )

    @Test fun debug_seed_populates_empty_database() = runBlocking {
        seed()
        val semList = db.semesterDao().allByStartDateDesc()
        assertEquals(1, semList.size)
        val semId = semList.first().id
        assertEquals(2, db.courseDao().coursesForSemester(semId).size)
        assertEquals(4, db.courseDao().meetingsForSemester(semId).size)
        assertEquals(1, db.manualItemDao().itemsForSemester(semId).size)
    }

    @Test fun debug_seed_twice_is_idempotent() = runBlocking {
        seed()
        seed()
        val semId = db.semesterDao().allByStartDateDesc().first().id
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
        assertEquals(2, db.courseDao().coursesForSemester(semId).size)
        assertEquals(4, db.courseDao().meetingsForSemester(semId).size)
        assertEquals(1, db.manualItemDao().itemsForSemester(semId).size)
    }

    @Test fun debug_seed_semester_is_portal_unlinked() = runBlocking {
        seed()
        val row = db.semesterDao().allByStartDateDesc().first()
        assertFalse(row.portalLinked)
        assertTrue(row.isCurrentAcademicSemester)
    }

    @Test fun debug_seed_contains_school_and_manual_blocks() = runBlocking {
        seed()
        val sem = db.semesterDao().allByStartDateDesc().first().let(Mappers::toDomain)
        val meetings = db.courseDao().meetingsForSemester(sem.id.value).map(Mappers::toDomain)
        // 高等无机化学 周五 3-5 节按教师-周次拆为 3 条 meeting
        val chem = meetings.filter { it.weekday == 5 && it.startPeriod == 3 && it.endPeriod == 5 }
        assertEquals(3, chem.size)
        assertEquals(
            listOf(listOf("吴长征"), listOf("刘斯"), listOf("郭宇桥")),
            chem.map { it.teacherNames },
        )
        assertEquals(
            setOf(WeekPattern.range(2, 6), WeekPattern.range(7, 12), WeekPattern.range(13, 18)),
            chem.map { it.weekPattern }.toSet(),
        )
        // 线性代数 周二 3-4 节 1-20 周
        val math = meetings.filter { it.weekday == 2 && it.startPeriod == 3 && it.endPeriod == 4 }
        assertEquals(1, math.size)
        assertEquals(WeekPattern.range(1, 20), math.first().weekPattern)
        // 手动：周六 14:20–16:00 第 2 周
        val manualItem = db.manualItemDao().itemsForSemester(sem.id.value).map(Mappers::toDomain).first()
        assertEquals(6, manualItem.weekday)
        assertEquals(LocalTime.of(14, 20), manualItem.startTime)
        assertEquals(LocalTime.of(16, 0), manualItem.endTime)
        assertEquals(WeekPattern.of(2), manualItem.weekPattern)
    }
}
