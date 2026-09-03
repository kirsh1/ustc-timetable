package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.dto.UstcPortalResponse
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import java.net.URI
import org.jsoup.Jsoup

@JvmInline
value class UstcStudentId(val value: Long) {
    init {
        require(value > 0) { "Student identifier must be positive" }
    }
}

@JvmInline
value class UstcTurnId(val value: Long) {
    init {
        require(value > 0) { "Turn identifier must be positive" }
    }
}

data class UstcRequestContext(
    val studentId: UstcStudentId,
    val turnId: UstcTurnId,
)

class UstcCurrentTurnDiscovery(
    private val descriptor: UstcPortalDescriptor,
) {
    fun parse(response: UstcPortalResponse): UstcRequestContext = try {
        val responseUri = URI(response.finalUrl)
        if (!descriptor.hasSameOrigin(responseUri)) parseFailure()

        val candidates = Jsoup.parse(response.body, response.finalUrl)
            .select("div.col-sm-3.text-center > a.btn.btn-primary[href]")
            .filter { it.text().trim() == CURRENT_TURN_TEXT }
        if (candidates.size != 1) parseFailure()

        val link = responseUri.resolve(candidates.single().attr("href"))
        descriptor.requestContextFrom(link) ?: parseFailure()
    } catch (failure: Exception) {
        if (failure is com.ustc.timetable.sync.SyncFailure) throw failure
        throw SyncError.ParseFailed.asFailure(failure)
    }

    private fun parseFailure(): Nothing = throw SyncError.ParseFailed.asFailure()

    private companion object {
        const val CURRENT_TURN_TEXT = "进入选课"
    }
}
