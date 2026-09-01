# 子计划 04 — Integration + Qualification（I1–I4, J1–J3）

- 需求来源：[SPEC r3.1](../specs/2026-08-30-ustc-timetable-design.md)；路线图：[2026-08-30-ustc-timetable.md](2026-08-30-ustc-timetable.md)
- 前置：子计划 01–03 完成（I2 的真实导入路径另需 gated 分支 F2–F4/F6/G1 完成；fake 路径不受阻）。
- 路径约定：相对仓库根；命令在仓库根执行；测试过滤用完整 FQCN；每个 Task 均为六个独立 checkbox（write RED test → run RED → minimal implementation → run GREEN → targeted regression → commit）。J 系列为验证型任务，六步映射为：准备材料 → 首次执行 → 修复问题 → 复跑确认 → 定向回归 → commit。

---

## Task I1 — FirstLaunchScreen + 稍后手动创建

- SPEC §5.7、§3.2、§3.5.1。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchScreen.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchViewModel.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/AppRoot.kt`；修改 `app/src/main/java/com/ustc/timetable/MainActivity.kt`；测试覆盖 ViewModel、Screen、AppRoot 与 atomic repository claim。

接口：
```kotlin
sealed interface FirstLaunchGate { data object Loading; data object Empty; data object Ready }

class FirstLaunchViewModel(
    private val semesters: SemesterRepository,
    private val settings: SettingsStore,
    private val bundledOfficial: ScheduleProfile,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<FirstLaunchUiState> // observeSemesters(): first emission 前 Loading；empty → Empty；non-empty → Ready
    fun onSkipManualCreation()               // atomic Room create 成功后才写 viewedSemesterId
}
```
- First-launch gate 的唯一 authority 是反应式 Room semester emptiness；`Loading` 防止已有数据用户冷启动闪出首启页。Empty → Ready 后同一 `AppRoot` 回到 Timetable，I3 Settings/ProfileEditor destination 不复制。
- 本地 fallback 固定使用 `AppContainer.bundledOfficial`，不读取 working pointer；`SemesterRepository.createInitialLocalSemesterIfEmpty()` 在一个 transaction 内重新检查空库、clone bundled 为 private profile、插入 forced-manual AUTUMN_2026、exclusive academic-current。Room commit 成功后才写 viewed DataStore。
- DebugSeed 与 manual fallback 共享同一个 atomic empty-database claim；Debug/Release 使用相同 first-launch gate，debug 异步 seed 可自然触发 Empty → Ready。
- 当前真实 portal evidence 未满足，import runtime 是 nullable/unavailable seam；按钮保持可见并提示“导入功能将在门户接入后可用”，不得 composition fake portal。
- 步骤：
- [ ] 1. 写 failing test `app/src/test/java/com/ustc/timetable/timetable/ui/FirstLaunchViewModelTest.kt`（in-memory Room + fake settings）：`skip_creates_local_autumn_2026_semester`（断言 `displayName=="2026-2027 秋季"`、`week1Start==2026-08-31`、`totalWeeks==20`、`portalLinked==false`、`isCurrentAcademicSemester==true`、`sourceFingerprint==null`、`profileId` 指向新建克隆行）、`skip_sets_viewed_to_new_semester`、`first_launch_shown_only_when_no_semesters`、`skip_twice_is_idempotent`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.FirstLaunchViewModelTest"`。
- [ ] 3. 最小实现：界面文案与两个按钮严格按 SPEC §5.7；`[登录并导入]` 使用 nullable real-runtime launcher，未接入时点击提示“导入功能将在门户接入后可用”，不读取 `portalReady` 作为 first-launch gate。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.FirstLaunchViewModelTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI1: database-driven first launch and atomic manual fallback"`。

---

## Task I2 — 登录导入流（fake 路径先行；真实导入依赖 gated 分支）

- SPEC §5.7、§3.2、§6.3、§8.2（含 boundProfile 的事务顺序）。
- fake-path 文件：`app/src/main/java/com/ustc/timetable/semester/ImportFlowViewModel.kt`；测试 `app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt`。`SemesterConfirmSheet` 与 runtime wiring 留给 I4/真实 portal composition。

