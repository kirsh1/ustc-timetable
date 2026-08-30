# USTC 课表 App 设计规格（Approved Requirements Spec）

- 日期：2026-08-30
- 修订：r3.1（2026-08-30 docs-only hotfix；修订记录见文末，保留 r1/r2/r3 历史）
- 状态：**已批准需求固化 + r3.1 修正；等待 A0 批准后进入实现**
- 范围：仅服务中国科学技术大学当前教务系统的 Android 原生课表 App。不做多学校适配、不做通用教务框架、不做网页壳。
- 配套实施计划：roadmap [docs/superpowers/plans/2026-08-30-ustc-timetable.md](../plans/2026-08-30-ustc-timetable.md) 及其 4 份子计划（Foundation / Timetable UI + Manual / Portal + Sync / Integration + Qualification）。
- 变更纪律：本文件是已批准需求的固化。任何偏离必须先修订本文件并向用户说明，禁止实现与 spec 脱节。

---

## 1. 产品不变量（Product Invariants）

以下条目为固定产品决策，**不提供设置项，不允许实现绕过**：

1. 一周永远周一 → 周日，七列始终完整存在；周六周日即使无课也不隐藏。
2. 不提供“修改一周第一天”设置；App 固定 Monday-first。
3. 不提供日视图；不提供“工作周”模式；不提供横向滚动隐藏部分星期。
4. 首页即整周课表，没有 Dashboard、没有底部导航栏。
5. 时间轴 Y 坐标按真实分钟比例计算，禁止“每节课等高”模型。
6. 课表主体是真正的 Compose children（可点击/长按/无障碍/语义测试），不画成不可交互的整版 Canvas bitmap。
7. 学校导入的课程只读；用户手动项目可编辑可删除；学校重新同步绝不覆盖手动项目。
8. 历史学期永久本地保存，查看历史学期绝不发起 Web 请求。
9. App 绝不保存学校账号密码；只保存必要 session/cookie，且必须加密存储。
10. 任何同步失败（网络/解析/登录失效）都不得破坏本地已有课表；禁止“先 DELETE 再抓网页”。
11. UI 层不感知 HTML、DOM selector、“2-18周”等学校网页字符串；这些只存在于 `school/ustc` 边界内。
12. 不引入跨平台框架（Flutter / React Native / Electron / Tauri）；不为“未来其他学校”做抽象。
13. **academic-current 与 viewed 严格分离**：`Semester.isCurrentAcademicSemester` 只表示“学校教务当前处于哪个学期”，仅由导入/同步流程置位；浏览、切换、查看历史学期**永远不得修改**它。UI 正在查看哪个学期是独立的 `viewedSemesterId` 状态。
14. **学校同步只针对 academic-current 学期**：后台周任务与手动同步都只作用于 `isCurrentAcademicSemester == true && portalLinked == true` 的学期；纯手动学期（`portalLinked == false`）永不执行学校同步，也永不产生 reauth 通知。
15. **手动项目时间必须落在其学期绑定 profile 的 day window 内**：允许任意分钟（不要求节次），禁止轴外时间。
16. **学期绑定的作息 profile 不可变**：学期创建时克隆绑定 profile（§3.5.1），未来修改作息或新学期默认值永不重解释历史学期。

---

## 2. 技术架构

### 2.1 技术栈

| 用途 | 选型 |
|---|---|
| 语言 | Kotlin 2.4.10（K2；Compose 编译器用官方 `org.jetbrains.kotlin.plugin.compose`） |
| UI | Jetpack Compose（BOM **2026.06.00** → 实测解析 ui/foundation/runtime **1.11.3**、Material 3 1.4.0；core ≤ 1.11.x 的 stable BOM；不升级 Compose 1.12、不采用 Robolectric 4.17 beta）（仅基础控件/Dialog/Sheet/Settings） |
| 持久化 | Room 2.8.4（KSP 2.3.11） |
| 后台任务 | WorkManager 2.11.2（每周一次静默同步） |
| 网络 | OkHttp 5.5.0 |
| 登录 | Android WebView（首次登录；自动完成检测见 §7.1） |
| 并发 | Kotlin Coroutines / Flow 1.11.0 |
| HTML 解析 | Jsoup 1.23.2（仅 `school/ustc/parser` 内使用） |
| 序列化 | kotlinx-serialization 1.11.0（作息 profile 资产、指纹内容序列化、DTO） |
| DI | 手写 `AppContainer`（构造注入；WorkManager 用自定义 `WorkerFactory`）。不引入 Hilt/Dagger，减少构建链复杂度 |
| 设置存储 | Jetpack DataStore (Preferences) 1.2.1 |

- 依赖与构建的**精确固定版本组合**（AGP 9.3.0 / Gradle wrapper 9.5.0 / JDK 21 等）见 roadmap plan 的 A0 章节与 `gradle/libs.versions.toml`；执行时以该组合为准，不使用“最新稳定”作为规格。
- minSdk = 26（原生 `java.time`）；**compileSdk = 37、targetSdk = 36**（有意决策：37 仅作编译 API surface 以满足所选依赖的 AAR minCompileSdk——navigation 2.10.0 / core 1.19.0 / lifecycle 2.11.0 / okhttp-android 5.5.0；targetSdk 36 仍是 App runtime behavior authority；已装 platform android-37.0）。
- 单 Gradle 模块 `:app`；包结构按职责隔离（见 2.2）。
- Material 3 只用于基础控件、Dialog、Sheet、Settings 页；课表主体使用专用 Compose 布局（`BoxWithConstraints` + 按比例定位的 Compose children）。视觉要求：干净、原生、现代、信息优先；不要巨大圆角卡片、不要到处 elevation、不要底部导航、不要 Dashboard 化。不复刻旧本科课表的旧 Android 视觉。

### 2.2 包结构（职责边界，不要求机械照搬目录但必须保持隔离）

```
com.ustc.timetable/
├── timetable/
│   ├── domain/      WeekPattern、Semester、Course、CourseMeeting、ManualScheduleItem、
│   │                WeekCalculator、SchoolSnapshot、FingerprintedSchoolContent、
│   │                SchoolSnapshotFingerprint、SnapshotDiffer、ScheduleChange、ChangeFormatter
│   ├── data/        Room entities/DAOs/db/mappers、applySchoolSnapshot、仓库实现、DataStore SettingsStore
│   ├── ui/          TimetableScreen、ViewModel、各 Sheet/Dialog、主题、色板、viewedSemesterId 状态
│   └── layout/      TimelineAxis、WeeklyTimetableLayout、LongPressResolver（纯逻辑 + Compose 网格组件）
├── manual/          手动项目编辑器 UI 与创建流程
├── semester/        学期切换 UI、SemesterConfirmSheet、学期元数据识别结果模型
├── scheduleprofile/ ScheduleProfile、PeriodTime、LocalTimeRange、period↔时间换算、bundled 资产加载、Profile 绑定仓库
├── school/ustc/
│   ├── auth/        WebView 登录（自动完成检测）、SessionStore（Keystore 加密）、UstcSessionManager、LoginPageDetector
│   ├── portal/      PortalDescriptor、UstcPortalUrls（真实 URL 常量，证据门后填写）、SchoolPortalSource、UstcHttpPortalSource
│   ├── parser/      选课结果页/我的课表页/学期元数据 parser（Jsoup）
│   └── dto/         USTC 专属中间 DTO（不进入 domain）
├── sync/            SyncEngine、SyncError、SyncResult、WeeklySyncWorker、SyncScheduler、NotificationPermissionController 挂接点
├── notification/    通知渠道、SyncNotification、POST_NOTIFICATIONS 权限策略
└── settings/        SettingsScreen 及其 ViewModel
```

