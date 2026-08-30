# USTC 课表 App 实施计划（Roadmap）

- 日期：2026-08-30
- 修订：r3.1（docs-only hotfix；未写任何 production 代码）
- 配套规格（唯一需求来源）：[docs/superpowers/specs/2026-08-30-ustc-timetable-design.md](../specs/2026-08-30-ustc-timetable-design.md)（下称 SPEC，`SPEC §n` 指其章节）
- 本文件是**路线图**：版本组合、任务索引、依赖顺序、证据门、覆盖矩阵。可执行细节在 4 份子计划中，每份子计划均引用同一份 SPEC，不引入 SPEC 之外的需求。

## 子计划文件

| 子计划 | 覆盖 Task | 主题 |
|---|---|---|
| [subplan-01-foundation.md](subplan-01-foundation.md) | A0–A6, B1 | 脚手架（固定版本组合）、domain、Room、profile 绑定、布局纯函数 |
| [subplan-02-timetable-ui-manual.md](subplan-02-timetable-ui-manual.md) | B2, B3, C1–C4, D1–D3 | 课表网格 UI、周/学期切换、详情、非本周淡化、手动项目 |
| [subplan-03-portal-sync.md](subplan-03-portal-sync.md) | E1, E2, F1, F5, F2–F4, F6, G1–G3, H1–H3 | 认证与会话、parser/normalizer、同步引擎与事务、diff/通知/周任务 |
| [subplan-04-integration-qualification.md](subplan-04-integration-qualification.md) | I1–I4, J1–J3 | 首启导入、Settings、确认页、全量回归与真机验收 |

## 执行纪律（全部子计划共同遵守）

