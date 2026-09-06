package com.ustc.timetable.timetable.domain

import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SchoolSnapshotFingerprint {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun compute(content: FingerprintedSchoolContent): String {
        val canonical = content.copy(
            courses = content.courses.sortedBy { it.sourceCourseKey },
            meetings = content.meetings.sortedWith(fingerprintedMeetingComparator),
        )
        val serialized = if (canonical.meetings.any { it.exactStartMinutes != null }) {
            json.encodeToString(
                FingerprintedSchoolContentV2(
                    version = 2,
                    semesterMeta = canonical.semesterMeta,
                    courses = canonical.courses,
                    meetings = canonical.meetings.map { meeting ->
                        FingerprintedMeetingV2(
                            meeting.sourceCourseKey,
                            meeting.weekday,
                            meeting.startPeriod,
                            meeting.endPeriod,
                            meeting.weekPatternMask,
                            meeting.location,
                            meeting.teacherNames,
                            meeting.exactStartMinutes,
                            meeting.exactEndMinutes,
                        )
                    },
                ),
            )
        } else {
            json.encodeToString(canonical)
        }
        val bytes = serialized.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

internal val fingerprintedMeetingComparator = Comparator<FingerprintedMeeting> { first, second ->
    compareValues(first.sourceCourseKey, second.sourceCourseKey)
        .takeIf { it != 0 }
        ?: compareValues(first.weekday, second.weekday).takeIf { it != 0 }
        ?: compareValues(first.startPeriod, second.startPeriod).takeIf { it != 0 }
        ?: compareValues(first.endPeriod, second.endPeriod).takeIf { it != 0 }
        ?: compareValues(first.weekPatternMask, second.weekPatternMask).takeIf { it != 0 }
        ?: compareValues(first.location, second.location).takeIf { it != 0 }
        ?: compareStringLists(first.teacherNames, second.teacherNames).takeIf { it != 0 }
        ?: compareValues(first.exactStartMinutes, second.exactStartMinutes).takeIf { it != 0 }
        ?: compareValues(first.exactEndMinutes, second.exactEndMinutes)
}

@kotlinx.serialization.Serializable
private data class FingerprintedSchoolContentV2(
    val version: Int,
    val semesterMeta: FingerprintedSemesterMeta,
    val courses: List<FingerprintedCourse>,
    val meetings: List<FingerprintedMeetingV2>,
)

@kotlinx.serialization.Serializable
private data class FingerprintedMeetingV2(
    val sourceCourseKey: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPatternMask: Long,
    val location: String,
    val teacherNames: List<String>,
    val exactStartMinutes: Int?,
    val exactEndMinutes: Int?,
)

internal fun compareStringLists(first: List<String>, second: List<String>): Int {
    for (index in 0 until minOf(first.size, second.size)) {
        val compared = first[index].compareTo(second[index])
        if (compared != 0) return compared
    }
    return first.size.compareTo(second.size)
}
