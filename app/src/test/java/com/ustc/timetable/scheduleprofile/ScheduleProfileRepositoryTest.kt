package com.ustc.timetable.scheduleprofile

import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import java.time.LocalTime
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class ScheduleProfileRepositoryTest {

    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: ScheduleProfileRepository
    private lateinit var bundledOfficial: ScheduleProfile
    private val bundledId = "profile.bundled.ustc.2026-autumn"
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java)
            .allowMainThreadQueries().build()
        bundledOfficial = OfficialProfileLoader.load(ctx)
        val dir = Files.createTempDirectory("a6-profiles")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { dir.resolve("settings.preferences_pb").toFile() }),
        )
        repo = ScheduleProfileRepository(db, settings, bundledOfficial)
    }

    @After fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    /** 合成 13 节 fixture（非官方时间；官方表唯一权威是 A3 asset）。 */
    private fun syntheticPeriods(): List<PeriodTime> {
        val times = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25",
            "13:30" to "14:15", "14:25" to "15:10", "15:20" to "16:05", "16:15" to "17:00", "17:10" to "17:55",
            "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35",
        )
        return times.mapIndexed { i, (s, e) -> PeriodTime(i + 1, LocalTime.parse(s), LocalTime.parse(e)) }
    }

    private suspend fun profileCount(): Int = db.scheduleProfileDao().all().size

    // ---- bundled seed 生命周期 ----

    @Test fun bundled_profile_seed_is_idempotent() = runBlocking {
        repo.ensureBundledSeeded()
        repo.ensureBundledSeeded()
        assertEquals(1, profileCount())
        assertEquals(bundledOfficial, db.scheduleProfileDao().byId(bundledId)!!.toProfile())
    }

    @Test fun bundled_profile_comes_from_real_A3_asset() = runBlocking {
        repo.ensureBundledSeeded()
        val freshLoad = OfficialProfileLoader.load(RuntimeEnvironment.getApplication())
        assertEquals(freshLoad.periods, db.scheduleProfileDao().byId(bundledId)!!.toProfile().periods)
    }

    @Test fun bundled_seed_does_not_overwrite_existing_row() = runBlocking {
        val mutated = ScheduleProfileEntity(bundledId, "mutated", true, encodePeriods(syntheticPeriods()))
        db.scheduleProfileDao().insert(mutated)
        repo.ensureBundledSeeded()
        assertEquals("mutated", db.scheduleProfileDao().byId(bundledId)!!.name)  // 历史 identity 不可变
    }

    // ---- working profile 默认语义 ----

    @Test fun working_defaults_to_seeded_bundled() = runBlocking {
        assertEquals(bundledOfficial, repo.observeWorking().first())
        assertEquals(bundledOfficial, db.scheduleProfileDao().byId(bundledId)!!.toProfile())
        assertEquals(null, settings.activeWorkingProfileId.first())
    }

    @Test fun working_edit_creates_new_row_and_moves_pointer() = runBlocking {
        val edited = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        assertFalse(edited.isBundledOfficial)
        assertNotEquals(bundledId, edited.id)
        assertEquals(syntheticPeriods(), edited.periods)
        assertEquals(edited.id, settings.activeWorkingProfileId.first())
        // bundled 行保持原样（只读、不可被编辑覆盖）
        assertEquals(bundledOfficial, db.scheduleProfileDao().byId(bundledId)!!.toProfile())
    }

    @Test fun working_edit_does_not_change_existing_semester_binding() = runBlocking {
        db.scheduleProfileDao().insert(ScheduleProfileEntity("profile.hist", "历史绑定", false, encodePeriods(syntheticPeriods())))
        db.semesterDao().insert(
            com.ustc.timetable.timetable.data.db.Mappers.toEntity(
                com.ustc.timetable.timetable.domain.SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "profile.hist", now = java.time.Instant.EPOCH),
            ).copy(profileId = "profile.hist"),
        )
        val edited = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods().take(12) + PeriodTime(13, LocalTime.of(21, 40), LocalTime.of(22, 25)))
        assertEquals("profile.hist", db.semesterDao().byId("s1")!!.profileId)  // 既有绑定不被重解释
        assertNotEquals("profile.hist", edited.id)
    }

    @Test fun restore_working_returns_to_bundled() = runBlocking {
        val edited = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        assertEquals(edited.id, settings.activeWorkingProfileId.first())
        repo.restoreWorkingToBundled()
        assertEquals(null, settings.activeWorkingProfileId.first())
        assertEquals(bundledOfficial, repo.observeWorking().first())
        // 恢复后以 bundled 为 base 的再次编辑仍合法（base.id == 当前 working id）
        val editedAgain = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        assertEquals(syntheticPeriods(), editedAgain.periods)
    }

    @Test fun restore_default_keeps_old_custom_row() = runBlocking {
        val edited = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        repo.restoreWorkingToBundled()
        assertEquals(edited.toEntity(), db.scheduleProfileDao().byId(edited.id))
    }

    @Test fun restore_default_does_not_change_semester_bindings() = runBlocking {
        repo.ensureBundledSeeded()
        val bound = repo.cloneForSemester(bundledOfficial)
        db.semesterDao().insert(
            com.ustc.timetable.timetable.data.db.Mappers.toEntity(
                com.ustc.timetable.timetable.domain.SemesterDefaults.AUTUMN_2026("bound", bound.id, java.time.Instant.EPOCH),
            ),
        )
        repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        repo.restoreWorkingToBundled()
        assertEquals(bound.id, db.semesterDao().byId("bound")!!.profileId)
    }

    @Test fun saveWorkingEdited_rejects_stale_base() = runBlocking {
        val first = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveWorkingEdited(bundledOfficial, syntheticPeriods()) }  // stale：working 已指向 first
        }
        assertEquals(first.id, settings.activeWorkingProfileId.first())  // stale 编辑不改变指针
        val second = repo.saveWorkingEdited(first, syntheticPeriods().dropLast(1) + PeriodTime(13, LocalTime.of(21, 30), LocalTime.of(22, 15)))
        assertEquals(second.id, settings.activeWorkingProfileId.first())
    }

    // ---- clone / rebind ----

    @Test fun cloneForSemester_creates_new_immutable_row() = runBlocking {
        val before = profileCount()
        val clone = repo.cloneForSemester(bundledOfficial)
        assertEquals(before + 1, profileCount())
        assertNotEquals(bundledId, clone.id)
        assertFalse(clone.isBundledOfficial)
        assertEquals(bundledOfficial.name, clone.name)
        assertEquals(bundledOfficial.periods, clone.periods)
        // 不动 working 指针、不动 semester
        assertEquals(null, settings.activeWorkingProfileId.first())
    }

    private suspend fun seedRebindState() {
        repo.ensureBundledSeeded()
        db.scheduleProfileDao().insert(ScheduleProfileEntity("profile.hist", "历史绑定", false, encodePeriods(syntheticPeriods())))
        db.semesterDao().insert(
            com.ustc.timetable.timetable.data.db.Mappers.toEntity(
                com.ustc.timetable.timetable.domain.SemesterDefaults.AUTUMN_2026(id = "s-hist", profileId = "profile.hist", now = java.time.Instant.EPOCH),
            ).copy(isCurrentAcademicSemester = false, portalLinked = true),
        )
        db.semesterDao().insert(
            com.ustc.timetable.timetable.data.db.Mappers.toEntity(
                com.ustc.timetable.timetable.domain.SemesterDefaults.AUTUMN_2026(id = "s-cur", profileId = bundledId, now = java.time.Instant.EPOCH),
            ).copy(portalLinked = false),
        )
    }

    @Test fun rebind_current_clones_working_and_changes_only_academic_current() = runBlocking {
        seedRebindState()
        val working = repo.saveWorkingEdited(bundledOfficial, syntheticPeriods())
        val historyBefore = db.semesterDao().byId("s-hist")!!.profileId
        val changed = repo.rebindAcademicCurrentSemester()
        assertTrue(changed)
        val cur = db.semesterDao().byId("s-cur")!!
        assertNotEquals(bundledId, cur.profileId)          // 重绑到新 clone
        assertFalse(db.scheduleProfileDao().byId(cur.profileId)!!.isBundledOfficial)
        assertEquals(working.periods, db.scheduleProfileDao().byId(cur.profileId)!!.toProfile().periods)
        assertEquals(historyBefore, db.semesterDao().byId("s-hist")!!.profileId)  // 历史学期不变
    }

    @Test fun rebind_with_no_academic_current_returns_false_without_new_profile() = runBlocking {
        seedRebindState()
        db.semesterDao().clearAcademicCurrentFlags()
        val before = profileCount()
        assertEquals(false, repo.rebindAcademicCurrentSemester())
        assertEquals(before, profileCount())
    }

    @Test fun rebind_failure_rolls_back_profile_clone() = runBlocking {
        seedRebindState()
        // 人为制造两个 academic-current：rebind 命中 2 行 → check 失败 → clone 回滚
        db.semesterDao().insert(
            com.ustc.timetable.timetable.data.db.Mappers.toEntity(
                com.ustc.timetable.timetable.domain.SemesterDefaults.AUTUMN_2026(id = "s-cur2", profileId = bundledId, now = java.time.Instant.EPOCH),
            ),
        )
        val before = profileCount()
        val curProfileBefore = db.semesterDao().byId("s-cur")!!.profileId
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.rebindAcademicCurrentSemester() } }
        assertEquals(before, profileCount())
        assertEquals(bundledId, db.semesterDao().byId("s-cur")!!.profileId)
        assertEquals(curProfileBefore, db.semesterDao().byId("s-cur")!!.profileId)
    }

    // ---- profile serialization ----

    @Test fun scheduleProfile_entity_roundtrip_preserves_all_periods() {
        val profile = ScheduleProfile("profile.custom.x", "自定义", false, syntheticPeriods())
        assertEquals(profile, profile.toEntity().toProfile())
    }

    @Test fun malformed_periodsJson_fails_explicitly() {
        val e = ScheduleProfileEntity("profile.custom.x", "自定义", false, "not-json")
        assertThrows(Exception::class.java) { e.toProfile() }
        val wrongField = ScheduleProfileEntity("profile.custom.x", "自定义", false, """[{"n":1}]""")
        assertThrows(Exception::class.java) { e.copy(periodsJson = wrongField.periodsJson).toProfile() }
    }
}