1. **TDD 六步**：每个 Task 固定为 checkbox 步骤：写 failing test → 运行并观察预期 RED → 最小实现 → 运行 GREEN → 定向回归 → commit。RED 的判定：测试代码引用未实现符号时**编译失败**，或断言失败，两者都算预期 RED；除此之外的失败（环境错误等）必须先修复环境。
2. **路径约定**：所有文件路径相对仓库根（即 `X:\桌面\schedule`，A0 后为 git repo 根）；验证命令均在仓库根执行且必须以 `./gradlew` 开头；测试过滤一律用完整 FQCN（可带包级后缀通配，如 `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.*"`），禁止省略包名前缀的缩写形式。
3. **证据门**：`school/ustc` 的真实 URL/selector/登录判定，在用户交付 SPEC §13 证据前一律不写；不凭截图猜测。
4. **范围锁**：不做 SPEC §12 之外的功能；不引入 Hilt/多模块/跨平台框架；不改 SPEC r3.1 的任何不变量（§1）。
5. **Commit**：信息格式 `phaseA1: weekpattern value type with parse/format/contains`；commit 前 Task 验证命令必须全绿；`local.properties`、`.idea/`、`build/` 不入库。
6. **环境**：Windows + Git Bash；JDK 21（`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\`）；Android SDK 位于 `C:\Users\Forstargazing\AppData\Local\Android\Sdk`（A0 写入 `local.properties`）。**仓库根固定为 ASCII 路径 `X:\schedule`**（Gradle 9.5 在 Windows 非 ASCII 项目路径下 test worker 类加载失败，A0 实测后经批准迁移）。

## A0 固定版本组合（已核实，2026-08-30）

经 Google Maven / Maven Central metadata 与 AGP 官方 release notes 核实的**精确组合**，写入 `gradle/libs.versions.toml`，执行时不允许"latest stable"式浮动：

| 组件 | 固定版本 | 依据 |
|---|---|---|
| Android Gradle Plugin | **9.3.0**（stable，2026-07） | 官方 release notes：要求 Gradle ≥ 9.5.0、JDK ≥ 17、build-tools 36.0.0、支持至 API 37 |
| Gradle wrapper | **9.5.0** | AGP 9.3.0 的最低/默认要求 |
| Kotlin | **2.4.10**（stable，2026-07-14） | AGP 9.3 默认 **Built-in Kotlin**：KGP 是 AGP 的运行时依赖（最低 2.2.10，自动满足），**不再应用 `org.jetbrains.kotlin.android`** |
| Compose compiler 插件 | **org.jetbrains.kotlin.plugin.compose 2.4.10** | Built-in Kotlin 下**仍需在 plugins 块显式应用**（版本随 Kotlin；KGP 2.3.1+ 支持 built-in Kotlin） |
| serialization 插件 | **org.jetbrains.kotlin.plugin.serialization 2.4.10** | 同上，显式应用 |
| KSP | **2.3.11** | ≥ 2.3.1 支持 AGP 9.0 built-in Kotlin；自 2.3.0 起版本与 Kotlin 解耦 |
| compileSdk | **37** | A0 实测：navigation 2.10.0 / core 1.19.0 / lifecycle 2.11.0 / okhttp-android 5.5.0 的 AAR minCompileSdk=37（用户批准 Option B）；已装 platform android-37.0 |
| targetSdk | **36** | 有意决策：App runtime behavior authority 保持 36；compileSdk 37 仅作为编译 API surface 满足 AAR minCompileSdk |
| Robolectric 测试 SDK | **@Config(sdk=[36])** | Robolectric 4.16.1（stable，上限 SDK 36）；单测运行于 SDK 36，与 compileSdk 37 无关 |
| minSdk | **26** | 原生 java.time，无需 desugaring |
| Compose BOM | **2026.06.00**（实测解析 ui/foundation/runtime **1.11.3**、material3 **1.4.0**） | core ≤ 1.11.x 的 stable BOM；**不升级 Compose 1.12、不采用 Robolectric 4.17 beta** |
| Room | **2.8.4** | Google Maven 最新稳定 |
| WorkManager | **2.11.2** | 最新稳定（2.12.0-rc01 不采用） |
| navigation-compose | **2.10.0** | 最新稳定 |
| DataStore | **1.2.1** | 最新稳定 |
| lifecycle-* | **2.11.0** | 最新稳定 |
| activity-compose | **1.13.0** | 最新稳定 |
| core-ktx | **1.19.0** | 最新稳定 |
| OkHttp / MockWebServer3 | **5.5.0** | Maven Central 最新稳定 |
| Jsoup | **1.23.2** | 最新稳定 |
| kotlinx-serialization-json / coroutines | **1.11.0 / 1.11.0** | 最新稳定 |
| JUnit / Robolectric | **4.13.2 / 4.16.1** | Robolectric 4.16.x stable（最新 patch 4.16.1）支持 SDK 36 且**要求 Java 21**（与本机 JDK 21 匹配）；不得使用 4.17 beta |
| Turbine | **1.2.1** | 最新稳定 |
| androidx.test.ext:junit / test:runner | **1.3.0 / 1.7.0** | 最新稳定 |

- **Built-in Kotlin 决策（A0 落地，依据官方 Migrate to built-in Kotlin 指南）**：AGP 9.3 默认启用 built-in Kotlin——新工程**不应用** `org.jetbrains.kotlin.android`（catalog、根 alias、app application 三处均无）；`kotlin { compilerOptions {} }` 仍为官方 DSL，`jvmTarget` 默认取 `android.compileOptions.targetCompatibility`，无需显式设置；**禁止**设置 `android.builtInKotlin=false` / `android.newDsl=false` 退回 legacy。
- 回退规则（仅当 A0 构建/依赖解析失败时使用，须在 commit 信息注明）：KSP 在 2.3.x 内升 patch；**Compose BOM 2026.06.00 解析失败时不得擅自改用 Robolectric beta 或 Compose 1.12，先停下向用户报告**；若仍需整组降级，使用备选组合 **AGP 8.13.x + Gradle 8.14.x + Kotlin 2.2.20 + KSP 2.2.20-2.0.4 + Compose BOM 2025.06.01**，并**恢复 `org.jetbrains.kotlin.android` 插件**（AGP 8 无 built-in Kotlin），其余库版本不变。
- compileSdk=37 / targetSdk=36 决策来源：A0 实测 AAR minCompileSdk 冲突（navigation 2.10.0 / core 1.19.0 / lifecycle 2.11.0 / okhttp-android 5.5.0），用户批准 Option B——compileSdk 37 仅作编译 API surface，targetSdk 36 仍为 runtime behavior authority；已选依赖一律不降级；不升级 Compose 1.12、不采用 Robolectric 4.17 beta、不提高 targetSdk。
- 本机 JDK 21 满足：AGP 9.3.0（需 ≥17）、Gradle 9.5（支持 17–21+）、Robolectric 4.16 SDK 36（需 21）。

## 任务索引

| Task | 一句话 | 子计划 |
|---|---|---|
| A0 | git init + Gradle 脚手架（固定版本组合）+ 冒烟测试 | 01 |
| A1 | WeekPattern 位掩码 + parse/format/contains/单双周 | 01 |
| A2 | Semester/Term + WeekCalculator（Monday-first） | 01 |
| A3 | ScheduleProfile + period↔时间 + bundled 资产 + LocalTimeRange | 01 |
| A4 | Course/CourseMeeting/ManualScheduleItem + 不变量（credits 可空、day window 语义在 D2 校验落地） | 01 |
| A5 | Room schema + DAO + `applySchoolSnapshot` 单事务 | 01 |
| A6 | 仓库层（viewed/working/绑定 profile）+ AppContainer | 01 |
| B1 | TimelineAxis + WeeklyTimetableLayout + snap5（纯函数） | 01 |
| B2 | WeeklyTimetableGrid Compose 组件（children 块/淡化/长按/语义） | 02 |
| B3 | TimetableViewModel + TimetableScreen + DebugSeed | 02 |
| C1 | 周切换（Pager/箭头/周选择器） | 02 |
| C2 | 学期切换（只写 viewedSemesterId） | 02 |
| C3 | 课程详情 Sheet + 周次/时间格式化 | 02 |
| C4 | 显示非当前周课程开关（淡化持久化） | 02 |
| D1 | LongPressResolver（七列→weekday、5 分钟吸附、day window clamp） | 02 |
| D2 | ManualItemEditorSheet（三周次模式 + day window 校验） | 02 |
| D3 | 手动项目编辑/删除接线 | 02 |
| E1 | SessionStore（Keystore AES-GCM） | 03 |
| E2 | PortalDescriptor + WebView 登录外壳 + captureAndVerify 探测闭环 + 自动完成检测 | 03 |
| F1 | DTO + fixture 机制 + 证据请求（GATE 发出） | 03 |
| F5 | UstcSnapshotNormalizer（不依赖真实 HTML，先行） | 03 |
| F2 | 选课结果页 Parser 实现类 UstcCourseSelectionPageParser【证据门】 | 03 |
| F3 | 我的课表页 Parser 实现类 UstcTimetablePageParser【证据门】 | 03 |
| F4 | SemesterMetaParser 实现类 UstcSemesterMetaParser【证据门】 | 03 |
| F6 | HeuristicLoginPageDetector【证据门】 | 03 |
| G1 | UstcHttpPortalSource（MockWebServer）【证据门分支末梢；完成后只替换注入实现，不重写 SyncEngine】 | 03 |
| G2 | SyncEngine 管线（只依赖 parser/source **接口**，fake 注入）+ applySchoolSnapshot/importNewSemesterWithSnapshot 单事务 | 03 |
| G3 | 手动同步 UX + 失效弹窗 + 重登续跑 | 03 |
| H1 | FingerprintedSchoolContent + 指纹 + SnapshotDiffer（多阶段最小代价配对） + ChangeFormatter（**完整交付，位于 G2 之前**） | 03 |
| H2 | SyncNotification + NotificationPermissionController（POST_NOTIFICATIONS） | 03 |
| H3 | WeeklySyncWorker（academic-current && portalLinked 门） + SyncScheduler | 03 |
| I1 | FirstLaunchScreen + 稍后手动创建（portalLinked=false 学期） | 04 |
| I2 | 登录导入流 + SemesterConfirmSheet 触发逻辑（真实导入依赖 F2–F4） | 04 |
| I3 | SettingsScreen（含通知权限提示、作息 clone-on-write 编辑、重绑仅 academic-current） | 04 |
| I4 | SemesterConfirmSheet 完整化 | 04 |
| J1 | 全测试套件 + lint（模拟器 connected） | 04 |
| J2 | 真机验收 16 项（SPEC §14） | 04 |
| J3 | 发布构建 sanity | 04 |

## 依赖顺序与证据门

```
主链（evidence-free，一路到可发布的本地版 App）：
A0 → A1 → A2 → A3 → A4 → A5 → A6 → B1 → B2 → B3 → C1 → C2 → C3 → C4 → D1 → D2 → D3
D3 → E1 → E2 → F1 → F5 → H1（完整）→ G2（只依赖 parser/source 接口，fake 注入）→ G3 → H2 → H3
H3 → I1 → I2（fake 路径）→ I3 → I4 → J1 → J2 → J3

