package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UstcSemesterMetaParserTest {
    private val parser = UstcSemesterMetaParser()

    @Test fun real_evidence_has_no_semantic_semester_metadata() {
        val result = parser.parse(
            RealUstcFixturePages.selection(),
            RealUstcFixturePages.timetable(documentBody = RealUstcFixturePages.fixture("05-timetable-dom.html")),
        )

        assertEquals(UstcSemesterMetaPartial(null, null, null, null, null, null, null), result.meta)
        assertFalse(result.isConfident)
    }

    @Test fun year_looking_ui_text_layout_digest_and_course_week_maxima_are_not_promoted() {
        val result = parser.parse(
            RealUstcFixturePages.selection(
                documentBody = "<html><title>2026-2027 秋季学期</title><body>2026-08-31 至 2027-01-15</body></html>",
            ),
            RealUstcFixturePages.timetable(
                documentBody = "<html><body>第20周</body></html>",
            ),
        )

        assertEquals(UstcSemesterMetaPartial(null, null, null, null, null, null, null), result.meta)
        assertFalse(result.isConfident)
    }

    @Test fun missing_authoritative_payload_container_is_parse_failure() {
        val selectionWithoutResponses = UstcPortalPage(
            document = RealUstcFixturePages.response("selection", "<html></html>", "text/html"),
            xhr = emptyMap(),
        )

        assertParseFailed {
            parser.parse(selectionWithoutResponses, RealUstcFixturePages.timetable())
        }
    }

    @Test fun malformed_authoritative_payload_container_is_parse_failure() {
        val malformedTimetable = RealUstcFixturePages.timetable().copy(
            xhr = RealUstcFixturePages.timetable().xhr + (
                UstcEndpointId.TIMETABLE_DATUM to RealUstcFixturePages.response(
                    "timetable-datum",
                    "not-json",
                    "application/json",
                )
            ),
        )

        assertParseFailed {
            parser.parse(RealUstcFixturePages.selection(), malformedTimetable)
        }
    }

    @Test fun structurally_empty_datum_container_is_parse_failure() {
        assertParseFailed {
            parser.parse(
                RealUstcFixturePages.selection(),
                RealUstcFixturePages.timetable(datum = """{"result":{}}"""),
            )
        }
    }
}
