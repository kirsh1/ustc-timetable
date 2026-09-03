package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.auth.LoginPageDetector
import com.ustc.timetable.school.ustc.auth.SessionCookieHeader
import com.ustc.timetable.school.ustc.auth.SessionStore
import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcPortalResponse
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import com.ustc.timetable.sync.asFailure
import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class UstcHttpPortalSource(
    private val descriptor: UstcPortalDescriptor,
    private val session: SessionStore,
    private val detector: LoginPageDetector,
    private val fetcher: CookieAwareFetcher,
) : SchoolPortalSource {
    private val requestMutex = Mutex()
    private val discovery = UstcCurrentTurnDiscovery(descriptor)
    private var pendingTimetablePage: UstcPortalPage? = null

    override suspend fun fetchCourseSelectionPage(): UstcPortalPage = requestMutex.withLock {
        pendingTimetablePage = null
        val bundle = fetchBundle()
        pendingTimetablePage = bundle.timetable
        bundle.selection
    }

    override suspend fun fetchTimetablePage(): UstcPortalPage = requestMutex.withLock {
        pendingTimetablePage?.also { pendingTimetablePage = null } ?: fetchBundle().timetable
    }

    private suspend fun fetchBundle(): PortalBundle {
        val headers = session.load()?.headers ?: throw SyncError.AuthenticationExpired.asFailure()
        val context = requestContextFrom(headers) ?: run {
            val discoveryResponse = fetchChecked(
                Request.Builder()
                    .url(descriptor.currentTurnDiscoveryUrl)
                    .header("Accept", "text/html")
                    .get()
                    .build(),
                headers,
            )
            discovery.parse(discoveryResponse)
        }

        val layout = fetchJsonPost(
            descriptor.timetableLayoutUrl,
            buildJsonObject { put("timeTableLayoutId", 1) }.toString(),
            headers,
        )
        val selected = fetchChecked(
            Request.Builder()
                .url(descriptor.selectedLessonsUrl)
                .header("Accept", "application/json")
                .post(
                    FormBody.Builder()
                        .add("studentId", context.studentId.value.toString())
                        .add("turnId", context.turnId.value.toString())
                        .build(),
                )
                .build(),
            headers,
        )
        val lessonIds = selectedLessonIds(selected)
        val datum = fetchJsonPost(
            descriptor.timetableDatumUrl,
            buildJsonObject {
                put("lessonIds", buildJsonArray { lessonIds.forEach { add(JsonPrimitive(it)) } })
                put("studentId", context.studentId.value)
            }.toString(),
            headers,
        )
        val digest = fetchJsonPost(
            descriptor.weekIndicesDigestUrl,
            digestRequest(datum).toString(),
            headers,
        )

        return PortalBundle(
            selection = UstcPortalPage(
                document = null,
                xhr = mapOf(UstcEndpointId.SELECTED_LESSONS to selected),
            ),
            timetable = UstcPortalPage(
                document = null,
                xhr = mapOf(
                    UstcEndpointId.TIMETABLE_LAYOUT to layout,
                    UstcEndpointId.TIMETABLE_DATUM to datum,
                    UstcEndpointId.WEEK_INDICES_DIGEST to digest,
                ),
            ),
        )
    }

    private suspend fun fetchJsonPost(
        url: String,
        body: String,
        headers: List<SessionCookieHeader>,
    ): UstcPortalResponse = fetchChecked(
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build(),
        headers,
    )

    private suspend fun fetchChecked(
        request: Request,
        headers: List<SessionCookieHeader>,
    ): UstcPortalResponse {
        val response = fetcher.fetch(request, headers)
        if (detector.isLoginPage(response.finalUrl, response.body)) {
            throw SyncError.AuthenticationExpired.asFailure()
        }
        return response
    }

    private fun selectedLessonIds(response: UstcPortalResponse): List<Long> = parseForRequest {
        val values = Json.parseToJsonElement(response.body).jsonArray
        values.map { element ->
            val id = element.jsonObject.requiredPositiveLong("id")
            id
        }.also { ids ->
            if (ids.distinct().size != ids.size) requestParseFailure()
        }
    }

    private fun digestRequest(response: UstcPortalResponse): JsonArray = parseForRequest {
        val schedules = Json.parseToJsonElement(response.body)
            .jsonObject["result"]
            ?.jsonObject
            ?.get("scheduleList")
            ?.jsonArray
            ?: requestParseFailure()
        val cards = linkedMapOf<CardKey, LinkedHashSet<Long>>()
        schedules.forEach { element ->
            val schedule = element.jsonObject
            val key = CardKey(
                lessonId = schedule.requiredPositiveLong("lessonId"),
                scheduleGroupId = schedule.requiredPositiveLong("scheduleGroupId"),
                startTime = schedule.requiredLong("startTime"),
                endTime = schedule.requiredLong("endTime"),
                weekday = schedule.requiredLong("weekday").takeIf { it in 1..7 }
                    ?: requestParseFailure(),
            )
            val week = schedule.requiredLong("weekIndex").takeIf { it in 1..63 }
                ?: requestParseFailure()
            cards.getOrPut(key, ::linkedSetOf).add(week)
        }
        buildJsonArray {
            cards.values.forEachIndexed { index, weeks ->
                add(
                    buildJsonObject {
                        put("weekIndicesGroupId", "c${index + 1}")
                        put("weekIndices", buildJsonArray { weeks.forEach { add(JsonPrimitive(it)) } })
                    },
                )
            }
        }
    }

    private fun JsonObject.requiredLong(name: String): Long =
        (get(name) as? JsonPrimitive)?.longOrNull ?: requestParseFailure()

    private fun JsonObject.requiredPositiveLong(name: String): Long =
        requiredLong(name).takeIf { it > 0 } ?: requestParseFailure()

    private inline fun <T> parseForRequest(block: () -> T): T = try {
        block()
    } catch (failure: SyncFailure) {
        throw failure
    } catch (failure: Exception) {
        throw SyncError.ParseFailed.asFailure(failure)
    }

    private fun requestParseFailure(): Nothing = throw SyncError.ParseFailed.asFailure()

    private fun requestContextFrom(headers: List<SessionCookieHeader>): UstcRequestContext? {
        val contexts = headers.mapNotNull { header ->
            runCatching { descriptor.requestContextFrom(URI(header.requestUrl)) }.getOrNull()
        }.distinct()
        if (contexts.size > 1) requestParseFailure()
        return contexts.singleOrNull()
    }

    private data class PortalBundle(
        val selection: UstcPortalPage,
        val timetable: UstcPortalPage,
    )

    private data class CardKey(
        val lessonId: Long,
        val scheduleGroupId: Long,
        val startTime: Long,
        val endTime: Long,
        val weekday: Long,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
