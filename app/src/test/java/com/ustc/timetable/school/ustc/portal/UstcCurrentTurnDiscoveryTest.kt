package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.dto.UstcPortalResponse
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.syncErrorOrNull
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UstcCurrentTurnDiscoveryTest {
    private val descriptor = UstcPortalDescriptor("https://portal.fixture.invalid/")
    private val discovery = UstcCurrentTurnDiscovery(descriptor)

    @Test fun production_descriptor_declares_every_evidenced_request_scope() {
        val production = UstcPortalDescriptor()

        PortalDescriptorRules.validate(production)

        assertEquals(listOf("jw.ustc.edu.cn"), production.sessionHosts)
        assertEquals(
            listOf(
                "/for-std/course-select",
                "/ws/schedule-table/timetable-layout",
                "/ws/for-std/course-select/selected-lessons",
                "/ws/schedule-table/datum",
                "/ws/schedule-table/week-indices-digest",
            ),
            production.sessionCookieUrls.map { URI(it).path },
        )
    }

    @Test fun parses_typed_identifiers_from_the_unique_current_turn_link() {
        val context = discovery.parse(
            response(
                """
                <div class="col-sm-3 text-center">
                  <a class="btn btn-primary"
                     href="/for-std/course-select/101/turn/202/select">进入选课</a>
                </div>
                """.trimIndent(),
            ),
        )

        assertEquals(UstcStudentId(101), context.studentId)
        assertEquals(UstcTurnId(202), context.turnId)
    }

    @Test fun missing_current_turn_link_fails_closed() {
        assertParseFailed { discovery.parse(response("<main>no current turn</main>")) }
    }

    @Test fun malformed_current_turn_path_fails_closed() {
        assertParseFailed {
            discovery.parse(
                response(
                    """
                    <div class="col-sm-3 text-center">
                      <a class="btn btn-primary" href="/for-std/course-select/101/select">进入选课</a>
                    </div>
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test fun non_numeric_identifiers_fail_closed() {
        assertParseFailed {
            discovery.parse(
                response(
                    """
                    <div class="col-sm-3 text-center">
                      <a class="btn btn-primary"
                         href="/for-std/course-select/student/turn/current/select">进入选课</a>
                    </div>
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test fun ambiguous_current_turn_links_fail_closed() {
        assertParseFailed {
            discovery.parse(
                response(
                    """
                    <div class="col-sm-3 text-center">
                      <a class="btn btn-primary"
                         href="/for-std/course-select/101/turn/202/select">进入选课</a>
                    </div>
                    <div class="col-sm-3 text-center">
                      <a class="btn btn-primary"
                         href="/for-std/course-select/101/turn/303/select">进入选课</a>
                    </div>
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test fun matching_link_on_an_untrusted_origin_fails_closed() {
        assertParseFailed {
            discovery.parse(
                response(
                    """
                    <div class="col-sm-3 text-center">
                      <a class="btn btn-primary"
                         href="https://other.fixture.invalid/for-std/course-select/101/turn/202/select">进入选课</a>
                    </div>
                    """.trimIndent(),
                ),
            )
        }
    }

    private fun response(html: String) = UstcPortalResponse(
        requestUrl = descriptor.currentTurnDiscoveryUrl,
        finalUrl = "https://portal.fixture.invalid/for-std/course-select/turns/101",
        contentType = "text/html; charset=utf-8",
        body = html,
    )

    private fun assertParseFailed(block: () -> Unit) {
        val thrown = assertThrows(Exception::class.java, block)
        assertEquals(SyncError.ParseFailed, thrown.syncErrorOrNull())
    }
}