### 2.3 数据流与依赖规则

```
School HTML
    ↓  (仅 school/ustc 内)
USTC-specific DTO
    ↓
parser / normalizer
    ↓
canonical domain model
    ↓
Room
    ↓
Compose UI
```

- 依赖只能自上而下指向 domain；domain 不依赖 Room/OkHttp/Jsoup/Compose。
- 禁止 UI 直接理解 HTML、DOM selector、“2-18周”字符串。
- 数据来源（HTTP vs WebView DOM）对 UI 与 Room 不可见，隐藏在 `SchoolPortalSource` 边界后。

---

## 3. 域模型（Canonical Domain Model）

### 3.1 WeekPattern

- 内部表示：`@JvmInline value class WeekPattern(val mask: Long)`，第 `w` 周（1-based）对应 bit `w-1`；容量 63 周，超出抛 `IllegalArgumentException`。内部业务状态**禁止**保存 `"2-6,8,10-12周"` 文本。
- 纯函数 API（全部单元测试覆盖）：
  - `contains(week: Int): Boolean`（week < 1 返回 false）
  - `format(): String` — 规范形式为逗号分隔的升序单值与闭区间，区间用 ASCII `-`（如 `2-6,8,10-12`）
  - `WeekPattern.parse(raw: String): WeekPattern` — 接受中英文逗号/顿号分隔、`a-b`/`a–b` 区间、单个数字；周围可携带“周/第/”等装饰字符；数字语法不合法（空集、倒序区间、非数字、>63 周）抛 `IllegalArgumentException`。“单周/双周”文本不在 `parse` 内，由 USTC parser 映射到 `oddWithin/evenWithin`（§6.3）
  - 构造器：`of(vararg weeks: Int)`、`range(start: Int, endInclusive: Int): WeekPattern`、`oddWithin(start, endInclusive)`（单周）、`evenWithin(start, endInclusive)`（双周）
- 必须支持的语义集合：`1-20`、`2-6`、`2,4,6,8`、`2-6,8,10-12`、单周、双周。

### 3.2 学期、academic-current 与 viewed semester

```kotlin
data class Semester(
  val id: SemesterId,                     // 本地 UUID；仅作主键/外键，绝不参与 fingerprint 与 diff pairing
  val displayName: String,                // “2026-2027 秋季”
  val academicYear: String,               // “2026-2027”
  val term: Term,                         // enum { AUTUMN, SPRING, SUMMER }
  val week1Start: LocalDate,              // 第 1 周周一，当前自然周的唯一 authority
  val totalWeeks: Int,
  val startDate: LocalDate,
  val endDate: LocalDate,
  val importedAt: Instant,                // 审计字段；不参与 fingerprint
  val lastSyncedAt: Instant?,             // 审计字段；不参与 fingerprint
  val isCurrentAcademicSemester: Boolean, // 学校教务意义上的当前学期；全局至多一个 true；仅导入/同步流程置位
  val portalLinked: Boolean,              // true=由学校门户导入（可同步）；false=纯手动本地学期（永不同步、永无 reauth 通知）
  val profileId: ProfileId,               // 创建时克隆绑定的作息 profile；创建后不可变（§3.5.1）
  val sourceFingerprint: String?          // 最近一次成功同步的学校内容指纹（§8.3）；纯手动学期为 null
)
```

- **双状态分离（不变量 §1-13/14）**：
  - `isCurrentAcademicSemester`：仅两条路径可写——① 新学期导入成功的事务里“新学期置 true、其余全部置 false”；② 后台/手动同步在确认学校已进入新学期并成功导入后同样处理。浏览/切换/查看历史学期永远不写此字段。
  - `viewedSemesterId`：UI 正在查看的学期，存 DataStore（key `viewed_semester_id`），仅由用户在 UI 中的学期切换写；默认与回退值为 academic-current 学期。
  - 学校同步（手动 `↻` 与 WorkManager）**只读** `isCurrentAcademicSemester && portalLinked` 的学期；`viewedSemesterId` 对同步逻辑不可见。
- **2026 秋季官方基准（作为常量/首次默认值固化）**：2026-08-30 开学注册、2026-08-31 上课、2027-01-15 学期结束、共 20 教学周。App 固定 Monday-first ⇒ `week1Start = 2026-08-31`，`totalWeeks = 20`。
- 周计算纯函数（`WeekCalculator`）：
  - `weekNumberOn(date, semester): Int?` = `WEEKS.between(week1Start, date) + 1`，超出 `[1, totalWeeks]` 返回 null
  - `weekStart(semester, week): LocalDate`（必为周一）、`weekRange(semester, week): LocalDateRange`
  - `naturalWeekToday(semester, today): Int?`
- `week1Start` 是当前自然周的唯一 authority；不引入其他日期推断源。
- 新学期第一次同步：尽可能从学校网页自动识别 displayName / academicYear / term / week1Start / totalWeeks / startDate / endDate（`SemesterMetaParser`，见 §6.3）；**识别不足时才弹一次确认界面**（SemesterConfirmSheet，预填已识别值）；确认后与首份学校快照在**同一个数据库事务**内永久保存（§8.2）。
- 首次启动“稍后手动创建”路径：以 2026 秋季官方基准常量本地创建学期（`isCurrentAcademicSemester=true`、`portalLinked=false`、`sourceFingerprint=null`、`profileId`=bundled 克隆），随后进入课表（见 §5.7）。

### 3.3 Course / CourseMeeting（学校数据，只读）

```kotlin
data class Course(
  val id: CourseId,              // 本地 UUID；仅作外键，不参与 fingerprint/diff
  val semesterId: SemesterId,
  val sourceCourseKey: String,   // 稳定业务身份（§8.3.1）；学校提供，缺失时由 normalizer 以 "name:" + courseName 合成
  val courseCode: String,
  val name: String,
  val credits: Double?,          // 可空；缺失=soft issue（§6.3），显示为 “—”
  val courseType: String?,
  val source: ItemSource = ItemSource.SCHOOL
)

data class CourseMeeting(
  val id: MeetingId,             // 本地 UUID；仅作主键
  val courseId: CourseId,
  val weekday: Int,              // 1..7 = 周一..周日
  val startPeriod: Int,          // 校方课程只存节次
  val endPeriod: Int,
  val weekPattern: WeekPattern,
  val location: String,
  val teacherNames: List<String>,
  val source: ItemSource = ItemSource.SCHOOL
)

enum class ItemSource { SCHOOL, MANUAL }
```

- **课程与实际上课安排分离**；课表渲染使用 CourseMeeting。
- 拆分规则：同一门课若不同周的老师/教室不同（如 高等无机化学 周五 3–5 节：吴长征 2–6 周 / 刘斯 7–12 周 / 郭宇桥 13–18 周），normalizer 必须拆成多条 meeting；中间某周换教室同样继续拆。UI 不承担 assignment matching。
- 学校课程与 meeting 只读：不提供编辑/删除 UI。

### 3.4 ManualScheduleItem（手动项目）

```kotlin
data class ManualScheduleItem(
  val id: ManualItemId,
  val semesterId: SemesterId,
  val title: String,
  val weekday: Int,              // 1..7
  val startTime: LocalTime,      // 分钟粒度的任意时间；不要求对应节次
  val endTime: LocalTime,
  val weekPattern: WeekPattern,
  val location: String?,
  val note: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
  val source: ItemSource = ItemSource.MANUAL
)
```

