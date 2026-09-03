package com.ustc.timetable.school.ustc.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicLoginPageDetectorTest {
    private val detector = HeuristicLoginPageDetector(PORTAL_LOGIN_URL)

    @Test fun real_portal_cas_login_page_is_detected() {
        assertTrue(detector.isLoginPage(PORTAL_LOGIN_URL, fixture("09-login-expired-dom.html")))
    }

    @Test fun auth_expired_login_response_is_detected() {
        assertTrue(
            detector.isLoginPage(
                "$PORTAL_LOGIN_URL?refer=%2Ffixture",
                fixture("09-login-expired-dom.html"),
            ),
        )
    }

    @Test fun valid_course_selection_document_is_not_detected() {
        assertFalse(
            detector.isLoginPage(
                "https://jw.ustc.edu.cn/for-std/course-select/STUDENT/turn/TURN/all-course-takes",
                fixture("03-selection-source.html"),
            ),
        )
    }

    @Test fun valid_timetable_document_is_not_detected() {
        assertFalse(
            detector.isLoginPage(
                "https://jw.ustc.edu.cn/for-std/course-select/STUDENT/turn/TURN/select",
                fixture("06-timetable-source.html"),
            ),
        )
    }

    @Test fun valid_xhr_json_is_not_detected() {
        assertFalse(
            detector.isLoginPage(
                "https://jw.ustc.edu.cn/ws/for-std/course-select/selected-lessons",
                fixture("xhr/response-02-selected-lessons.json"),
            ),
        )
    }

    @Test fun cas_host_without_evidenced_login_structure_is_insufficient() {
        assertFalse(
            detector.isLoginPage(
                "https://id.ustc.edu.cn/cas/login",
                "<html><body>Identity provider</body></html>",
            ),
        )
    }

    @Test fun password_input_on_unrelated_host_is_insufficient() {
        assertFalse(
            detector.isLoginPage(
                "https://unrelated.example/login",
                "<html><body><input type=\"password\" name=\"password\"></body></html>",
            ),
        )
    }

    @Test fun portal_login_url_without_evidenced_structure_is_insufficient() {
        assertFalse(detector.isLoginPage(PORTAL_LOGIN_URL, "<html><body>Not a login page</body></html>"))
    }

    private fun fixture(path: String): String {
        require(path.isNotBlank() && !path.contains("..") && !path.startsWith('/') && !path.startsWith('\\'))
        return checkNotNull(
            HeuristicLoginPageDetectorTest::class.java.getResourceAsStream("/fixtures/ustc/$path"),
        ) { "missing fixture $path" }.use { it.readBytes().decodeToString() }
    }

    private companion object {
        const val PORTAL_LOGIN_URL = "https://jw.ustc.edu.cn/login"
    }
}