接口：
```kotlin
sealed interface ImportStep {
    data object AwaitingLogin : ImportStep
    data object Fetching : ImportStep
    data class ConfirmMeta(val draft: SemesterImportDraft) : ImportStep
    data class Done(val semesterId: SemesterId) : ImportStep
    data class Error(val error: SyncError) : ImportStep
}
data class SemesterImportDraft(                 // parser-owned academic fields；缺失值保持 null
    val displayName: String?, val academicYear: String?, val term: Term?,
    val week1Start: LocalDate?, val totalWeeks: Int?,
    val startDate: LocalDate?, val endDate: LocalDate?,
)
data class ConfirmedSemesterMeta(               // UI 只能提交七个 academic fields
    val displayName: String, val academicYear: String, val term: Term,
    val week1Start: LocalDate, val totalWeeks: Int,
    val startDate: LocalDate, val endDate: LocalDate,
)
class ImportFlowViewModel(
    private val portal: SchoolPortalSource,
    private val selectionParser: CourseSelectionPageParser,   // 接口
    private val timetableParser: TimetablePageParser,          // 接口
    private val metaParser: SemesterMetaParser,                // 接口
    private val normalizer: UstcSnapshotNormalizer,
    private val db: TimetableDatabase,
    private val settings: SettingsStore, private val clock: Clock,
) : ViewModel() {
    val step: StateFlow<ImportStep>
    fun onLoginResultOk()
    fun onMetaConfirmed(meta: ConfirmedSemesterMeta)
}
```
- fake 导入主路径（`onLoginResultOk`）：生成一次 provisional `SemesterId` → 抓两页 → 三 parser 接口 → `normalizer.normalize(..., provisionalId)` → destructive-write validation。只有 `isConfident=true` 且七个 academic fields 完整、语义合法时可直接提交；否则进入 `ConfirmMeta`，已识别值原样保留、缺失值保持 `null`，**不得回退或预填 2026 秋季基准**。
- 提交路径由 VM 从 `ConfirmedSemesterMeta` 构建系统字段（provisional id、`portalLinked=true`、academic-current、`importedAt`、private profile id、fingerprint）；working profile 只在内存中克隆为 private entity，禁止调用任何会先插表的 profile clone API。先用 H1 canonical content 计算 fingerprint，再用 `FreshLocalIds.assign` 生成持久化 ID，唯一首个 Room mutation 是 `db.importNewSemesterWithSnapshot(...)`：insert boundProfile → insert semester → setExclusiveAcademicCurrent → insert 学校行 → updateSyncMeta，全在同一事务。事务成功后才写 `viewedSemesterId` 并进入 `Done`。
- I2 fake-path 不依赖 `UstcSessionManager`，不接 `AppContainer`/Activity/Compose，也不安装 fake portal runtime；真实 login/import composition 继续等待 F2/F3/F4/F6/G1 evidence gate。
- 步骤：
- [ ] 1. 写 failing test（fake portal/parsers；in-memory db）：锁定单次 login pipeline、partial-null preservation、confident/incomplete confirmation gate、metadata semantic validation、H1 fingerprint、fresh IDs、原子 private-profile import、旧学期保留、DB commit 后 viewed 更新、已知失败/empty snapshot 零写、transaction rollback 与重复事件幂等。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 3. 最小实现：按上述 evidence-free boundary；直接注入 fakeable source/parser interfaces，复用 F5 normalizer、H1 fingerprint、G2 `FreshLocalIds` 与唯一 import transaction primitive。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.*" --tests "com.ustc.timetable.sync.SyncEngineTest"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI2: evidence-free atomic school import flow"`。

---

## Task I3 — SettingsScreen + 作息 clone-on-write 编辑

- SPEC §8.4、§8.5、§3.5.1。
- 文件：`app/src/main/java/com/ustc/timetable/settings/SettingsScreen.kt`、`app/src/main/java/com/ustc/timetable/settings/SettingsViewModel.kt`、`app/src/main/java/com/ustc/timetable/scheduleprofile/ProfileEditorScreen.kt`；测试 `app/src/test/java/com/ustc/timetable/settings/SettingsScreenTest.kt`、`app/src/test/java/com/ustc/timetable/scheduleprofile/ProfileEditorTest.kt`。

Settings 结构（严格按 SPEC §8.4；**禁止出现**：一周第一天、周末开关、日/周视图、五日/七日）：
```kotlin
class SettingsViewModel(
    private val settings: SettingsStore, private val profiles: ScheduleProfileRepository,
    private val semesters: SemesterRepository,
    private val permission: NotificationPermissionController,
    private val notifications: NotificationsEnabledChecker,
    private val weeklyScheduling: WeeklySyncScheduling?,
    private val manualSync: SettingsManualSync?,
    private val session: SettingsSessionAccess?,
) : ViewModel() {
    val state: StateFlow<SettingsUiState>
    val events: Flow<SettingsEvent>      // permission/settings/relogin one-shot UI events
    fun onToggleWeeklySync(v: Boolean)   // 总是持久化 intent；仅 runtime seam 非 null 时调 scheduler
    fun onSyncSectionEntered()           // 与首次 enable 二者取先，经 H2 policy 发一次 permission event
    fun onNotificationPermissionRequestLaunched() // Route 实际 launch 后才 markRequested
    fun onSyncNow()                      // eligible target + runtime seam 同时存在才 start
    fun onRelogin(); fun onClearLogin()
    fun onApplyWorkingProfileToCurrentSemester()  // profiles.rebindAcademicCurrentSemester()；仅 academic-current 场景存在该入口
}
```
- notification enabled 每次在 screen entry、permission result/resume 边界通过 checker 刷新；Android `RequestPermission` launcher 与 app-notification settings Intent 只存在于 Route/Composable，ViewModel 不调用 Android launcher。API 33+ denied + request-shown 才展示提示，拒绝通知不改变 weekly sync preference。
- H3 WorkerFactory/真实 portal runtime 尚未 composition，因此当前 APK 注入 `weeklyScheduling=null`、`manualSync=null`、relogin unavailable：用户 intent 仍持久化，但不 enqueue broken Worker；立即同步/重新登录保持可见且 disabled。真实 runtime 完成后 startup 再按 persisted intent reconciliation。
- `SettingsStore.lastSyncFinishedAt` 定义为最后一次实际 portal sync attempt 完成时间；I3 只读并以 `yyyy-MM-dd HH:mm` 展示，`null` 显示 `—`，本轮写入次数为 0。target-aware producer 留给真实 runtime composition。
- ProfileEditor：13 行（节次号 + 可编辑开始/结束时间），初值只来自 working profile；保存校验：全部 `end > start` 且 `periods[i+1].start > periods[i].end` → `profiles.saveWorkingEdited(base, periods)`（clone-on-write 新行）。「恢复学校默认」只清 working pointer，旧 custom row 可保留且任何 semester binding 不变；显式 apply 仍只调用 `rebindAcademicCurrentSemester()`。
- portal-dependent Settings actions 使用 nullable runtime seam；不得为 Settings 安装 fake portal/session。登录清除经 session seam 清密文并复位 needReauth，不删除任何本地课表数据。
- 步骤：
- [ ] 1. 写 failing test：`all_required_entries_present`（语义树断言 SPEC §8.4 每项文案存在）、`forbidden_entries_absent`（"第一天""周末""日视图""五日""七日"不出现）、`editor_rejects_overlapping_periods`、`editor_save_creates_new_profile_and_working_pointer_moves`（学期绑定行内容不变）、`restore_default_removes_working_customization`、`apply_to_current_semester_rebinds_only_academic_current`、`weekly_sync_toggle_requests_permission_once`、`notification_denied_hint_row_shown`、`sync_now_disabled_without_portal_linked_semester`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.scheduleprofile.ProfileEditorTest"`。
- [ ] 3. 最小实现：按上述 runtime/settings boundary；首页 gear 用 MainActivity 本地 destination state 接 Settings/ProfileEditor，不引入 Navigation Compose，不启动 I1；「关于」区显示 `BuildConfig.VERSION_NAME`。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.scheduleprofile.ProfileEditorTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.*" --tests "com.ustc.timetable.scheduleprofile.*" --tests "com.ustc.timetable.notification.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI3: settings and clone-on-write profile editor"`。