- **时间合法性（不变量 §1-15）**：`startTime/endTime` 必须位于**该学期绑定 profile 的 day window**（§3.5.1）内；分钟粒度任意，不要求落在节次上；校验：`end > start`、同一天、两端均在窗口内。轴外时间一律拒绝（编辑器校验失败禁用保存；长按草稿生成时已 clamp）。
- 长按周一到周日任意空白区域新增；由长按位置推算 **viewed 学期** / 当前查看周 / weekday / 近似开始时间（`LongPressResolver`），开始时间**向下吸附到 5 分钟**；默认时长 45 分钟（UI 默认值，可改），并 clamp 到 day window 内。
- 手动项目属于**viewed 学期**（用户正在查看的学期，含历史学期——历史学期可继续增补本地记录，这不违反不变量 §1-14，因为它是纯本地写）。
- 周次模式至少支持三种：① 仅当前周；② 连续区间（如 3–12）；③ 自定义集合（如 2-6,8,10-12）。不实现通用 Calendar RRULE。
- 可编辑、可删除；**同步的服务端替换集合永远只含 `source=SCHOOL` 数据**。

### 3.5 ScheduleProfile（作息时间）

```kotlin
data class PeriodTime(val number: Int, val start: LocalTime, val end: LocalTime)
data class ScheduleProfile(
  val id: ProfileId,
  val name: String,
  val isBundledOfficial: Boolean,
  val periods: List<PeriodTime>   // 13 项，number 1..13
) {
  fun timeRange(startPeriod: Int, endPeriod: Int): LocalTimeRange  // 显示时把节次转真实时间
  fun dayWindow(): LocalTimeRange                                  // min(start)..max(end)
}
data class LocalTimeRange(val start: LocalTime, val endInclusive: LocalTime) {
  operator fun contains(t: LocalTime): Boolean
}
```
（`ProfileId` 与 `CourseId` 等 id 同族定义于 `timetable/domain`：`@JvmInline value class ProfileId(val value: String)`。）
```

**Bundled 官方 2026 秋季作息（APK 内置 JSON 资产，不可被用户修改）**：

| 节次 | 时间 | 节次 | 时间 | 节次 | 时间 |
|---|---|---|---|---|---|
| 1 | 07:50–08:35 | 6 | 14:00–14:45 | 11 | 19:30–20:15 |
| 2 | 08:40–09:25 | 7 | 14:50–15:35 | 12 | 20:20–21:05 |
| 3 | 09:45–10:30 | 8 | 15:55–16:40 | 13 | 21:10–21:55 |
| 4 | 10:35–11:20 | 9 | 16:45–17:30 | | |
| 5 | 11:25–12:10 | 10 | 17:35–18:20 | | |

（课间 09:25–09:45、15:35–15:55 各 20 分钟，其余 5 分钟；午休 12:10–14:00、晚饭 18:20–19:30 按真实间隔体现。）

- 校方课程内部只存 `startPeriod/endPeriod`，显示时由**该学期绑定的 profile**（§3.5.1）转真实时间；手动项目直接存时间。两套并存于同一时间轴。
- 设置 → 学校作息时间：默认只读查看当前 working profile；点击编辑生成**新 profile 行**（clone-on-write，见 §3.5.1），支持逐节修改开始/结束时间与“恢复学校默认”（working profile 指回 bundled 克隆）。bundled 永远只读。
- **禁止把具体作息时间 hardcode 到 Compose 页面**；所有时间来自学期绑定 profile。

#### 3.5.1 Profile 绑定与不可变性

1. `schedule_profiles` 表中的 profile 行**一旦被任何学期引用即不可变**；编辑作息 = 新建一行（clone-on-write），并把 DataStore 的 `active_working_profile_id` 指向新行。
2. `Semester.profileId` 在学期创建时写入：克隆当时的 working profile 为该学期私有行（bundled 官方为一行 seeded 只读数据，同样通过克隆绑定）。**创建后不可变**。
3. 渲染任何学期（课表网格、时间轴、节次→时间换算、手动项目校验）**只用该学期绑定的 profile**。未来修改 working profile、未来 bundled 默认值变化，都不重解释历史学期。
4. 唯一的例外重绑入口：设置 → 学校作息时间 → “应用为当前学期作息”——**仅 academic-current 学期**、显式用户动作、克隆新行并重绑 academic-current 学期；历史学期无任何重绑途径（UI 不出现、仓库不提供）。
5. 数据量极小，克隆行不做去重优化（允许内容重复的行存在）。

---

## 4. 时间轴与课表布局

### 4.1 时间轴

- `TimelineAxis(start, endInclusive)` 由**查看学期绑定的 profile** 推导：`dayWindow()`（bundled 时 = 07:50–21:55）。Y 坐标 = 分钟线性映射：`fraction(t) = minutesBetween(start, t) / minutesBetween(start, end)`。
- 因此 5 分钟课间视觉短、20 分钟课间明显更长、午休/晚饭间隔真实体现。
- 左侧独立时间刻度栏（gutter），在每个节次开始时刻显示小号时间标签，顶部与底部为窗口边界时间。
- 轴外时刻不出现：手动项目校验禁止（§3.4）；当前时间线绘制时 clamp 到轴范围。

### 4.2 布局算法（纯逻辑，`WeeklyTimetableLayout`，全量单元测试）

- 周一→周日固定 7 等宽列；X 轴为列宽比例 0..1。
- `place(blocks, axis): List<PlacedBlock>`，`PlacedBlock(block, weekday, column, columnsInGroup, topFraction, heightFraction)`：
  1. 按 weekday 分组；组内按 `(start, end)` 排序。
  2. 贪心列分配：新块放入“最后一个块 end ≤ 新块 start”的最小编号列；若块与当前开放组内任意块重叠则属于同组，否则关闭当前组。
  3. 组内每块 `width = 1/columnsInGroup`，`xOffset = column/columnsInGroup`。
  4. 端点相接（A end == B start）不算重叠，各自独占整宽。
- 冲突展示：同列内并排缩窄，绝不互相覆盖。
- 课程块保持 Compose child：支持 click、long press、accessibility、semantic testing。

### 4.3 课程卡信息优先级（七列很窄）

1. 必显：课程名称（粗体、省略）、地点。
2. 空间允许（块高/列宽超阈值）再显示：教师、时间。
3. 不得为展示完整长课程名破坏七列宽度（ellipsis，不换行列扩宽）。
4. 无障碍/语义文案：`"高等无机化学，周五 09:45–12:10，第7–12周，TH-B301，刘斯"`。

### 4.4 课程详情（学校课程只读）

点击学校课程块弹出详情（Sheet）：课程名；该 meeting 的 `周五 · 09:45–12:10`、`第 7–12 周`、地点、教师；课程号、学分（缺失显示 “—”）；**完整安排**列表（该课程全部 meeting 的 教师 + 周次 + 地点）。

### 4.5 非当前查看周课程

- 默认只显示 `weekPattern.contains(查看周)` 的课程。
- 设置“显示非当前周课程”开启后，非该周课程以明显淡化（alpha 0.35）绘制。状态持久保存（DataStore），不设计成复杂课表模式系统。

### 4.6 当前时间线

同时满足以下三条件才绘制“现在”横线：① 查看中的学期 == academic-current 学期；② 当前查看周 == 当前自然教学周；③ 今天属于本周（周一–周日）。历史周/未来周绝不绘制。

### 4.7 颜色

- 学校网页颜色不进入 domain model，也不照搬学校原色。
- 同一学期同一课程稳定同色：`paletteIndex = MD5(semesterId + ":" + sourceCourseKey)[0] % palette.size`（以稳定业务键取色，本地 UUID 重建不影响颜色）。手动项目使用 `MD5("manual:" + manualItemId)`——手动项目 id 稳定持久，颜色随之稳定。
- 色板为内置 12 色 Material 3 tonal 配对（container / on-container），资源文件定义。第一版无自定义配色。

---

## 5. UI 规格

### 5.1 首页（唯一主界面）

```
2026-2027 秋季 ▼                  ↻  ⚙