证据门分支（gated，可与主链并行推进）：
【SPEC §13 证据】→ F2 → F3 → F4 → F6 → G1
G1 完成后只替换注入实现：UstcPortalDescriptor 真实 URL、UstcCourseSelectionPageParser/UstcTimetablePageParser/UstcSemesterMetaParser、HeuristicLoginPageDetector；
SyncEngine 及下游不改写 → 重跑 G1 定向回归 + 受影响 parser 测试 + J1。
```

- 证据未到时可完成：主链全部（含 G2/G3/H/I1–I4）——App 以本地/手动数据完整可用；被证据门挡住的只有：真实抓取（G1 真实接线）、真实登录自动检测（F6 后才生效）、I2 的真实导入。
- **严禁**为绕过证据门猜测 URL/selector/登录判定（SPEC §6.4）。

## 需要用户提供的 USTC portal 证据

见 SPEC §13 共 9 项（两页登录后 URL；两页脱敏 HTML；登录方式与成功跳转；XHR 响应；源代码是否含数据；周次控件行为；登录失效页 HTML；会话有效期/互踢；probeUrl 建议）。

## 测试覆盖 ↔ Task 对照（SPEC §11）

见 SPEC §11 表格右列（子计划-Task 编号），此处不重复。

## 真机验收 ↔ Phase 对照（SPEC §14，16 项）

1→B3/J2；2→B2/J2；3→A2/J2；4→A3/B1/J2；5,6,7→D2/J2；8→B3/D3/J2；9→A5+G2/J2；10→G2+G3/J2；11→C2/J2；12→H3/J2；13→H1+H2/J2；14→A5/J2；15→C2+H3/J2；16→H2/I3/J2。
