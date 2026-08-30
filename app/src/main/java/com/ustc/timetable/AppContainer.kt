package com.ustc.timetable

import android.content.Context
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.TimetableRepository
import com.ustc.timetable.timetable.data.createSettingsDataStore
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import java.time.Clock

/**
 * 手写依赖容器（SPEC §2.1）。构造为惰性字段装配，Application.onCreate 不做数据库 I/O、
 * 不发网络请求、不启动 portal、不自动同步。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val db: TimetableDatabase =
        Room.databaseBuilder(appContext, TimetableDatabase::class.java, TimetableDatabase.NAME).build()

    val settings: SettingsStore = SettingsStore(createSettingsDataStore(appContext))

    /** bundled official profile（A3 asset 唯一权威解析；seed 生命周期由 repository 闭合）。 */
    val bundledOfficial: ScheduleProfile = OfficialProfileLoader.load(appContext)

    val profiles: ScheduleProfileRepository = ScheduleProfileRepository(db, settings, bundledOfficial)

    val semesters: SemesterRepository = SemesterRepository(db, profiles)

    val timetable: TimetableRepository = TimetableRepository(db)

    val manual: ManualItemRepository = ManualItemRepository(db, Clock.systemUTC())

    /** 学校门户参数在 gated 分支（SPEC §13 证据 → F2–F6/G1）接入前置 false。 */
    val portalReady: Boolean = false
}
