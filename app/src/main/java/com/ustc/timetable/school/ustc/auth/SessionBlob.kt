package com.ustc.timetable.school.ustc.auth

import java.time.Instant

data class SessionBlob(
    val headers: List<SessionCookieHeader>,
    val capturedAt: Instant,
) {
    override fun toString(): String =
        "SessionBlob(headers=<${headers.size} redacted>, capturedAt=$capturedAt)"
}