‹                 第 2 周                 ›
                9.7 - 9.13

      一   二   三   四   五   六   日
      7    8    9   10   11   12   13

07:50  ┌课表网格（七列 + 真实时间轴）┐
时刻    │                            │
21:55  └────────────────────────────┘
```

- 无 Dashboard、无底部导航栏。
- 顶栏左侧：学期名（= viewed 学期名）+ ▼ → 点击弹出 SemesterSwitcherSheet（纯本地切换，只写 `viewedSemesterId`）。
- 顶栏右侧：`↻` 手动同步 **academic-current 学期**；`⚙` 轻量圆形按钮进入设置（不用遮挡课程的右下 FAB）。
- `↻` 显示条件：viewed 学期 == academic-current 学期 且 `portalLinked == true`。查看历史学期或纯手动学期时 `↻` 隐藏（避免暗示历史可同步）。
- 周标题行：`‹` / `›` 箭头切周；点击“第 N 周”弹出 WeekSwitcherSheet 快速选择；副标题显示该周日期范围（如 `9.7 - 9.13`）。
- 列头：`一 7` 格式（星期字符 + 当日号）；今天列高亮。
- 左右滑动整张课表切换周次（HorizontalPager，页 = 周次 1..totalWeeks）。
- 启动时定位到 viewed 学期的当前自然教学周（历史学期 clamp 到 1，未来学期 clamp 到 totalWeeks）；查看周不持久化。

### 5.2 周切换汇总

手动滑动、左右箭头、周次快速选择三种方式；全部只改本地查看周，无网络行为。

### 5.3 学期切换（只动 viewed，永不动 academic-current）

SemesterSwitcherSheet：本地学期按 startDate 倒序；academic-current 学期与 portalLinked 状态有视觉标注；点击仅写 `viewedSemesterId`，绝不发 Web 请求、绝不修改 `isCurrentAcademicSemester`、绝不触发任何同步；切换后查看周重置为该学期的当前自然周（历史学期 clamp 到 1）。`viewedSemesterId` 持久化（DataStore），App 重启恢复。

### 5.4 设置入口与页面

见 §8.4（Settings 结构）。没有底部栏，设置从顶部齿轮进入。

### 5.5 同步入口 UX

- `↻`（仅在 §5.1 条件下出现）：对 academic-current 学期执行手动同步，转圈指示。
- 成功有变化：界面内简要反馈（“课表已更新：N 处变化”）。
- 成功无变化：完全静默（仅停止转圈）。
- 登录失效：弹窗「登录状态已失效，已有课表不会受到影响」[取消] [重新登录]；重新登录成功后**自动继续刚才的同步**。
- 其他失败：snackbar 提示失败原因，本地数据不动。

### 5.6 手动项目编辑器（ManualItemEditorSheet）

- 字段：标题（必填）、地点（选填）、备注（选填）、星期（预填自长按列）、开始/结束时间（Material TimePicker，预填吸附值）、周次模式三选一（仅当前周 / 连续区间 a–b / 自定义周次网格 1..totalWeeks 勾选）。
- 校验：标题非空、`end > start`、起止时间均在该学期绑定 profile 的 day window 内（§3.4）；任一不满足禁用保存并提示。
- 学校课程块点击→只读详情；手动块点击→打开同一编辑器（可删除，删除需确认）。学校块与手动块均无长按动作；长按仅作用于空白区域。

### 5.7 首次启动（无多页 onboarding）

```
            课表

    从学校教务系统导入课表

登录仅用于读取你的课表。
App 不保存学校账户密码，
课表数据保存在本机。

         [登录并导入]
        [稍后手动创建]
