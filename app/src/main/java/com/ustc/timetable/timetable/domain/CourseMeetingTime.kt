package com.ustc.timetable.timetable.domain

import com.ustc.timetable.scheduleprofile.LocalTimeRange
import com.ustc.timetable.scheduleprofile.ScheduleProfile

fun CourseMeeting.effectiveTimeRange(profile: ScheduleProfile): LocalTimeRange =
    exactStartTime?.let { LocalTimeRange(it, requireNotNull(exactEndTime)) }
        ?: profile.timeRange(startPeriod, endPeriod)