---

## Task I4 — SemesterConfirmSheet 完整化

- SPEC §3.2、§5.7。
- 文件：完善 `app/src/main/java/com/ustc/timetable/semester/SemesterConfirmSheet.kt`；测试并入 `app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt`。
- 步骤：
- [ ] 1. 写 failing test（补 UI 层用例）：`confirm_sheet_prefills_partial_meta`、`confirm_sheet_allows_editing_all_six_fields`（displayName/academicYear/term/week1Start/totalWeeks/startDate/endDate）、`week1start_must_be_monday_ui_block`、`confirm_writes_viewed_and_dismisses`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 3. 最小实现：日期用 Material3 `DatePicker`；term 用三选一 SegmentedButton。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI4: semester confirmation sheet finalized"`。

---

## Task J1 — 全测试套件 + lint（模拟器）【验证型任务】

- 步骤：
- [ ] 1. 准备 androidTest（新目录 `app/src/androidTest/java/com/ustc/timetable/ui/`）：`TimetableSmokeTest`（首启七列可见、切周、切学期）、`ManualItemUiTest`（长按新建→编辑→删除）、`ReauthUiTest`（fake portal source 经 `AppContainer` 测试覆写：AuthExpired → 弹窗 → 重登 contract fake → 续跑）。
- [ ] 2. 首次执行：`./gradlew :app:testDebugUnitTest` → 预期 GREEN；`./gradlew :app:lintDebug` → 预期无 error（warning 逐条记录处置）；若 RED 则为发现缺陷。
- [ ] 3. 修复发现的问题（缺陷修复走对应子计划 Task 的 TDD 流程，不在本任务内直接改实现）。
- [ ] 4. 复跑确认 GREEN：`./gradlew :app:testDebugUnitTest :app:lintDebug`。
- [ ] 5. 定向回归（connected，模拟器 AVD `dsh_android16` / API 36 x86_64）：`./gradlew :app:connectedDebugAndroidTest` → GREEN（SPEC §28 UI 项全部覆盖）。
- [ ] 6. commit：`git add -A && git commit -m "phaseJ1: full suite green on emulator"`。

