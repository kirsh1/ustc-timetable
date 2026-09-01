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
        val bytes = json.encodeToString(canonical).toByteArray(Charsets.UTF_8)
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
        ?: compareStringLists(first.teacherNames, second.teacherNames)
}

internal fun compareStringLists(first: List<String>, second: List<String>): Int {
    for (index in 0 until minOf(first.size, second.size)) {
        val compared = first[index].compareTo(second[index])
        if (compared != 0) return compared
    }
    return first.size.compareTo(second.size)
}
