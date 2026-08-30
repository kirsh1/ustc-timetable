package com.ustc.timetable.timetable.domain

import java.time.Instant

enum class ItemSource { SCHOOL, MANUAL }

@JvmInline value class CourseId(val value: String)

/**
 * 学校课程（canonical，SPEC §3.3）。稳定业务身份是 [sourceCourseKey]（缺失时由 normalizer
 * 以 "name:"+courseName 合成）；[credits] 可空 = 缺失学分 soft issue。只允许 SCHOOL source。
 */
data class Course(
    val id: CourseId,
    val semesterId: SemesterId,
    val sourceCourseKey: String,
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val courseType: String?,
    val source: ItemSource = ItemSource.SCHOOL,
) {
    init {
        require(source == ItemSource.SCHOOL) { "Course.source must be SCHOOL: $source" }
        require(sourceCourseKey.isNotBlank()) { "sourceCourseKey required" }
        require(name.isNotBlank()) { "name required" }
    }
}
