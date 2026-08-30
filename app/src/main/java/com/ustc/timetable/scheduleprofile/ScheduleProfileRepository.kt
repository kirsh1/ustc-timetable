package com.ustc.timetable.scheduleprofile

import androidx.room.withTransaction
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.SemesterId
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 作息 profile 仓库（SPEC §3.5.1）。
 * bundled official row 由 A3 asset 解析并幂等 seed（同 ID 行存在即复用，历史 identity 不可变）；
 * working 指针 null = bundled；编辑一律 clone-on-write 新行；
 * 学期创建时再克隆为学期私有行（cloneForSemester 只插行）；
 * rebindAcademicCurrentSemester 是唯一可改既有学期 profileId 的窄入口，且原子。
 */
class ScheduleProfileRepository(
    private val db: TimetableDatabase,
    private val settings: SettingsStore,
    private val bundledOfficial: ScheduleProfile,
) {

    /** bundled 行存在则复用（不覆盖），不存在则插入 A3 asset 解析结果。 */
    suspend fun ensureBundledSeeded() {
        if (db.scheduleProfileDao().byId(bundledOfficial.id) == null) {
            db.scheduleProfileDao().insert(bundledOfficial.toEntity())
        }
    }

    /** 当前 working profile：指针为 null 时即 seeded bundled official。 */
    fun observeWorking(): Flow<ScheduleProfile> = flow {
        ensureBundledSeeded()
        emit(resolveWorking(settings.activeWorkingProfileId.first()))
    }

    /** 查看学期的绑定 profile（学期私有行；行缺失回落 bundled）。 */
    fun observeForSemester(semesterId: SemesterId): Flow<ScheduleProfile> = flow {
        ensureBundledSeeded()
        db.semesterDao().observeAll().collect { semesters ->
            val bound = semesters.first { it.id == semesterId.value }.profileId
            emit(resolveWorking(bound))
        }
    }

    /**
     * clone-on-write 编辑：base.id 必须等于当前 working id（stale 编辑拒绝，指针不变）；
     * 新建 immutable 行并把 working 指针移过去；bundled 行永不被编辑覆盖。
     */
    suspend fun saveWorkingEdited(base: ScheduleProfile, periods: List<PeriodTime>): ScheduleProfile {
        ensureBundledSeeded()
        val workingId = currentWorkingId()
        require(base.id == workingId) { "stale working profile edit: ${base.id} != $workingId" }
        val updated = ScheduleProfile(
            id = "profile.custom." + UUID.randomUUID(),
            name = base.name,
            isBundledOfficial = false,
            periods = periods,
        )
        db.scheduleProfileDao().insert(updated.toEntity())
        settings.setActiveWorkingProfileId(updated.id)
        return updated
    }

    /** working 指针清除 = 回到 bundled official identity。 */
    suspend fun restoreWorkingToBundled() {
        settings.clearActiveWorkingProfileId()
    }

    /**
     * 为学期创建克隆一份私有 immutable 行（新 UUID id，name/periods 复制，isBundledOfficial=false）。
     * 只插入行：不修改 working 指针、不修改 semester——调用方（如 createLocalSemester / rebind）
     * 在同一事务内使用返回值，保证失败时不残留孤儿 clone。
     */
    suspend fun cloneForSemester(source: ScheduleProfile): ScheduleProfile {
        val clone = ScheduleProfile(
            id = "profile.semester." + UUID.randomUUID(),
            name = source.name,
            isBundledOfficial = false,
            periods = source.periods,
        )
        db.scheduleProfileDao().insert(clone.toEntity())
        return clone
    }

    /**
     * SPEC §3.5.1 唯一允许重绑既有学期 profileId 的入口：SQL 仅命中 isCurrentAcademicSemester = 1。
     * 无 academic-current → false（不创建 clone）；命中行数非 1 → 抛出回滚（无孤儿 clone）。
     * 纯手动 academic-current 同样适用（portalLinked 无关）。
     */
    suspend fun rebindAcademicCurrentSemester(): Boolean = db.withTransaction {
        if (db.semesterDao().academicCurrent() == null) return@withTransaction false
        ensureBundledSeeded()
        val working = resolveWorking(currentWorkingId())
        val clone = cloneForSemester(working)
        val updated = db.semesterDao().rebindAcademicCurrentProfile(clone.id)
        check(updated == 1) { "rebind affected $updated semesters, expected 1" }
        true
    }

    // ---- internals ----

    private suspend fun currentWorkingId(): String {
        ensureBundledSeeded()
        return settings.activeWorkingProfileId.first() ?: bundledOfficial.id
    }

    private suspend fun resolveWorking(id: String?): ScheduleProfile =
        if (id == null) bundledOfficial
        else db.scheduleProfileDao().byId(id)?.toProfile() ?: bundledOfficial
}

@Serializable
private data class PeriodDto(val number: Int, val start: String, val end: String)

// 严格默认：periodsJson 误拼字段或未知 schema 字段显式失败，不静默修复。
private val profileJson = Json

internal fun encodePeriods(periods: List<PeriodTime>): String =
    profileJson.encodeToString(
        ListSerializer(PeriodDto.serializer()),
        periods.map { PeriodDto(it.number, it.start.toString(), it.end.toString()) },
    )

internal fun decodePeriods(json: String): List<PeriodTime> =
    profileJson.decodeFromString(ListSerializer(PeriodDto.serializer()), json)
        .map { PeriodTime(it.number, LocalTime.parse(it.start), LocalTime.parse(it.end)) }

internal fun ScheduleProfile.toEntity(): ScheduleProfileEntity =
    ScheduleProfileEntity(id = id, name = name, isBundledOfficial = isBundledOfficial, periodsJson = encodePeriods(periods))

internal fun ScheduleProfileEntity.toProfile(): ScheduleProfile =
    ScheduleProfile(id = id, name = name, isBundledOfficial = isBundledOfficial, periods = decodePeriods(periodsJson))