## Task J2 — 真机验收（SPEC §14 的 16 项）【验证型任务】

- 步骤：
- [ ] 1. 准备：真机安装 debug 构建（`./gradlew :app:installDebug`），打印 SPEC §14 的 16 项 checklist 到验收记录模板 `docs/superpowers/acceptance/<执行日>-device-qualification.md`。
- [ ] 2. 首次执行：逐项操作并记录通过/失败（含 r3 关注点：15 浏览历史后重启 academic-current 与周任务目标不变；16 Android 13+ 拒绝通知权限后功能正常、不重复请求、设置显示提示）。
- [ ] 3. 修复发现的问题（回到对应子计划 Task 处理；不改本任务内实现）。
- [ ] 4. 复跑确认：失败项重测全过；真机 Keystore 冒烟——登录→`SessionStore` 落盘→杀进程重启→`hasSession()==true`；清除登录后为 false。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN（确认验收过程中无代码回归）。
- [ ] 6. commit：`git add docs && git commit -m "phaseJ2: device qualification recorded"`。

## Task J3 — 发布构建 sanity【验证型任务】

- 步骤：
- [ ] 1. 准备：确认 release 配置（`isMinifyEnabled=false`、debug 签名；后续开启 R8 需补 keep 规则并重跑 J1）。
- [ ] 2. 首次执行：`./gradlew :app:assembleRelease` → BUILD SUCCESSFUL，确认 `app/build/outputs/apk/release/app-release.apk` 存在。
- [ ] 3. 修复发现的问题（若有）。
- [ ] 4. 复跑确认：真机安装 release 构建抽样冒烟 SPEC §14 第 1/9/14 项。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN。
- [ ] 6. commit：`git add -A && git commit -m "phaseJ3: release build sanity pass"`。

---

## 本子计划完成判定

- J1 全绿 + J2 16 项全过并留档 + J3 release 可安装。
- 全程未修改 SPEC 不变量；如实现与 SPEC 冲突，停下修订 SPEC 并报告，不得静默偏离。
