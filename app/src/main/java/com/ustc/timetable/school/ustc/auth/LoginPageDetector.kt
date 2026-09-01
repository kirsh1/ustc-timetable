package com.ustc.timetable.school.ustc.auth

fun interface LoginPageDetector {
    fun isLoginPage(url: String, html: String): Boolean
}
