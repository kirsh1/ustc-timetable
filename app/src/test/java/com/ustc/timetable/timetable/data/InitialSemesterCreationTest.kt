package com.ustc.timetable.timetable.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.ProfileId
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class InitialSemesterCreationTest {
    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var storeScope: CoroutineScope
    private val now = Instant.parse("2026-09-01T03:04:05Z")

    @Before fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java).allowMainThreadQueries().build()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dir = Files.createTempDirectory("i1-initial")
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { dir.resolve("settings.preferences_pb").toFile() })
        profiles = ScheduleProfileRepository(db, settings, OfficialProfileLoader.load(context))
        semesters = SemesterRepository(db, profiles)
    }

    @After fun tearDown() { db.close(); storeScope.cancel() }

    private fun base(id: String = "initial") = SemesterDefaults.AUTUMN_2026(
        id = id,
        profileId = "must-be-replaced",
        now = now,
    )

    @Test fun initial_create_when_database_empty_commits_one_semester_and_profile() = runBlocking {
        profiles.ensureBundledSeeded()
        val bundled = OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val beforeProfiles = db.scheduleProfileDao().all().size

        val created = semesters.createInitialLocalSemesterIfEmpty(base(), bundled)

        assertEquals(created, db.semesterDao().byId("initial")!!.let(Mappers::toDomain))
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
        assertEquals(beforeProfiles + 1, db.scheduleProfileDao().all().size)
    }

    @Test fun initial_create_when_database_nonempty_is_zero_write() = runBlocking {
        profiles.ensureBundledSeeded()
        val existing = base("existing")
        db.semesterDao().insert(Mappers.toEntity(existing.copy(profileId = ProfileId(OfficialProfileLoader.BUNDLED_PROFILE_ID))))
        val beforeSemesters = db.semesterDao().allByStartDateDesc()
        val beforeProfiles = db.scheduleProfileDao().all()

        val result = semesters.createInitialLocalSemesterIfEmpty(base("loser"), OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext()))

        assertNull(result)
        assertEquals(beforeSemesters, db.semesterDao().allByStartDateDesc())
        assertEquals(beforeProfiles, db.scheduleProfileDao().all())
    }

    @Test fun concurrent_initial_create_commits_at_most_one_semester() = runBlocking {
        val bundled = OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val results = (1..8).map { index -> async(Dispatchers.Default) { semesters.createInitialLocalSemesterIfEmpty(base("initial-$index"), bundled) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun concurrent_initial_create_leaves_no_orphan_profiles() = runBlocking {
        profiles.ensureBundledSeeded()
        val bundled = OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        (1..8).map { index -> async(Dispatchers.Default) { semesters.createInitialLocalSemesterIfEmpty(base("initial-$index"), bundled) } }.awaitAll()
        assertEquals(2, db.scheduleProfileDao().all().size)
    }

    @Test fun initial_create_forces_manual_provenance_and_private_bundled_clone() = runBlocking {
        val bundled = OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val forged = base().copy(portalLinked = true, sourceFingerprint = "forged", isCurrentAcademicSemester = false)
        val created = semesters.createInitialLocalSemesterIfEmpty(forged, bundled)!!
        val privateProfile = db.scheduleProfileDao().byId(created.profileId.value)!!

        assertFalse(created.portalLinked)
        assertNull(created.sourceFingerprint)
        assertTrue(created.isCurrentAcademicSemester)
        assertTrue(created.profileId.value != bundled.id)
        assertFalse(privateProfile.isBundledOfficial)
        assertEquals(bundled.periods, com.ustc.timetable.scheduleprofile.decodePeriods(privateProfile.periodsJson))
    }

    @Test fun transaction_failure_rolls_back_private_profile_and_semester() = runBlocking {
        profiles.ensureBundledSeeded()
        val beforeProfiles = db.scheduleProfileDao().all()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_initial BEFORE INSERT ON semesters BEGIN SELECT RAISE(ABORT, 'forced'); END",
        )

        assertThrows(Exception::class.java) {
            runBlocking {
                semesters.createInitialLocalSemesterIfEmpty(
                    base(),
                    OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                )
            }
        }
        assertEquals(emptyList<Any>(), db.semesterDao().allByStartDateDesc())
        assertEquals(beforeProfiles, db.scheduleProfileDao().all())
    }

    @Test fun manual_initial_creation_creates_no_school_rows() = runBlocking {
        val created = semesters.createInitialLocalSemesterIfEmpty(
            base(),
            OfficialProfileLoader.load(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
        )!!
        assertEquals(emptyList<Any>(), db.courseDao().coursesForSemester(created.id.value))
        assertEquals(emptyList<Any>(), db.courseDao().meetingsForSemester(created.id.value))
    }
}