```

- [登录并导入] → WebView 学校网页登录（自动完成检测，§7.1）→ 成功 → 抓取当前学期两页 → parse → （学期元数据识别不足才弹确认页）→ 建学期 + 首份快照**同一事务**保存（§8.2）→ 进入整周课表。
- [稍后手动创建] → 以 §3.2 的 2026 秋季基准常量本地建学期（`portalLinked=false`），进入空白课表（可长按添加手动项目；无 `↻`）。
- 日常启动绝不展示学校网页。

### 5.8 日期与“今天”

列头日期取自 `weekRange(查看周)`；`今天`（若在查看周内）列头高亮 + 当前时间线（§4.6）。

---

## 6. 学校数据管道（USTC Portal Pipeline）

### 6.1 数据来源（两页都抓，缺一不可）

| 页面 | 提供字段 |
|---|---|
| 选课结果页 | courseCode、courseName、credits、department、course type、teacher summary、teaching weeks |
| 我的课表页 | weekday、periods、week range、location、teacher/week assignment |

- 同步时两页都抓取并合并为 canonical data。
- **不允许只取其一作为唯一真相来源**，除非对真实 HTML 的调查证明另一页完全冗余，且证据记录进本 spec 后才允许偏离。

### 6.2 DTO（`school/ustc/dto`，仅边界内存在）

```kotlin
data class UstcCourseSummary(courseCode, name, credits: Double?, department?, courseType?, teacherSummary?, weeksText?)
data class UstcTimetableEntry(courseName, courseCode?, weekdayText, periodText, weekText, locationText, teacherText)
data class UstcSemesterMetaPartial(displayName?, academicYear?, term?, week1Start?, totalWeeks?, startDate?, endDate?)
data class UstcPortalPage(html: String, finalUrl: String)
```

### 6.3 Parser / Normalizer

- `CourseSelectionPageParser.parse(UstcPortalPage): List<UstcCourseSummary>`（credits 缺失 → null，soft issue）
- `TimetablePageParser.parse(UstcPortalPage): List<UstcTimetableEntry>`
- `SemesterMetaParser.parse(pages): SemesterMetaResult(meta: UstcSemesterMetaPartial, isConfident: Boolean)` — `isConfident=false` 触发确认界面
- 三个 parser 均为**接口**（`school/ustc/parser/ParserInterfaces.kt`），真实实现类以 `Ustc` 前缀命名（F2–F4 产出）；`SyncEngine` 只依赖接口并接受 fake 注入，因此同步核心 evidence-free。
- `LoginPageDetector.isLoginPage(url: String, html: String): Boolean`
- `UstcSnapshotNormalizer.normalize(selection, timetable, meta, profile): NormalizedSchoolSnapshot`
  - 选课页 ↔ 课表页课程匹配：优先 courseCode，其次精确课程名；合成 `sourceCourseKey`（§8.3.1）
  - “吴长征 2–6周 / 刘斯 7–12周 …” 教师-周次分段 → 拆成多条 meeting
  - `单周/双周`、`2-6,8,10-12` → `WeekPattern`（单周→`oddWithin`，双周→`evenWithin`）；教室、教师变化继续拆分
  - 缺失字段 → `NormalizationIssue` 收集（缺失 credits、缺失 courseType 等为 soft warning）；**课表页课程在选课页完全无法匹配**为 hard failure，同步中止
  - 输出 canonical `List<Course> + List<CourseMeeting>`
- 解析/归一化失败一律抛 `SyncError.ParseFailed` / `SyncError.ValidationFailed`。

### 6.4 证据门（不猜 selector）

在拿到 §13 所列真实页面证据之前：不写任何真实 URL / DOM id / class / CSS selector / 登录成功条件；只准备接口、DTO、fixture 机制与 normalizer 逻辑（后两者仅依赖 DTO，不依赖真实 HTML）。禁止用真实账号密码写测试；fixture 必须脱敏。

---

## 7. 认证与会话

### 7.1 登录架构

```
WebView 负责首次登录 → Authenticated session → OkHttp 静默访问课表 URL → HTML parser
```

- 第一次登录可见学校官方网页；日常启动绝不展示。
- App 不自制账号/密码输入框调用私有登录 API；**绝对禁止保存学校密码**；只保存必要 session/cookie。
- **登录完成检测输入闭合**：`LoginPageDetector` 需要 `(url, html)`；WebView 侧只可靠拿到 cookie 与当前 URL。因此登录确认统一走“探测闭环”：
  1. WebView 登录过程中 `CookieManager` 持有学校域 cookie；
  2. 触发检测时，`UstcSessionManager.captureAndVerify()` 用 OkHttp 携带这些 cookie 对 `PortalDescriptor.probeUrl`（低成本的已登录可达页面，默认 = 选课结果页 URL）发 GET；
  3. 探测响应的 `(finalUrl, html)` 交给 `LoginPageDetector.isLoginPage(finalUrl, html)`——输入由此闭合；
  4. 非登录页 → 写入 `SessionBlob` 并返回成功；是登录页 → 抛 `SyncError.AuthenticationExpired`，WebView 停留在登录态。
- **自动完成检测（真实 detector 接线后的最终行为）**：`WebViewLoginActivity` 在每次 `onPageFinished` 检查 `currentUrl` 的 host 是否属于 `PortalDescriptor.sessionHosts`（门户域，即已离开 SSO 登录域）；命中即自动调用 `captureAndVerify`，成功则自动 `setResult(RESULT_OK)` 并关闭，无需用户点任何确认按钮。“我已完成登录”手动按钮仅作为 fallback 保留（自动检测连续 3 次探测均失败时显示提示气泡引导使用）。
- 外部默认浏览器认证：仅当证据证明学校 SSO 支持 OAuth/OIDC/CAS 等可安全回传 session/token 的流程时才实现 `ExternalBrowserAuthProvider`；若只是浏览器 Cookie session，继续用 WebView。禁止读取 Chrome 私有 Cookie。第一版不同时维护两套登录；仅预留 `AuthProvider` 接口缝（YAGNI，不做第二实现）。

### 7.2 Session 存储（URL-aware raw Cookie header 模型）

- 模型：**禁止**把 Cookie 拆解为 `Map<cookieName, cookieValue>`——拆解会破坏跨 host/path 的同名 cookie 与原始属性语义。统一使用 URL-aware raw header：

```kotlin
data class SessionCookieHeader(val requestUrl: String, val cookieHeader: String)
data class SessionBlob(val headers: List<SessionCookieHeader>, val capturedAt: Instant)
interface CookieRetriever { fun cookieHeaderFor(url: String): String? }   // 生产：CookieManager.getInstance().getCookie(url)
```

- 捕获范围：`captureAndVerify()` 对 `PortalDescriptor` 的 `probeUrl / selectionUrl / timetableUrl` 三个目标**分别**取适用于各自 URL 的 raw header；`(requestUrl, cookieHeader)` 完全相同的条目去重。**不得假设三个 URL 必然同 host/path**；等真实证据确认同域后才允许收敛为单 header（收敛须记录进本 spec）。
- 请求时选择：`SessionCookieHeader` 的 scope 键 = 标准化 **scheme + host + effectivePort + path**（默认端口归一：http→80 / https→443；空 path 归一为 `/`；query/fragment 不参与）。`SessionCookieHeader.pickFor(requestUrl, headers)` **只允许精确 scope 匹配**：命中唯一条目返回之，找不到返回 null（该请求不带 Cookie 头）；**不得**借用兄弟 path 或其他 host/port 的 raw header——`CookieManager.getCookie(url)` 已针对具体 URL 完成 scope 过滤，二次放宽只会扩大作用域。header 字符串**原样发送**：不重排、不拆解、不按 cookie-name 去重。
- 重定向策略：手工附加的 raw Cookie header **不得自动泄漏给不同 origin**。页抓取与 probe 统一走 `CookieAwareFetcher`（OkHttp 配置 `followRedirects(false)` + `followSslRedirects(false)`）：手动检查 3xx Location → 解析绝对 URL → **按新跳目标 URL 重新 pickFor**（新 scope 无 header 则该跳不带 Cookie）→ 最多 5 跳，超限抛 `SyncError.NetworkFailed`。三个 URL 的 scope 能否安全合并，必须等真实 USTC portal 证据并修订本 spec 后才允许，当前不得提前假定。
- blob 内容即凭据：只存 header 集与捕获时间，绝不包含用户名/密码。
- 方案：AES-GCM 密钥生成于 **AndroidKeyStore**（`SecretKeyProvider` 抽象；无 StrongBox 机型回落普通 Keystore）；blob 加密后存于 App 私有存储（DataStore/file）。
- **不使用已弃用的 androidx security-crypto（EncryptedSharedPreferences/EncryptedFile）**；以当前 Android 推荐做法（Keystore 持钥 + 私有存储密文）为准。
- 设置提供“清除登录状态”（抹掉密文 + 复位 needReauth）。

### 7.3 数据获取：HTTP 优先，WebView DOM 为 fallback

1. 登录完成后优先 `OkHttp GET` 固定课表 URL（携带 session cookie）。
2. 服务端返回完整 HTML → 直接 parser。
3. 仅当实际验证发现：页面必须执行 JS / HTML 不含课表数据 / session 无法可靠从 WebView 用于 HTTP —— 才启用 `WebViewDomSource` fallback；它同样隐藏在 `SchoolPortalSource` 边界后，UI 与 Room 不知道数据来自 HTTP 还是 DOM。

```kotlin
interface SchoolPortalSource {
  suspend fun fetchCourseSelectionPage(): UstcPortalPage
  suspend fun fetchTimetablePage(): UstcPortalPage
}
```

### 7.4 登录失效

- 检测：响应 HTML 命中 `LoginPageDetector`（输入为该响应的 `(finalUrl, html)`）或被重定向到登录域 → 抛 `SyncError.AuthenticationExpired`，**绝不是 0 courses**，绝不因此清空 Room。
- 手动同步遇失效：见 §5.5 弹窗 + 重登后自动续跑。
- 后台同步遇失效：保留课表；置位 `needReauth`（DataStore）；发“请重新登录”通知（受 §8.5 权限约束）。设置-学校账户显示登录状态。

---

## 8. 同步、Diff 与通知

### 8.1 同步触发策略与目标学期

- **同步目标学期恒为 `isCurrentAcademicSemester == true && portalLinked == true` 的唯一学期**；不存在时手动同步入口隐藏（§5.1）、WorkManager 静默空转（无任何通知、不置 needReauth）。
- 触发：手动刷新（`↻`）+ WorkManager 每周一次静默同步（周期与时机由系统决定，允许灵活窗口）。
- 不做：每次启动自动请求、每小时轮询、常驻服务。
- App 启动始终 Room → 立即显示，与网络无关；`viewedSemesterId` 影响显示，不影响同步目标。

### 8.2 事务化同步流程（SyncEngine）

```
下载两页 → 认证有效 → 全部 parse 成功 → normalize → cross-page validation
→ canonical new snapshot → fingerprint(old vs new) → 相同则静默结束
→ 不同则 diff → 【单个 database-level Room 事务：替换 source=SCHOOL 行 + 写入 sourceFingerprint/lastSyncedAt】
→ （后台路径）发变化通知
```

- 事务边界（**一个 database-level Room transaction**，`androidx.room.withTransaction`）：

```kotlin
suspend fun TimetableDatabase.applySchoolSnapshot(
  semesterId: SemesterId,
  courses: List<Course>,          // 本地 CourseId/MeetingId 在事务内生成
  meetings: List<CourseMeeting>,
  fingerprint: String,
  syncedAt: Instant,
) { /* withTransaction { deleteSchoolRows(semesterId); insertCourses; insertMeetings;
       semesterDao.updateSyncMeta(semesterId, fingerprint, syncedAt) } */ }
