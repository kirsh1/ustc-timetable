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
import com.ustc.timetable.timetable.data.db.TimetableMigrations
import java.time.Clock
import com.ustc.timetable.school.ustc.UstcPortalRuntime
import com.ustc.timetable.school.ustc.auth.AndroidKeystoreKeyProvider
import com.ustc.timetable.school.ustc.auth.FileSessionStorage
import com.ustc.timetable.school.ustc.auth.HeuristicLoginPageDetector
import com.ustc.timetable.school.ustc.auth.SessionStore
import com.ustc.timetable.school.ustc.auth.UstcSessionManager
import com.ustc.timetable.school.ustc.auth.WebViewCookieRetriever
import com.ustc.timetable.school.ustc.parser.UstcCourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.UstcSemesterMetaParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.parser.UstcTimetablePageParser
import com.ustc.timetable.school.ustc.portal.CookieAwareFetcher
import com.ustc.timetable.school.ustc.portal.PortalHttpClientFactory
import com.ustc.timetable.school.ustc.portal.UstcHttpPortalSource
import com.ustc.timetable.school.ustc.portal.UstcPortalDescriptor
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.SyncEngine
import com.ustc.timetable.sync.SyncExecutionRecorder
import com.ustc.timetable.sync.SyncExecutionSource
import com.ustc.timetable.sync.WeeklySyncWorkerFactory
import com.ustc.timetable.timetable.domain.SnapshotDiffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器（SPEC §2.1）。构造为惰性字段装配，Application.onCreate 不做数据库 I/O、
 * 不发网络请求、不启动 portal、不自动同步。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val db: TimetableDatabase =
        Room.databaseBuilder(appContext, TimetableDatabase::class.java, TimetableDatabase.NAME)
            .addMigrations(TimetableMigrations.MIGRATION_1_2)
            .build()

    val settings: SettingsStore = SettingsStore(createSettingsDataStore(appContext))

    /** bundled official profile（A3 asset 唯一权威解析；seed 生命周期由 repository 闭合）。 */
    val bundledOfficial: ScheduleProfile = OfficialProfileLoader.load(appContext)

    val profiles: ScheduleProfileRepository = ScheduleProfileRepository(db, settings, bundledOfficial)

    val semesters: SemesterRepository = SemesterRepository(db, profiles)

    val timetable: TimetableRepository = TimetableRepository(db)

    val manual: ManualItemRepository = ManualItemRepository(db, Clock.systemUTC())

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val ustcPortalRuntime: UstcPortalRuntime = createUstcPortalRuntime()

    val portalReady: Boolean = true

    private fun createUstcPortalRuntime(): UstcPortalRuntime {
        val descriptor = UstcPortalDescriptor()
        val sessionStore = SessionStore(AndroidKeystoreKeyProvider(), FileSessionStorage(appContext))
        val fetcher = CookieAwareFetcher(PortalHttpClientFactory.create())
        val loginDetector = HeuristicLoginPageDetector(descriptor.loginUrl)
        val sessionManager = UstcSessionManager(
            descriptor = descriptor,
            store = sessionStore,
            cookies = WebViewCookieRetriever(),
            fetcher = fetcher,
            detector = loginDetector,
        )
        val portalSource = UstcHttpPortalSource(descriptor, sessionStore, loginDetector, fetcher)
        val courseParser = UstcCourseSelectionPageParser()
        val timetableParser = UstcTimetablePageParser()
        val semesterMetaParser = UstcSemesterMetaParser()
        val normalizer = UstcSnapshotNormalizer()
        val syncEngine = SyncEngine(
            portal = portalSource,
            selectionParser = courseParser,
            timetableParser = timetableParser,
            metaParser = semesterMetaParser,
            normalizer = normalizer,
            differ = SnapshotDiffer(),
            db = db,
            clock = Clock.systemUTC(),
        )
        val recorder = SyncExecutionRecorder(
            source = SyncExecutionSource(syncEngine::executeCurrentAcademicSemester),
            settings = settings,
        )
        val manualController = ManualSyncController(recorder, appScope)
        return UstcPortalRuntime(
            descriptor = descriptor,
            sessionStore = sessionStore,
            sessionManager = sessionManager,
            portalSource = portalSource,
            courseParser = courseParser,
            timetableParser = timetableParser,
            semesterMetaParser = semesterMetaParser,
            normalizer = normalizer,
            syncEngine = syncEngine,
            syncRecorder = recorder,
            manualSyncController = manualController,
            workerFactory = WeeklySyncWorkerFactory(recorder, settings),
        )
    }
}
