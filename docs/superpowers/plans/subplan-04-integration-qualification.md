# 子计划 04 — Integration + Qualification（I1–I4, J1–J3）

- 需求来源：[SPEC r3.1](../specs/2026-08-30-ustc-timetable-design.md)；路线图：[2026-08-30-ustc-timetable.md](2026-08-30-ustc-timetable.md)
- 前置：子计划 01–03 完成（I2 的真实导入路径另需 gated 分支 F2–F4/F6/G1 完成；fake 路径不受阻）。
- 路径约定：相对仓库根；命令在仓库根执行；测试过滤用完整 FQCN；每个 Task 均为六个独立 checkbox（write RED test → run RED → minimal implementation → run GREEN → targeted regression → commit）。J 系列为验证型任务，六步映射为：准备材料 → 首次执行 → 修复问题 → 复跑确认 → 定向回归 → commit。

---

## Task I1 — FirstLaunchScreen + 稍后手动创建

- SPEC §5.7、§3.2、§3.5.1。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchScreen.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchViewModel.kt`；修改 `app/src/main/java/com/ustc/timetable/MainActivity.kt`（库空 → FirstLaunchScreen，否则 TimetableScreen）；测试 `app/src/test/java/com/ustc/timetable/timetable/ui/FirstLaunchViewModelTest.kt`。

接口：
```kotlin
class FirstLaunchViewModel(
    private val semesters: SemesterRepository, private val profiles: ScheduleProfileRepository,
    private val settings: SettingsStore, private val clock: Clock,
) : ViewModel() {
    fun onSkipManualCreation()      // 事务内：克隆 bundled working 为学期 profile → createLocalSemester(AUTUMN_2026, portalLinked=false, isCurrentAcademicSemester=true) → setViewedSemesterId
    val importedSemesterCreated: StateFlow<Boolean?>
}
```
- 步骤：
- [ ] 1. 写 failing test `app/src/test/java/com/ustc/timetable/timetable/ui/FirstLaunchViewModelTest.kt`（in-memory Room + fake settings）：`skip_creates_local_autumn_2026_semester`（断言 `displayName=="2026-2027 秋季"`、`week1Start==2026-08-31`、`totalWeeks==20`、`portalLinked==false`、`isCurrentAcademicSemester==true`、`sourceFingerprint==null`、`profileId` 指向新建克隆行）、`skip_sets_viewed_to_new_semester`、`first_launch_shown_only_when_no_semesters`、`skip_twice_is_idempotent`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.FirstLaunchViewModelTest"`。
- [ ] 3. 最小实现：界面文案与两个按钮严格按 SPEC §5.7；`[登录并导入]` 经 `AppContainer.portalReady: Boolean` 判断（gated 分支 G1 完成前置 true）决定可用性，未接入时点击提示"导入功能将在门户接入后可用"。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.FirstLaunchViewModelTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.*"` → GREEN。
- [ ] 6. commit：`git add -A && git commit -m "phaseI1: first launch screen with local manual-fallback semester"`。

---

## Task I2 — 登录导入流（fake 路径先行；真实导入依赖 gated 分支）

- SPEC §5.7、§3.2、§6.3、§8.2（含 boundProfile 的事务顺序）。
- 文件：`app/src/main/java/com/ustc/timetable/semester/SemesterConfirmSheet.kt`、`app/src/main/java/com/ustc/timetable/semester/ImportFlowViewModel.kt`；测试 `app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt`。

