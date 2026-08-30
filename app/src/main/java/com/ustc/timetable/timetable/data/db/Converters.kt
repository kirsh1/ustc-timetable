package com.ustc.timetable.timetable.data.db

import androidx.room.TypeConverter
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal const val TEACHER_DELIMITER = "\u0001"

internal fun joinTeachers(v: List<String>): String = v.joinToString(TEACHER_DELIMITER)

internal fun splitTeachers(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split(TEACHER_DELIMITER)

class Converters {
    @TypeConverter fun toWeekPatternMask(p: WeekPattern): Long = p.mask
    @TypeConverter fun fromWeekPatternMask(mask: Long): WeekPattern = WeekPattern(mask)

    @TypeConverter fun toMinutes(t: LocalTime): Int = t.hour * 60 + t.minute
    @TypeConverter fun fromMinutes(m: Int): LocalTime = LocalTime.of(m / 60, m % 60)

    @TypeConverter fun toEpochDay(d: LocalDate): Long = d.toEpochDay()
    @TypeConverter fun fromEpochDay(v: Long): LocalDate = LocalDate.ofEpochDay(v)

    @TypeConverter fun toEpochMilli(i: Instant): Long = i.toEpochMilli()
    @TypeConverter fun fromEpochMilli(v: Long): Instant = Instant.ofEpochMilli(v)

    @TypeConverter fun teachersToString(v: List<String>): String = joinTeachers(v)
    @TypeConverter fun stringToTeachers(s: String): List<String> = splitTeachers(s)
}