```

- 学校行替换、semester 指纹与 lastSyncedAt 更新**必须原子**；事务内任意一步失败全部回滚，本地课表保持完整原样；禁止“先 DELETE 再抓网页”（下载与解析永远发生在事务之前）。
- 首次导入（I2）同样以**一个 database-level 事务**完成，顺序固定：`insert boundProfile`（该学期私有 profile 克隆行）→ `insert semester(profileId = boundProfile.id)` → `setExclusiveAcademicCurrent`（新学期 true、其余 false）→ `insert courses/meetings` → `updateSyncMeta(fingerprint, syncedAt)`；任一步失败整体回滚，**不残留孤儿 profile 行**。
- MANUAL 永远不参与学校 replacement；事务只 touch `source=SCHOOL` 行。
- 后台任务失败（非认证类）静默保留本地数据，等待下个周期。

### 8.3 稳定身份、Fingerprint 与 Diff

#### 8.3.1 稳定业务身份（identity）

- Course 的稳定业务身份 = `(semesterId, sourceCourseKey)`。`sourceCourseKey` 来自学校数据；学校未提供稳定键时 normalizer 以 `"name:" + courseName` 合成——只要课程名不变，跨同步稳定。
- Meeting 无学校侧 id；跨快照 pairing 用 `(sourceCourseKey, weekday)` 桶 + 多阶段确定性配对（§8.3.3），不用本地 UUID。
- 本地 `CourseId/MeetingId/SemesterId(UUID)` 仅为外键/主键，replacement 时可整批重建；**绝不用于 fingerprint、diff pairing、取色**（取色用 sourceCourseKey，见 §4.7）。

#### 8.3.2 Fingerprint（只含学校内容）

- 指纹输入是专用类型 `FingerprintedSchoolContent`，结构上就不含本地字段：

```kotlin
data class FingerprintedSchoolContent(
  val semesterMeta: FingerprintedSemesterMeta, // displayName, academicYear, term, week1Start, totalWeeks, startDate, endDate
  val courses: List<FingerprintedCourse>,      // sourceCourseKey, courseCode, name, credits?, courseType?（按 sourceCourseKey 排序）
  val meetings: List<FingerprintedMeeting>,    // sourceCourseKey, weekday, startPeriod, endPeriod, weekPatternMask, location, teacherNames（全字段排序）
)
```

- **明确排除**：本地随机/生成 id（CourseId、MeetingId、本地 SemesterId）、`importedAt`、`lastSyncedAt`、`isCurrentAcademicSemester`、`portalLinked`、`profileId`、`ManualScheduleItem` 全部内容、通知与 UI 状态。
- 计算：`SchoolSnapshotFingerprint.compute(content): String` = kotlinx-serialization 规范 JSON（集合先排序）→ SHA-256 → hex。测试断言：字段顺序无关、插入顺序无关、任一学校字段变化即变化、任何排除字段变化不影响。
- `old == new`：不写任何数据（`applySchoolSnapshot` 不执行）、不改任何无关数据、不通知、完全静默。注意：指纹不同但 diff 结果为空（如仅有 credits 之类无通知语义的漂移）→ 同样不发通知，仅更新存储的指纹。

#### 8.3.3 Diff（SnapshotDiffer）

- Course 级：按 `sourceCourseKey` 配对 → `CourseAdded` / `CourseRemoved`。学校改课程名会因 `sourceCourseKey` 变化表现为 remove+add（记录在案的设计取舍）。
- Meeting 级：对每个 `(sourceCourseKey, weekday)` 桶做**多阶段确定性配对**（禁止桶内排序后按位 zip——插入一个较早 meeting 会使后续全部错位、产生连锁假 TimeChanged）：
  1. **Stage 1 保持身份**：字段完全相等的 old/new 直接配对，不产生任何 change（未变化 meeting 优先保持原匹配）；
  2. **Stage 2 带虚拟 unmatched 节点的最小代价指派**：剩余项构造候选对，`pairCost` = 差异字段个数（节次 / 周次 / 地点 / 教师，1..4）；**置信阈值 `CONFIDENCE_THRESHOLD = 2`：仅 pairCost ≤ 2（共享 ≥2 字段）的候选允许参与配对**；**未匹配代价 `UNMATCHED_PENALTY = 3`**（每个未匹配 old/new 各计 3）。目标函数 = `Σ pairCost + UNMATCHED_PENALTY × 未匹配数`，取全局最小（等价于带 dummy-unmatched 节点的最小代价二部指派）；总代价并列时按配对序列的双方稳定键 `(startPeriod, endPeriod, weekPatternMask, location, teacherNames)` 字典序取唯一解。桶规模为个位数，穷举即可。由此：空 pairing 不可能胜过任何允许的低代价配对（≤2 < 2×3），低置信（3..4 差异）候选永不配对——算法不为提高匹配数量强迫低置信 pair；
  3. 配对内逐字段比较产生 `TimeChanged / WeekPatternChanged / LocationChanged / TeacherChanged`；
  4. 仍未匹配的 old → `MeetingRemoved`；仍未匹配的 new → `MeetingAdded`。
- 性质保证：单独时间变化产生 `TimeChanged`；单独地点/教师/周次变化产生对应 change；单纯插入/删除不给无关 meeting 伪造 change；无法可靠对应的剩余项宁可 `MeetingRemoved + MeetingAdded`，不伪造精确修改。

```kotlin
sealed class ScheduleChange {
  data class CourseAdded(val courseName: String) : ScheduleChange
  data class CourseRemoved(val courseName: String) : ScheduleChange
  data class MeetingAdded(val courseName: String, val summary: MeetingSummary) : ScheduleChange
  data class MeetingRemoved(val courseName: String, val summary: MeetingSummary) : ScheduleChange
  data class TimeChanged(val courseName: String, val weekday: Int, val oldPeriods: String, val newPeriods: String, val weeks: WeekPattern) : ScheduleChange
  data class LocationChanged(val courseName: String, val weeks: WeekPattern, val old: String, val new: String) : ScheduleChange
  data class TeacherChanged(val courseName: String, val weeks: WeekPattern, val old: String, val new: String) : ScheduleChange
  data class WeekPatternChanged(val courseName: String, val weekday: Int, val old: WeekPattern, val new: WeekPattern) : ScheduleChange
}
```

- 通知仅在有真实变化时发出，示例（`ChangeFormatter`）：

```
课表已更新
高等无机化学
第10周教室：TH-B301 → TH-C204
```

- “同步成功但无变化”完全静默。

### 8.4 Settings（无底部栏，顶部齿轮进入）

```
课表
  学校作息时间        （查看 working profile / 编辑=clone-on-write 新行 / 恢复学校默认 / 应用为当前学期作息[仅 academic-current]）
  显示非当前周课程    （开关，持久化）
同步
  上次同步时间
  每周静默同步        （开关，默认开启；开启时请求通知权限，见 §8.5）
  立即同步            （同步 academic-current；无 portalLinked 学期时禁用）
学校账户
  登录状态            （未登录 / 已登录（时间）/ 已失效(needReauth)）
  重新登录
  清除登录状态
关于
  数据与版本
  App 版本
