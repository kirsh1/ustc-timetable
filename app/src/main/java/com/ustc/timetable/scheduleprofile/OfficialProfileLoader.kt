package com.ustc.timetable.scheduleprofile

import android.content.Context
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 官方作息 loader（SPEC §3.5）。
 * 官方时间表的唯一 production source of truth 是 `assets/profile/official_2026autumn.json`；
 * 本文件不内嵌任何时间常量。bundled ID 版本化（`profile.bundled.ustc.2026-autumn`），
 * 未来新作息使用新 ID，学期创建时 clone 为私有绑定行（SPEC §3.5.1）。
 */
object OfficialProfileLoader {
    const val ASSET_PATH = "profile/official_2026autumn.json"
    const val BUNDLED_PROFILE_ID = "profile.bundled.ustc.2026-autumn"

    // 严格默认：asset 中误拼字段名或出现未知 schema 字段时直接失败，不静默吞掉。
    private val json = Json

    @Serializable
    private data class Dto(val name: String, val periods: List<PDto>)

    @Serializable
    private data class PDto(val number: Int, val start: String, val end: String)

    fun load(context: Context): ScheduleProfile =
        fromDto(json.decodeFromString<Dto>(context.assets.open(ASSET_PATH).bufferedReader().readText()))

    private fun fromDto(dto: Dto): ScheduleProfile = ScheduleProfile(
        id = BUNDLED_PROFILE_ID,
        name = dto.name,
        isBundledOfficial = true,
        periods = dto.periods.map { PeriodTime(it.number, LocalTime.parse(it.start), LocalTime.parse(it.end)) },
    )
}
