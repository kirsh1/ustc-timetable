package com.ustc.timetable.school.ustc.portal

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl

class UstcPortalDescriptor(
    origin: String = PRODUCTION_ORIGIN,
) : PortalDescriptor {
    private val baseUrl = origin.toHttpUrl().newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()

    override val loginUrl: String = url("login")
    val currentTurnDiscoveryUrl: String = url("for-std", "course-select")
    val timetableLayoutUrl: String = url("ws", "schedule-table", "timetable-layout")
    val selectedLessonsUrl: String = url("ws", "for-std", "course-select", "selected-lessons")
    val timetableDatumUrl: String = url("ws", "schedule-table", "datum")
    val weekIndicesDigestUrl: String = url("ws", "schedule-table", "week-indices-digest")
    override val probeUrl: String = currentTurnDiscoveryUrl
    override val selectionUrl: String = currentTurnDiscoveryUrl
    override val timetableUrl: String = currentTurnDiscoveryUrl
    override val sessionHosts: List<String> = listOf(baseUrl.host)
    override val sessionCookieUrls: List<String> = listOf(
        currentTurnDiscoveryUrl,
        timetableLayoutUrl,
        selectedLessonsUrl,
        timetableDatumUrl,
        weekIndicesDigestUrl,
    )

    internal fun hasSameOrigin(uri: URI): Boolean {
        val expected = URI(baseUrl.toString())
        return uri.scheme.equals(expected.scheme, ignoreCase = true) &&
            uri.host.equals(expected.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(expected) &&
            uri.rawUserInfo == null
    }

    internal fun requestContextFrom(uri: URI): UstcRequestContext? {
        if (!hasSameOrigin(uri) || uri.rawQuery != null || uri.rawFragment != null) return null
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        if (
            segments.size != 6 ||
            segments[0] != "for-std" ||
            segments[1] != "course-select" ||
            segments[3] != "turn" ||
            segments[5] != "select"
        ) {
            return null
        }
        val studentId = segments[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val turnId = segments[4].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return UstcRequestContext(UstcStudentId(studentId), UstcTurnId(turnId))
    }

    private fun url(vararg segments: String): String = baseUrl.newBuilder().apply {
        segments.forEach(::addPathSegment)
    }.build().toString()

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> 443
    }

    private companion object {
        const val PRODUCTION_ORIGIN = "https://jw.ustc.edu.cn/"
    }
}
