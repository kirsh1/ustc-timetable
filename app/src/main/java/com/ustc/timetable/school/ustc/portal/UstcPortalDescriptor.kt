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
    val portalHomeUrl: String = url("home")
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

    override fun isPostLoginPortalHomeUrl(rawUrl: String): Boolean {
        val uri = parseUrl(rawUrl) ?: return false
        return hasSameOrigin(uri) &&
            uri.rawPath == "/home" &&
            uri.rawQuery == null &&
            uri.rawFragment == null
    }

    override fun isDynamicSessionCookieUrl(rawUrl: String): Boolean {
        return isCurrentTurnLandingUrl(rawUrl) || isCurrentTurnSelectionUrl(rawUrl)
    }

    override fun isCurrentTurnLandingUrl(rawUrl: String): Boolean {
        val uri = parseUrl(rawUrl) ?: return false
        if (!hasSameOrigin(uri) || uri.rawQuery != null || uri.rawFragment != null) return false
        val match = DYNAMIC_LANDING_PATH.matchEntire(uri.rawPath.orEmpty()) ?: return false
        return match.groupValues[1].toLongOrNull()?.let { it > 0 } == true
    }

    override fun isCurrentTurnSelectionUrl(rawUrl: String): Boolean {
        val uri = parseUrl(rawUrl) ?: return false
        return requestContextFrom(uri) != null
    }

    internal fun hasSameOrigin(uri: URI): Boolean {
        val expected = URI(baseUrl.toString())
        return uri.scheme.equals(expected.scheme, ignoreCase = true) &&
            uri.host.equals(expected.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(expected) &&
            uri.rawUserInfo == null
    }

    internal fun requestContextFrom(uri: URI): UstcRequestContext? {
        if (!hasSameOrigin(uri) || uri.rawQuery != null || uri.rawFragment != null) return null
        val match = CURRENT_TURN_SELECTION_PATH.matchEntire(uri.rawPath.orEmpty()) ?: return null
        val studentId = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val turnId = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return UstcRequestContext(UstcStudentId(studentId), UstcTurnId(turnId))
    }

    private fun url(vararg segments: String): String = baseUrl.newBuilder().apply {
        segments.forEach(::addPathSegment)
    }.build().toString()

    private fun parseUrl(rawUrl: String): URI? = try {
        URI(rawUrl)
    } catch (_: Exception) {
        null
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> 443
    }

    private companion object {
        const val PRODUCTION_ORIGIN = "https://jw.ustc.edu.cn/"
        val DYNAMIC_LANDING_PATH = Regex("/for-std/course-select/turns/([0-9]+)")
        val CURRENT_TURN_SELECTION_PATH =
            Regex("/for-std/course-select/([0-9]+)/turn/([0-9]+)/select")
    }
}
