package com.ustc.timetable.school.ustc.dto

enum class UstcEndpointId {
    TIMETABLE_LAYOUT,
    SELECTED_LESSONS,
    TIMETABLE_DATUM,
    WEEK_INDICES_DIGEST,
}

data class UstcPortalResponse(
    val requestUrl: String,
    val finalUrl: String,
    val contentType: String?,
    val body: String,
)

data class UstcPortalPage(
    val document: UstcPortalResponse?,
    val xhr: Map<UstcEndpointId, UstcPortalResponse> = emptyMap(),
) {
    constructor(html: String, finalUrl: String) : this(
        document = UstcPortalResponse(
            requestUrl = finalUrl,
            finalUrl = finalUrl,
            contentType = "text/html",
            body = html,
        ),
    )

    val html: String
        get() = document?.body.orEmpty()

    val finalUrl: String
        get() = document?.finalUrl.orEmpty()
}
