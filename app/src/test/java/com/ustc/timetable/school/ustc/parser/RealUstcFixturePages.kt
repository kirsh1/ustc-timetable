package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcPortalResponse
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows

internal object RealUstcFixturePages {
    fun selection(
        selectedLessons: String = fixture("xhr/response-02-selected-lessons.json"),
        documentBody: String = fixture("03-selection-source.html"),
    ) = UstcPortalPage(
        document = response("selection-document", documentBody, "text/html"),
        xhr = mapOf(
            UstcEndpointId.SELECTED_LESSONS to response(
                "selected-lessons",
                selectedLessons,
                "application/json",
            ),
        ),
    )

    fun timetable(
        layout: String = fixture("xhr/response-01-timetable-layout.json"),
        datum: String = fixture("xhr/response-03-datum.json"),
        digest: String = fixture("xhr/response-04-week-indices-digest.json"),
        documentBody: String = fixture("06-timetable-source.html"),
    ) = UstcPortalPage(
        document = response("timetable-document", documentBody, "text/html"),
        xhr = mapOf(
            UstcEndpointId.TIMETABLE_LAYOUT to response("timetable-layout", layout, "application/json"),
            UstcEndpointId.TIMETABLE_DATUM to response("timetable-datum", datum, "application/json"),
            UstcEndpointId.WEEK_INDICES_DIGEST to response("week-indices-digest", digest, "application/json"),
        ),
    )

    fun response(name: String, body: String, contentType: String?) = UstcPortalResponse(
        requestUrl = "https://fixture.invalid/$name",
        finalUrl = "https://fixture.invalid/$name",
        contentType = contentType,
        body = body,
    )

    fun fixture(path: String): String {
        require(path.isNotBlank() && !path.contains("..") && !path.startsWith('/') && !path.startsWith('\\'))
        val stream = checkNotNull(
            RealUstcFixturePages::class.java.getResourceAsStream("/fixtures/ustc/$path"),
        ) { "missing fixture $path" }
        return stream.use { it.readBytes().decodeToString() }
    }
}

internal fun assertParseFailed(block: () -> Unit) {
    val failure = assertThrows(SyncFailure::class.java, block)
    assertSame(SyncError.ParseFailed, failure.error)
}
