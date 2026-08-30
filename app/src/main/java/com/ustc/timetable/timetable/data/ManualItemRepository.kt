package com.ustc.timetable.timetable.data

import androidx.room.withTransaction
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.SemesterId
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 手动项目仓库（SPEC §3.4）。update 的 updatedAt 由仓库注入的 Clock 写入，
 * createdAt 按数据库现值保留；更新/删除不存在 id 显式失败（返回/校验受影响行数）。
 */
class ManualItemRepository(
    private val db: TimetableDatabase,
    private val clock: Clock,
) {

    fun observe(semesterId: SemesterId): Flow<List<ManualScheduleItem>> =
        db.manualItemDao().observe(semesterId.value).map { list -> list.map(Mappers::toDomain) }

    suspend fun add(item: ManualScheduleItem) {
        db.manualItemDao().insert(Mappers.toEntity(item))
    }

    suspend fun update(item: ManualScheduleItem) {
        val current = db.manualItemDao().byId(item.id.value)
            ?: throw IllegalArgumentException("manual item missing: ${item.id}")
        val preserved = item.copy(
            createdAt = java.time.Instant.ofEpochMilli(current.createdAtEpochMilli),
            updatedAt = clock.instant(),
        )
        val count = db.manualItemDao().updateEntity(Mappers.toEntity(preserved))
        check(count == 1) { "manual item update affected $count rows: ${item.id}" }
    }

    suspend fun delete(id: com.ustc.timetable.timetable.domain.ManualItemId): Int =
        db.manualItemDao().delete(id.value)
}
