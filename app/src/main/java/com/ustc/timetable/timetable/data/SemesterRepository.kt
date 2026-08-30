package com.ustc.timetable.timetable.data

import androidx.room.withTransaction
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 学期仓库（SPEC §3.2/§3.5.1）。
 * viewedOrDefault 只读，优先级：valid viewedId → academic-current（含纯手动）→ latest startDate → null；
 * 绝不修改 isCurrentAcademicSemester / portalLinked。
 * createLocalSemester 强制 manual provenance 并在一个事务内完成 clone → insert → exclusive 切换。
 */
class SemesterRepository(
    private val db: TimetableDatabase,
    private val profiles: ScheduleProfileRepository,
) {

    fun observeSemesters(): Flow<List<Semester>> =
        db.semesterDao().observeAll().map { list -> list.map(Mappers::toDomain) }

    /** 同步目标学期：academic-current 且 portalLinked（仅供 sync 路径；浏览路径不使用）。 */
    suspend fun academicCurrentPortalLinked(): Semester? =
        db.semesterDao().academicCurrentPortalLinked()?.let(Mappers::toDomain)

    /** academic-current 学期（无论 portalLinked）；viewed 回退与设置展示使用。 */
    suspend fun academicCurrent(): Semester? =
        db.semesterDao().academicCurrent()?.let(Mappers::toDomain)

    suspend fun latestSemester(): Semester? =
        db.semesterDao().allByStartDateDesc().firstOrNull()?.let(Mappers::toDomain)

    /** UI 查看学期解析：valid viewedId → academic-current → latest startDate → null。只读。 */
    suspend fun viewedOrDefault(viewedId: String?): Semester? {
        viewedId?.let { id ->
            db.semesterDao().byId(id)?.let { return Mappers.toDomain(it) }
        }
        academicCurrent()?.let { return it }
        return latestSemester()
    }

    /**
     * 纯手动本地学期创建入口：单事务内 clone sourceProfile → insert semester（强制
     * portalLinked=false / sourceFingerprint=null / isCurrentAcademicSemester=true）→
     * setExclusiveAcademicCurrent。任一步失败全部回滚，无孤儿 profile；
     * 调用者无法通过 base 伪造 portal-linked 学期。
     */
    suspend fun createLocalSemester(base: Semester, sourceProfile: ScheduleProfile): Semester =
        db.withTransaction {
            val clone = profiles.cloneForSemester(sourceProfile)
            val created = base.copy(
                profileId = ProfileId(clone.id),
                portalLinked = false,
                sourceFingerprint = null,
                isCurrentAcademicSemester = true,
            )
            db.semesterDao().insert(Mappers.toEntity(created))
            db.semesterDao().setExclusiveAcademicCurrent(base.id.value)
            created
        }
}
