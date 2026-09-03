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

    @Test fun descriptor_accepts_only_the_evidenced_post_login_home_scope() {
        assertEquals(
            true,
            descriptor.isPostLoginPortalHomeUrl("https://portal.fixture.invalid/home"),
        )
        listOf(
            "https://portal.fixture.invalid/home/",
            "https://portal.fixture.invalid/home?next=1",
            "https://portal.fixture.invalid/home#section",
            "https://student@portal.fixture.invalid/home",
            "https://other.fixture.invalid/home",
        ).forEach { url -> assertEquals(false, descriptor.isPostLoginPortalHomeUrl(url)) }
    }

    @Test fun descriptor_accepts_only_the_evidenced_dynamic_landing_scope() {
        assertEquals(
            true,
            descriptor.isCurrentTurnLandingUrl(
                "https://portal.fixture.invalid/for-std/course-select/turns/101",
            ),
        )
        listOf(
            "https://portal.fixture.invalid/for-std/course-select/turns/101?next=1",
            "https://portal.fixture.invalid/for-std/course-select/turns/101#section",
            "https://portal.fixture.invalid/for-std/course-select/turns/0",
            "https://portal.fixture.invalid/for-std/course-select/turns/-1",
            "https://portal.fixture.invalid/for-std/course-select/turns/not-an-id",
            "https://portal.fixture.invalid/for-std/course-select/101/turn/202/select",
            "https://other.fixture.invalid/for-std/course-select/turns/101",
        ).forEach { url -> assertEquals(false, descriptor.isCurrentTurnLandingUrl(url)) }
    }

    @Test fun descriptor_accepts_only_exact_current_turn_selection_scope() {
        val valid = "https://portal.fixture.invalid/for-std/course-select/101/turn/202/select"
        assertEquals(true, descriptor.isCurrentTurnSelectionUrl(valid))
        assertEquals(true, descriptor.isDynamicSessionCookieUrl(valid))
        listOf(
            "$valid?next=1",
            "$valid#section",
            "https://portal.fixture.invalid/for-std/course-select/0/turn/202/select",
            "https://portal.fixture.invalid/for-std/course-select/101/turn/0/select",
            "https://portal.fixture.invalid/for-std/course-select/101/turn/202/all-course-takes",
            "https://other.fixture.invalid/for-std/course-select/101/turn/202/select",
        ).forEach { url ->
            assertEquals(false, descriptor.isCurrentTurnSelectionUrl(url))
        }
    }

    @Test fun webview_result_accepts_only_an_exact_current_turn_selection_url() {
        val valid = "https://portal.fixture.invalid/for-std/course-select/101/turn/202/select"
        assertEquals(valid, discovery.selectionUrlFromWebViewResult("\"$valid\""))
        listOf(
            "null",
            "\"https://other.fixture.invalid/for-std/course-select/101/turn/202/select\"",
            "\"https://portal.fixture.invalid/for-std/course-select/101/turn/202/select?token=redacted\"",
            "\"javascript:alert(1)\"",
            "not-json",
        ).forEach { value -> assertEquals(null, discovery.selectionUrlFromWebViewResult(value)) }
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
