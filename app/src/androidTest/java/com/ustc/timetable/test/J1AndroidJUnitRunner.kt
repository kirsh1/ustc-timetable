package com.ustc.timetable.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.ustc.timetable.TimetableApp

class J1AndroidJUnitRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, TimetableApp::class.java.name, context)
}