接口：
```kotlin
sealed class ImportStep {
    data object LoggingIn : ImportStep
    data class Fetching(val message: String) : ImportStep
    data class ConfirmMeta(val partial: UstcSemesterMetaPartial, val prefilled: Semester) : ImportStep   // isConfident=false 时
    data class Done(val semesterId: SemesterId) : ImportStep
    data class Error(val error: SyncError) : ImportStep
}
class ImportFlowViewModel(
    private val session: UstcSessionManager, private val portal: SchoolPortalSource,
    private val selectionParser: CourseSelectionPageParser,   // 接口
    private val timetableParser: TimetablePageParser,          // 接口
    private val metaParser: SemesterMetaParser,                // 接口
    private val normalizer: UstcSnapshotNormalizer,
    private val fingerprint: SchoolSnapshotFingerprintObject,  // object 直用，无需注入
    private val db: TimetableDatabase, private val profiles: ScheduleProfileRepository,
    private val settings: SettingsStore, private val clock: Clock,
) : ViewModel() {
    val step: StateFlow<ImportStep>
    fun onLoginResultOk()
    fun onMetaConfirmed(edited: Semester)
    fun onMetaCancelled()
}
```
- 导入主路径（`onLoginResultOk`）：抓两页 → 三 parser 接口 → `normalizer.normalize` → 以 meta 构造 `Semester`（`portalLinked=true`；`week1Start/totalWeeks` 缺失时回退 2026 秋季基准并在 ConfirmSheet 预填标注）→ `isConfident` ? 直接落库 : 进 `ConfirmMeta`；落库调用 `db.importNewSemesterWithSnapshot(boundProfile, semesterEntity, courses, meetings, fingerprint, syncedAt)`（SPEC §8.2 单事务，顺序：insert boundProfile → insert semester → setExclusiveAcademicCurrent → insert 学校行 → updateSyncMeta）→ `setViewedSemesterId` → `Done`。`boundProfile` 由 `profiles.bindProfileAtSemesterCreation(workingProfile)` 生成（克隆 working 为该学期私有行内容，id=新 UUID）。
- 步骤：
- [ ] 1. 写 failing test（fake portal/parsers 接口；in-memory db）：`confident_meta_skips_confirm_sheet`、`unconfident_meta_shows_confirm_prefilled`、`import_persists_semester_snapshot_and_switches_academic_current_in_one_flow`（旧 academic-current 被置 false）、`import_failure_leaves_no_semester`（parser 抛错 → semesters 表数量不变、原 academic-current 不变）、`import_failure_leaves_no_orphan_profile`（落库中途失败 → `schedule_profiles` 无新行）、`import_success_semester_points_to_private_profile_clone`（semester.profileId == 新克隆行 id）、`import_sets_viewed_to_new_semester`、`confirm_saves_permanent_semester_record`、`week1start_must_be_monday`（非周一被拒）。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 3. 最小实现：按上；聚合 `data class UstcParsers(selection, timetable, meta)` 便于注入亦可，直接逐个注入三接口亦可（实现取其一并保持与测试一致）。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.*" --tests "com.ustc.timetable.sync.SyncEngineTest"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI2: login import flow with single-transaction semester and bound profile"`。

---

## Task I3 — SettingsScreen + 作息 clone-on-write 编辑

- SPEC §8.4、§8.5、§3.5.1。
- 文件：`app/src/main/java/com/ustc/timetable/settings/SettingsScreen.kt`、`app/src/main/java/com/ustc/timetable/settings/SettingsViewModel.kt`、`app/src/main/java/com/ustc/timetable/scheduleprofile/ProfileEditorScreen.kt`；测试 `app/src/test/java/com/ustc/timetable/settings/SettingsScreenTest.kt`、`app/src/test/java/com/ustc/timetable/scheduleprofile/ProfileEditorTest.kt`。

Settings 结构（严格按 SPEC §8.4；**禁止出现**：一周第一天、周末开关、日/周视图、五日/七日）：
```kotlin
class SettingsViewModel(
    private val settings: SettingsStore, private val profiles: ScheduleProfileRepository,
    private val semesters: SemesterRepository, private val syncScheduler: SyncSchedulerApi,
    private val permission: NotificationPermissionController,
    private val notificationsEnabled: Boolean,
) : ViewModel() {
    val ui: StateFlow<SettingsUiState>   // lastSyncTime、weeklySyncEnabled、loginState(未登录/已登录/已失效)、通知权限提示行
    fun onToggleWeeklySync(v: Boolean)   // v=true：SyncScheduler.enqueue + shouldRequestNow→requestPermissions(POST_NOTIFICATIONS)→markRequested()；v=false：cancel
    fun onSyncNow()                      // ManualSyncController.start()（无 portalLinked 学期时按钮禁用）
    fun onRelogin(); fun onClearLogin()
    fun onApplyWorkingProfileToCurrentSemester()  // profiles.rebindAcademicCurrentSemester()；仅 academic-current 场景存在该入口
}
```
- ProfileEditor：13 行（节次号 + 开始/结束 TimePicker），保存校验：全部 `end > start` 且 `periods[i+1].start > periods[i].end` → `profiles.saveWorkingEdited(base, periods)`（clone-on-write 新行）；「恢复学校默认」→ `restoreWorkingToBundled()`。
- 步骤：
- [ ] 1. 写 failing test：`all_required_entries_present`（语义树断言 SPEC §8.4 每项文案存在）、`forbidden_entries_absent`（"第一天""周末""日视图""五日""七日"不出现）、`editor_rejects_overlapping_periods`、`editor_save_creates_new_profile_and_working_pointer_moves`（学期绑定行内容不变）、`restore_default_removes_working_customization`、`apply_to_current_semester_rebinds_only_academic_current`、`weekly_sync_toggle_requests_permission_once`、`notification_denied_hint_row_shown`、`sync_now_disabled_without_portal_linked_semester`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.scheduleprofile.ProfileEditorTest"`。
- [ ] 3. 最小实现：按上；`onRelogin`/`onClearLogin` 走 E2 会话管理；「关于」区显示 `BuildConfig.VERSION_NAME`。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.scheduleprofile.ProfileEditorTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.*" --tests "com.ustc.timetable.scheduleprofile.*" --tests "com.ustc.timetable.notification.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseI3: settings with clone-on-write profile editor and permission policy"`。

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