```

**禁止出现**：周一/周日作为第一天、是否显示周末、日/周视图、五日/七日课表（产品 invariant，§1）。

### 8.5 通知权限策略（Android 13+ POST_NOTIFICATIONS）

1. Manifest 声明 `android.permission.POST_NOTIFICATIONS`；API < 33 无需运行时请求。
2. 请求时机（前台、单次、不循环）：① 用户首次把“每周静默同步”打开时；② 用户首次进入设置 → 同步分区时。二者取先，之后不再主动重复弹窗。
3. 拒绝后：App 功能不受影响——后台同步照常执行，仅变化通知与 reauth 通知被静默抑制；设置 → 同步分区显示一行提示“通知权限未授予，课表变化将不会提醒”，点击可跳系统设置。
4. 通知发送侧（`SyncNotification`）发送前检查 `NotificationManagerCompat.areNotificationsEnabled()`，未授权直接 no-op，不崩溃不重试。

---

## 9. 持久化设计

### 9.1 Room（`TimetableDatabase` v1）

| 表 | 列（要点） |
|---|---|
| `semesters` | id PK、displayName、academicYear、term、week1Start(epochDay)、totalWeeks、startDate、endDate、importedAt、lastSyncedAt?、isCurrentAcademicSemester、portalLinked、profileId(FK→schedule_profiles)、sourceFingerprint? |
| `schedule_profiles` | id PK、name、isBundledOfficial、periodsJson；**被学期引用后不可变**（§3.5.1） |
| `courses` | id PK、semesterId（索引+FK）、sourceCourseKey、courseCode、name、credits?、courseType?、source |
| `course_meetings` | id PK、courseId（索引+FK 级联）、weekday、startPeriod、endPeriod、weekPatternMask(Long)、location、teacherNames(连接串)、source |
| `manual_items` | id PK、semesterId（索引）、title、weekday、startMinutes/endMinutes(当日分钟数)、weekPatternMask、location?、note?、createdAt、updatedAt |

- TypeConverters：`WeekPattern ↔ Long`、`LocalTime ↔ Int(分钟)`、`List<String> ↔ String`、`LocalDate ↔ epochDay`、`Instant ↔ epochMilli`。
- **唯一写路径**：`TimetableDatabase.applySchoolSnapshot(semesterId, courses, meetings, fingerprint, syncedAt)`（`androidx.room.withTransaction`，database-level）覆盖“学校行替换 + semester.sourceFingerprint/lastSyncedAt 更新”（§8.2）；DAO 不单独暴露“删学校行”的公开方法给 sync 层。
- v1 无迁移（首版）；学期/数据量小，无容量优化需求。

### 9.2 DataStore keys

| key | 类型 | 含义 |
|---|---|---|
| `viewed_semester_id` | String? | UI 正在查看的学期；缺省/失效回退 academic-current 学期 |
| `show_non_current_week` | Bool（默认 false） | 显示非当前周课程（淡化） |
| `weekly_sync_enabled` | Bool（默认 true） | 每周静默同步 |
| `active_working_profile_id` | String | 作息编辑的 working profile；缺省 = bundled 克隆 |
| `need_reauth` | Bool | 后台同步发现登录失效 |
| `last_sync_finished_at` | Long? | 设置页展示 |
| `notifications_request_shown` | Bool | §8.5 权限请求已发生过 |

---

## 10. 错误模型

```kotlin
sealed class SyncError {
  data object AuthenticationExpired : SyncError   // 登录页/被踢（detector 输入 = 探测或抓取响应的 (url, html)）
  data object ParseFailed : SyncError             // HTML 解析失败
  data object ValidationFailed : SyncError        // cross-page validation 失败
  data object NetworkFailed : SyncError           // 网络不可达/超时
}
sealed class SyncResult {
  data object NoChange : SyncResult
  data class Success(val changes: List<ScheduleChange>) : SyncResult
  data class Failed(val error: SyncError) : SyncResult
}
```

所有错误路径在 SyncEngine 内保证不写 Room（§8.2）；UI 按类型映射文案（§5.5 / §7.4）。

---

## 11. 测试策略

- 框架：JUnit 4.13.2 + Robolectric 4.16.1（Room、Compose 组件、WorkManager 用 Robolectric 跑，`@Config(sdk=[36])`，JDK 21 运行；Compose 组件测试位于 `app/src/test`）+ Compose UI Test（androidTest，Phase J 真机/模拟器全量跑）+ OkHttp MockWebServer3 5.5.0（HTTP 层）+ Turbine 1.2.1（Flow）+ coroutines-test。
- 顺序纪律：domain / parser / week calculation / diff / layout calculation 一律先 failing test 后实现。
- **解析 fixture 一律脱敏**（姓名/学号替换）；禁止真实账号密码出现在测试中；真实 HTML fixture 落位于 `app/src/test/resources/fixtures/ustc/`。
- 覆盖矩阵（对应批准要求 §28 + r2 修正项）：

| 领域 | 必测点 | 落点（子计划-Task） |
|---|---|---|
| Domain | WeekPattern parse/format/contains、周数计算、Monday-first 边界、学期切换、time/period 换算 | 01-A1/A2/A3/A4 |
| 状态分离 | 浏览历史不改 isCurrentAcademicSemester；viewed 持久化与回退；同步目标=academic-current && portalLinked | 02-C2、03-G2/H3 |
| Profile 绑定 | 学期绑定克隆、working 编辑不重解释历史、重绑仅 academic-current | 01-A6、04-I3 |
| Parser | 脱敏 fixture：普通课、多教师按周、多地点、单双周、缺字段、坏 HTML、登录页识别 | 03-F1/F2/F3/F4/F6 |
| Identity/Fingerprint | 排除本地 id/lastSyncedAt/importedAt；顺序无关；任一学校字段敏感 | 03-H1 |
| Diff | TimeChanged 真实产生（时间变化非增删）、MeetingAdded/Removed、精确 diff、指纹相同零 diff | 03-H1 |
| Sync | 成功替换、解析失败保旧、网络失败保旧、认证失效保旧、MANUAL 存活、单事务原子性 | 03-G2 |
| Layout | 七列 X、真实时间 Y、5/20 分钟间隔、重叠分组、七列全宽、07:50/21:55 边界 | 01-B1、02-B2 |
| Manual | 单周、连续周、任意周集合、day window 内任意分钟、5 分钟吸附、轴外拒绝 | 02-D1/D2 |
| 通知权限 | 未授权 no-op；拒绝后功能正常；请求一次性 | 03-H2/H3、04-J2 |
| UI (instrumentation) | 首启、七列可见、切周、切学期、课程详情、长按新建、编辑/删除手动、设置、重登流程 | 02-*、04-J1 |

---

## 12. 第一版明确排除（YAGNI）

多学校；云同步；账号系统；iOS；桌面端；通用日历 RRULE；ICS 双向同步；教务成绩；作业管理；番茄钟；校园地图；社交功能；AI 功能；自动识别假期调休并改课。教务处日历的节假日信息第一版只作参考，不自动改变课程。

---

## 13. 证据门槛（进入 USTC portal/parser 实装前必须由用户提供）

1. 选课结果页与我的课表页**登录后的完整 URL**（含域名、端口、查询参数）。
2. 两页登录后**保存的完整 HTML**（DevTools Save / `curl -b cookie`），已脱敏（姓名、学号等个人信息替换）。
3. 登录方式与登录页 URL（统一身份认证类型；登录成功后的跳转特征，用于界定 `sessionHosts`）。
4. 若课表数据由 XHR 返回：相关请求 URL 与响应样例（HTML/JSON）。
5. 查看网页源代码（Ctrl+U）是否直接包含课表数据（决定是否需要 `WebViewDomSource` fallback）。
6. 我的课表页的周次切换控件行为（用于 week1Start / totalWeeks 识别策略）。
7. 一份**登录失效/被踢出后**的页面 HTML（LoginPageDetector fixture）。
8. 会话有效期与同端互踢行为（影响 needReauth 提示）。
9. 适合做 `probeUrl` 的低成本已登录可达页面建议（默认取选课结果页 URL，可依据实际情况替换）。

在拿到以上证据前：Phase F2–F4、F6 与 G1 的真实接线、I2 的真实导入**被阻塞**；A–E、F1、F5、G2（fake source）不受阻塞。

---

## 14. 真机验收标准（Phase J，必须真机完成，不能只靠模拟器/单测）

1. 冷启动无网，本地已有课表立即显示。
2. 一屏固定显示周一到周日。
3. 2026 秋季第 1 周从 2026-08-31 开始。
4. 真实时间轴符合官方 13 节时间。
5. 周六长按可以创建一次讲座。
6. 可创建 3–12 周重复组会。
7. 自定义周次能表达 2-6,8,10-12。
8. 手动项目与学校课程共存。
9. 同步后手动项目不消失。
10. 登录失效后旧课表不消失。
11. 历史学期切换不产生 Web request。
12. 学校课表无变化时每周同步不产生通知。
13. 学校数据变化时通知给出具体 diff。
14. 退出并重启 App 后所有学期及自定义项仍存在。
15. 浏览历史学期并退出重启后，academic-current 学期不变，每周同步仍只同步 academic-current 学期（历史学期数据不变、无网络请求）。
16. Android 13+ 拒绝通知权限后：同步功能正常、不再重复弹权限请求、设置显示“通知未授予”提示。

---

## 15. 术语与命名（全仓库一致）

| 名称 | 含义 |
|---|---|
| `WeekPattern` | 周集合位掩码值类型 |
| `Semester / Term` | 学期 / 学期季节枚举；含 `isCurrentAcademicSemester`、`portalLinked`、`profileId` |
| `viewedSemesterId` | UI 查看中的学期（DataStore），与 academic-current 严格分离 |
| `Course / CourseMeeting / ManualScheduleItem` | 学校课程 / 上课安排 / 手动项目 |
| `ItemSource { SCHOOL, MANUAL }` | 数据来源 |
| `sourceCourseKey` | Course 稳定业务身份；缺失时 `"name:" + courseName` 合成 |
| `ScheduleProfile / PeriodTime / LocalTimeRange / ProfileId` | 作息 profile / 单节时间 / 时间区间 / profile 主键 |
| `Semester.profileId` | 学期创建时克隆绑定的 profile；不可变 |
| `WeekCalculator` | 周数/周区间纯函数 |
| `TimelineAxis / TimedBlock / PlacedBlock / WeeklyTimetableLayout` | 时间轴 / 可布局块 / 布局结果 / 布局纯函数 |
| `LongPressResolver` | 长按坐标 → (weekday, 吸附开始时间) |
| `NormalizedSchoolSnapshot` | normalizer 输出（courses + meetings + issues） |
| `FingerprintedSchoolContent / SchoolSnapshotFingerprint` | 指纹专用内容类型 / 指纹计算 |
| `SnapshotDiffer / ScheduleChange / MeetingSummary / ChangeFormatter` | diff（多阶段最小代价 meeting 配对）/ 变更类型 / meeting 摘要 / 文案格式化 |
| `SchoolPortalSource / UstcHttpPortalSource / LoginPageDetector / PortalDescriptor` | 数据源边界 / HTTP 实现 / 登录页识别 / 门户参数集 |
| `UstcSessionManager.captureAndVerify` | 登录探测闭环：对 probe/selection/timetable 三个目标 URL 分别收集 raw Cookie header（去重）→ OkHttp GET probeUrl → `LoginPageDetector((finalUrl, html))` → 存/抛 |
| `SessionCookieHeader / SessionBlob` | URL-aware raw Cookie header（scope=scheme+host+effectivePort+path，仅精确匹配；不拆 Map）/ 加密会话内容 |
| `CookieAwareFetcher` | 手动重定向页抓取：逐跳按目标 URL 重新 pickFor，raw header 不跨 origin 泄漏 |
| `SessionStore / SecretKeyProvider` | 会话加密存取 / Keystore 密钥 |
| `SyncEngine / SyncError / SyncResult / WeeklySyncWorker / SyncScheduler` | 同步引擎 / 错误 / 结果 / 周任务 / 调度 |
| `applySchoolSnapshot` | database-level 单事务：学校行替换 + 指纹/lastSyncedAt |
| `SemesterRepository / TimetableRepository / ManualItemRepository / ScheduleProfileRepository / SettingsStore` | 仓库与设置存储 |
| `NotificationPermissionController` | POST_NOTIFICATIONS 一次性请求与状态查询 |

---

## 修订记录

- r1（2026-08-30）：按已批准 30 节需求初版固化。
- r2（2026-08-30）：corrective pass——① academic-current 与 viewed semester 严格分离，历史浏览不改 `isCurrentAcademicSemester`，同步恒指 academic-current；② `Semester.profileId` 不可变克隆绑定 + profile clone-on-write（§3.5.1）；③ `Course.credits` 改 nullable；④ 稳定 identity（sourceCourseKey）与 fingerprint 排除清单（§8.3.1/8.3.2）；⑤ meeting diff 配对重写（TimeChanged 真实产生，新增 MeetingAdded/Removed）（§8.3.3）；⑥ 登录检测输入闭合 + 自动完成检测（§7.1/7.2）；⑦ 学校行替换与 semester 指纹/lastSyncedAt 合并为单个 database-level 事务 `applySchoolSnapshot`（§8.2）；⑧ 手动时间限定为绑定 profile day window 内任意分钟（§3.4）；⑨ `portalLinked` 状态与纯手动学期同步豁免（§3.2/§8.1）；⑩ Android 13+ POST_NOTIFICATIONS 权限策略（§8.5）。
- r3（2026-08-30）：final corrective pass——① Session 模型改为 URL-aware raw Cookie header（`SessionCookieHeader/SessionBlob`，禁止 `Map<name,value>`；三目标 URL 分别收集、去重、`pickFor` 选择；多 host/重复 cookie-name 语义不破坏，收敛需证据）（§7.2）；② meeting 配对改为多阶段确定性（Stage 1 恒等保持 + Stage 2 最小代价），删除按位 zip（§8.3.3）；③ 导入事务纳入 boundProfile 克隆并固定顺序、失败无孤儿 profile（§8.2）；④ 三 parser 明确为接口、实现类 `Ustc` 前缀，SyncEngine 只依赖接口（evidence-free）（§6.3）；⑤ 构建采用 AGP 9.3 Built-in Kotlin（见 roadmap A0），同步布局/依赖图/子计划机械整改。
- r3.1（2026-08-30）：docs-only hotfix——① Compose BOM 固定 **2026.06.00**（ui 1.11.4 / material3 1.4.0）：core ≤1.11.x 的最后 stable BOM，匹配 compileSdk 36 与 Robolectric 4.16（SDK 36 上限）；1.12 需 compileSdk 37，不采用 Robolectric beta（§2.1）；② `SessionCookieHeader` scope 键 = 标准化 scheme+host+effectivePort+path，`pickFor` 仅精确匹配、找不到即 null，删除 same-host/cross-path fallback；新增 `CookieAwareFetcher` 手动重定向策略（逐跳重选头、禁止跨 origin 泄漏、≤5 跳）（§7.2）；③ SnapshotDiffer Stage 2 重定义为带虚拟 unmatched 节点的最小代价指派：`CONFIDENCE_THRESHOLD=2`、`UNMATCHED_PENALTY=3`，目标函数与 tie-break 完整写入（§8.3.3）；④ 全部文档修订元数据统一 r3.1；A0 git 基线步骤改为 init→status untracked 核对→commit docs→status clean 核对；⑤ H3 测试契约：gate 权威在 SyncEngine，`manual_only_semester_makes_zero_portal_source_calls_and_no_notification`。
