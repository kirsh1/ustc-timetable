package com.ustc.timetable.timetable.data.db

import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class ImportSchoolSnapshotTest {
    private lateinit var db: TimetableDatabase
    private val now = Instant.ofEpochMilli(2_000_000)
    private val bundledId = OfficialProfileLoader.BUNDLED_PROFILE_ID

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.scheduleProfileDao().insert(ScheduleProfileEntity(bundledId, "bundled", true, "[]"))
        db.semesterDao().insert(Mappers.toEntity(SemesterDefaults.AUTUMN_2026("old", bundledId, Instant.EPOCH)))
    }

    @After fun tearDown() = db.close()

    @Test fun import_transaction_commits_complete_new_semester() = runBlocking {
        val fixture = importFixture()

        db.importNewSemesterWithSnapshot(
            fixture.profile,
            fixture.semester,
            fixture.courses,
            fixture.meetings,
            "fp-new",
            now,
        )

        val stored = db.semesterDao().byId("new")!!
        assertNotNull(db.scheduleProfileDao().byId(fixture.profile.id))
        assertEquals(fixture.profile.id, stored.profileId)
        assertEquals(true, stored.portalLinked)
        assertEquals(true, stored.isCurrentAcademicSemester)
        assertEquals(false, db.semesterDao().byId("old")!!.isCurrentAcademicSemester)
        assertEquals(1, db.courseDao().coursesForSemester("new").size)
        assertEquals(1, db.courseDao().meetingsForSemester("new").size)
        assertEquals("fp-new", stored.sourceFingerprint)
        assertEquals(now.toEpochMilli(), stored.lastSyncedAtEpochMilli)
    }

    @Test fun import_failure_rolls_back_profile_semester_academic_switch_and_rows() = runBlocking {
        val fixture = importFixture()
        val invalidMeeting = fixture.meetings.single().copy(courseId = CourseId("missing-course"))
        val beforeOld = db.semesterDao().byId("old")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                db.importNewSemesterWithSnapshot(
                    fixture.profile,
                    fixture.semester,
                    fixture.courses,
                    listOf(invalidMeeting),
                    "fp-new",
                    now,
                )
            }
        }

        assertNull(db.scheduleProfileDao().byId(fixture.profile.id))
        assertNull(db.semesterDao().byId("new"))
        assertTrue(db.courseDao().coursesForSemester("new").isEmpty())
        assertTrue(db.courseDao().meetingsForSemester("new").isEmpty())
        assertEquals(beforeOld, db.semesterDao().byId("old"))
        assertEquals(true, db.semesterDao().byId("old")!!.isCurrentAcademicSemester)
    }

    @Test fun import_rejects_profile_mismatch_without_state_change() = runBlocking {
        val fixture = importFixture()
        val mismatch = fixture.semester.copy(profileId = "other")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                db.importNewSemesterWithSnapshot(
                    fixture.profile, mismatch, fixture.courses, fixture.meetings, "fp", now,
                )
            }
        }

        assertNull(db.scheduleProfileDao().byId(fixture.profile.id))
        assertNull(db.semesterDao().byId("new"))
        assertEquals(true, db.semesterDao().byId("old")!!.isCurrentAcademicSemester)
    }

    private fun importFixture(): ImportFixture {
        val profile = ScheduleProfileEntity("profile.private", "private", false, "[]")
        val semester = Mappers.toEntity(
            SemesterDefaults.AUTUMN_2026("new", profile.id, Instant.ofEpochMilli(1_000_000)).copy(
                isCurrentAcademicSemester = true,
                portalLinked = true,
                profileId = ProfileId(profile.id),
            ),
        )
        val course = Course(CourseId("new-course"), SemesterId("new"), "C1", "C1", "新课", 3.0, "专业")
        val meeting = CourseMeeting(
            MeetingId("new-meeting"), course.id, 1, 1, 2, WeekPattern.range(1, 4), "A101", listOf("教师"),
        )
        return ImportFixture(profile, semester, listOf(course), listOf(meeting))
    }

    private data class ImportFixture(
        val profile: ScheduleProfileEntity,
        val semester: com.ustc.timetable.timetable.data.db.entity.SemesterEntity,
        val courses: List<Course>,
        val meetings: List<CourseMeeting>,
    )
}
