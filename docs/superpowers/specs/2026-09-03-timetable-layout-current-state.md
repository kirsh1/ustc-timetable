# 课表布局现状与改版待决事项（讨论底稿）

> 状态：**讨论材料，不是 Approved Requirements Spec，不构成 production 修改授权。**  
> 整理日期：2026-09-03  
> 代码基线：`1f10900a9f9c72d6a035d36fba1969f9c02b5668`  
> 目的：把当前所有与布局、视觉、导航和窗口适配有关的既有设计、实现事实、测试约束及新需求集中到一处，供下一轮一次性确认。

## 1. 材料边界与权威层级

本文只做事实归档和冲突标注，不选择最终方案。

当前资料的权威顺序如下：

1. 已批准产品规格：`docs/superpowers/specs/2026-08-30-ustc-timetable-design.md`。
2. 已批准的后续 corrective/reconciliation：UI-R2、WeekOverview-R1 及相应文档提交。
3. 当前 `HEAD` 的 production 代码与已锁定测试行为。
4. 2026-09-03 模拟器实拍和本轮用户反馈。
5. 本文“待确认”部分：仅为下一轮讨论清单，尚不覆盖前四项。

主要来源：

- 主规格 §4、§5、§8.4：时间轴、课程卡、首页、周切换、学期切换和设置。
- `docs/superpowers/plans/subplan-02-timetable-ui-manual.md`：B2–D3 的实现边界。
- `docs/superpowers/plans/subplan-04-integration-qualification.md`：Settings 与 AppRoot 边界。
- UI-R2：`dd5b4b4f03ce72451b0b0f2a9f7ca3de5890a1b1`。
- WeekOverview-R1：`678af9884675cbc3f566b2963f14dfcd144c400d`。
- 文档 reconciliation：`b20772d`。
- 当前实现：`TimetableScreen.kt`、`WeeklyTimetableGrid.kt`、`WeekOverviewStrip.kt`、`BlockTexts.kt`、`SettingsScreen.kt`、`SemesterSwitcherSheet.kt`、`AppRoot.kt`、`MainActivity.kt`。

## 2. 当前产品信息架构

### 2.1 顶层页面

当前没有 Dashboard 和底部导航。`AppRoot` 只有三种内部 destination：

```text
Timetable
  └─ Settings
       └─ Profile Editor
```

- 系统返回键：Profile Editor → Settings → Timetable。
- 首次启动完成后进入 Timetable。
- `MainActivity` 持有 Activity-scoped `TimetableViewModel`、`SettingsViewModel` 等实例。
- 页面切换不重新创建这些 ViewModel。

### 2.2 主课表的当前纵向结构

```text
系统状态栏 / display cutout safe area
学期标题                         手动刷新   设置
上一周        第 N 周  周概览     下一周
                 M.d - M.d
一 D     二 D     三 D     四 D     五 D     六 D     日 D
时间栏 |                  七列课表主体
       |                  （占满剩余高度）
系统导航栏 safe area
```

当前共有三个连续占高的头部层级：

1. 学期/操作顶栏。
2. 周导航与日期范围。
3. 星期和日号单行列头。

WeekOverview 展开时插入在周导航和星期列头之间，额外占用固定高度。

## 3. 系统栏、窗口与可用区域

### 3.1 Edge-to-edge

- `MainActivity.enableEdgeToEdge()` 使用透明状态栏和透明导航栏。
- `AppRoot` 对**整个 App 内容**统一应用 `WindowInsets.safeDrawing`。
- 主课表、设置、Profile Editor 都位于 safe drawing 内，不绘制到打孔、状态栏或导航手势区下面。
- 当前顶部视觉空隙由系统 safe inset 与页面自身 padding 共同形成，不只是 `TimetableScreen` 的一处 margin。

### 3.2 已有兼容性目标

- Android 16 / API 36 模拟器已验证 edge-to-edge 基础行为。
- 既有资格记录声明 compact geometry 和 safe-area 自动化已覆盖。
- 真机全屏、自由小窗、折叠/横屏尚未形成完整验收矩阵。
- 当前没有基于窗口宽度等级的专门布局分支。

### 3.3 当前未定义

- 横屏课表的专用头部结构。
- 极窄自由小窗的操作降级顺序。
- 大字体/显示缩放下顶部操作是否换行或收纳。
- 透明壁纸下 system bar scrim、正文对比度及课程色块对比策略。

## 4. 主课表顶栏与周导航

### 4.1 学期/操作顶栏

当前实现：

- 容器：全宽 `Row`。
- 外边距：水平 `12dp`，垂直 `6dp`。
- 左侧：`"${semester.displayName} ▼"`，`titleMedium`。
- 点击学期名：打开 `SemesterSwitcherSheet`。
- 右侧手动同步：文本字符 `↻`，`titleLarge`，水平内边距 `8dp`。
- 右侧设置：文本字符 `⚙`，`titleLarge`，起始内边距 `8dp`。
- 同步中：字符替换为 `20dp`、`2dp` stroke 的局部 `CircularProgressIndicator`。
- 首页刷新只在“当前查看学期是 academic-current、该学期 portalLinked、且 runtime 可用”时出现。

已锁定语义：

- 手动同步目标始终由同步引擎选择 academic-current 学期；不因 viewed semester 改变。
- 历史或本地学期不显示首页刷新，避免暗示可同步。
- 设置入口始终存在。

### 4.2 周导航行

当前实现：

- 结构：居中 `Column`，底部 `4dp`。
- 第一行：`‹`、`第 N 周`、`▦`、`›`。
- 上/下一周字符使用 `headlineMedium`；可点击区域水平 `20dp`、垂直 `4dp`。
- 边界周的箭头仍显示但降到 `0.38` alpha，且 disabled。
- 点击“第 N 周”打开四列 `WeekSwitcherSheet`。
- `▦` 是文本字符，不是图标库矢量；点击展开/收起 WeekOverview。
- 第二行：日期范围，格式为 `M.d - M.d`，`labelMedium`。

已锁定周导航方式：

1. 左右箭头。
2. 左右滑动整张课表的 `HorizontalPager`。
3. 点击周数打开周选择 Sheet。
4. WeekOverview 卡片点击。

上述方式都只改变查看周，不发网络请求。

### 4.3 星期列头

当前实现：

- 左侧先留出与时间栏相同的 `44dp` 空白。
- 七列等宽。
- 列头固定高 `32dp`。
- 每列内部 padding `2dp`。
- 单行文案格式：`一 31`、`二 1`……；只显示日号，不显示月份。
- 今天对应节点有独立 `today_header` 语义标记，但当前没有在 `WeekHeaderCells` 中绘制明确的主题色背景或文字色差。

## 5. WeekOverview 当前设计

### 5.1 外观与尺寸

- 默认收起。
- 展开区域是横向 `LazyRow`。
- 整体固定高 `104dp`。
- 卡片间距 `6dp`。
- 每张卡片宽 `72dp`、高 `98dp`、圆角 `8dp`。
- 卡片内边距 `5dp`。
- 小地图高 `70dp`，顶部 `3dp`，圆角 `4dp`。
- 当前查看周使用 `primaryContainer`、`2dp primary` 边框。
- 自然周在周号旁显示一个 `4dp` 的 `tertiary` 圆点。
- 展开后自动滚动到当前查看周。

### 5.2 数据与状态

- 每周小地图由 raw SCHOOL + MANUAL blocks 单独投影。
- 小地图只包含该周真正生效的 block。
- “显示非当前周课程”产生的 ghost 不进入小地图。
- 点击卡片切换周，并保持概览展开。
- 展开状态只存在于当前 Compose 会话。
- 切换学期时收起。
- 不写 DataStore，不写 SavedState，不发网络请求。

## 6. 七列网格与时间轴

### 6.1 X 轴

- 固定 Monday-first。
- 固定显示周一到周日七列，不提供五日模式或隐藏周末设置。
- 左侧时间 gutter 固定 `44dp`。
- gutter 后剩余宽度由七列完整等分。
- 重叠课程在同一 weekday 内并排分列，不覆盖。
- block 的有效宽度是 `weekdayColumnWidth / columnsInGroup`。

### 6.2 当前 Y 轴权威

当前 approved spec 和 production 使用**线性真实分钟轴**：

```text
fraction(t) = minutes(axis.start → t) / minutes(axis.start → axis.endInclusive)
```

- axis 来自 viewed semester 绑定的 `ScheduleProfile.dayWindow()`。
- bundled profile 当前窗口为 `07:50–21:55`。
- 课程位置、高度、时间标签、当前时间线、长按坐标解析和 WeekOverview 小地图共同依赖这个轴。
- 校方课程保存节次，渲染时经学期绑定 profile 转换为真实时间。
- MANUAL 项目直接保存真实时间，与学校课程共用同一轴。

线性轴的现有效果：

- 5 分钟、20 分钟、午休、晚间间隔会按真实时长占用不同视觉高度。
- 午休 `12:10 → 14:00` 明显占用课表纵向空间。
- 晚间相邻教学组之间的长间隔同样占用真实比例空间。

### 6.3 教学时段分组与背景

当前 `teachingTimeGroups(periods)`：

- 按节次编号排序。
- 相邻节次之间 gap `< 20 分钟` 时属于同一 teaching group。
- gap `>= 20 分钟` 时开启新 group。
- group 完全由 viewed semester 的 profile 推导，不硬编码上午/下午/晚上。

当前背景：

- 六条纵向日列分隔线。
- 每个 `periodStart` 一条横向细线。
- teaching groups 之间按真实轴高度绘制低 alpha 灰色休息带。
- 休息带是着色，不是压缩；它仍然占据对应分钟高度。

### 6.4 时间 gutter

- 标签集合为：所有 period start、所有 teaching group start/end、axis start/end。
- 先去重再排序。
- 标签使用 `LocalTime.toString()`，通常为 `HH:mm`。
- 标签锚在真实分钟轴位置，文字中心尽量对齐锚点。
- 顶底标签会 clamp 在可见区域内。
- 因 group end 与下一 period start 都可能显示，午休边缘会出现 `12:10` 和 `14:00` 两个标签。

### 6.5 当前时间红线

红线来源是 `nowLine`，不是随机 decoration。

只有同时满足以下条件才提供：

1. viewed semester 是 academic-current。
2. viewed week 等于今天对应的自然教学周。
3. 今天落在当前 page 的 Monday–Sunday 日期范围内。

Grid 收到 `nowLine` 后：

- 按 axis 映射 Y。
- 绘制横跨七列的 `2dp` 红色线 `#B3261E`。
- 当前没有限制红线只出现在“今天”这一列。

因此用户观察到“只有某周出现的多余红线”，与当前设计的自然周 now-line 行为一致。

## 7. 课程块当前设计

### 7.1 几何与交互

- block 是真正的 Compose child，不是整版 Canvas bitmap。
- 圆角 `6dp`。
- 无 elevation。
- child 被圆角边界 clip。
- 内边距 `3dp`。
- 点击 SCHOOL：打开只读课程详情。
- 点击 MANUAL：打开编辑器。
- 长按 block：只消费事件，不触发空白区新增。
- 长按空白：通过 grid-local fraction 解析 weekday 和真实时间。

### 7.2 文案层级

当前优先级：

1. 课程名：粗体。
2. 地点：独立单行。
3. 教师：空间足够才显示。
4. 时间：空间足够才显示。

课程名行数规则：

- block 高 `< 42dp`：1 行。
- 有地点且高 `< 96dp`：2 行。
- 无地点且高 `< 72dp`：2 行。
- 其余：3 行。
- 最后一行 ellipsis。

地点显示规则：

- block 高 `> 42dp` 且地点非空。
- 固定 1 行，ellipsis。

可选信息规则：

- `effective group width > 52dp` 才允许教师/时间。
- 教师还要求 block 高 `> 60dp`。
- 时间还要求 block 高 `> 90dp`。
- 两者固定 1 行，ellipsis。

字体现状：

- 课程名、地点、教师、时间没有独立定义 `fontSize` 或专用 typography token。
- 它们继承当前 Compose/Material ambient text style。
- 因而当前没有可统一调节的“课程卡标题/元数据”字体比例系统。

### 7.3 色板与 ghost

- 内置 12 组稳定 container/on-container 配对。
- SCHOOL 颜色身份：`semesterId:sourceCourseKey`。
- MANUAL 颜色身份：`manual:manualItemId`。
- 同一业务课程跨本地 UUID 重建保持颜色。
- 当前周 active block alpha = `1.0`。
- 设置打开后，非当前周 ghost alpha = `0.35`。
- ghost 与 active block 一起参与 overlap layout。
- 设置关闭时，非当前周 block 在进入 place 前已过滤，不占 overlap 列。
- 色板有测试要求所有文字/背景配对达到既定可读对比度。

### 7.4 无障碍

每个 block 的 content description 始终包含：

```text
课程名，星期 + 时间，周次，地点（若有），教师（若有）
```

即使教师或时间因为窄宽在视觉上隐藏，无障碍文案仍保留完整信息。

## 8. 长按新增与 Y 轴耦合

当前 `LongPressResolver` 直接接收线性 `TimelineAxis`：

1. X fraction finite 校验并 clamp 到 `[0, 1)`。
2. 映射到 weekday 1..7。
3. Y fraction clamp 到 `[0, 1]`。
4. `axis.timeAt(y)` 得到近似真实时间。
5. 向下吸附到 5 分钟。
6. 再 clamp 回 day window。

这意味着：如果未来采用“休息时间不占视觉高度”的分段压缩轴，不能只改绘图；长按反解也必须使用同一个可逆坐标模型，否则用户点下午区域会得到错误的实际时间。

## 9. 学期切换当前设计

### 9.1 入口

- 当前入口是主课表左上学期名。
- 点击打开 `SemesterSwitcherSheet`。

### 9.2 Sheet 内容

- 学期按 `startDate` 倒序。
- 每行显示学期 display name。
- 使用可读标签独立表达：
  - 当前学期（academic-current）
  - 学校课表 / 本地课表（portalLinked）
  - 正在查看（viewed）
- 行高由内容和上下 `10dp` padding 构成。
- 标签使用 `secondaryContainer`，小圆角，横向 `6dp`、纵向 `2dp` 内边距。

### 9.3 状态语义

- 选择学期只写 `SettingsStore.viewedSemesterId`。
- 不修改 `isCurrentAcademicSemester`。
- 不触发同步。
- 不修改数据库学期行。
- 切换后查看周重置为该学期默认周。
- viewed semester 跨 App 重启持久化。

## 10. Settings 当前设计

### 10.1 页面结构

当前为全屏 `LazyColumn`：

- 页面水平 padding `16dp`。
- 顶部：文本字符 `‹` + “设置”。
- 返回字符使用 `headlineMedium`，右侧 `16dp` padding。
- 标题使用 `titleLarge`。
- 下面四个分组：课表、同步、学校账户、关于。

### 10.2 分组卡

- 组名：`titleSmall`、primary 色，左 `4dp`、底 `6dp`。
- 每组底部间隔 `12dp`。
- 内容使用 Material 3 `Card` 默认 shape/color/elevation 行为。
- Card 内部水平 padding `14dp`。
- 行间使用 `outlineVariant` 分隔线。
- 普通行最小高度 `56dp`，上下 padding `12dp`。
- Toggle 行最小高度 `56dp`。
- 行标题没有显式 typography。
- 行尾 value 没有显式 typography，只使用 `onSurfaceVariant`。
- 当前没有 leading icons、行级 supporting text 布局或统一 trailing chevron。

### 10.3 现有分组和动作

```text
课表
  学校作息时间
  显示非当前周课程
  应用为当前学期作息（条件显示）
  恢复学校默认

同步
  上次同步时间
  每周静默同步
  立即同步
  通知权限提示（条件显示）

学校账户
  登录状态
  重新登录
  清除登录状态

关于
  数据与版本
  App 版本
```

当前设置页已包含“立即同步”，所以把首页刷新移入设置时，不需要新增第二个同步 authority；需要决定的是是否删除首页入口以及如何呈现同步进行中/结果反馈。

当前设置页**不包含学期选择**。把学期切换移入设置将涉及 Settings 的 state/callback 结构，但必须继续调用现有 viewed-semester authority，不能创造第二套学期状态。

### 10.4 与 Google 原生参考的可见差异

从本轮提供的 Google Play 账户/设置参考图可观察到：

- 参考图使用更明确的页面背景与白色 section surface 分层。
- section 使用更大的统一外圆角，但内部行通过细分隔线连续成组。
- leading icon、标题、supporting text 和 trailing control 有稳定列对齐。
- 组间留白明显，但行内纵向密度和文字层级一致。
- 标题、正文、supporting text 的字号/颜色层级更明确。

当前 App 的主要差异：

- 淡紫默认 Card 填充面积较大，页面与卡片层次偏重。
- 行标题和值常在同一基线争抢宽度，长 profile 名和登录时间容易拥挤。
- 无 leading icon 列，也无 supporting text 的统一换行区域。
- 普通行、危险动作、只读信息、导航动作视觉差异不足。
- 返回、首页刷新、设置、周概览均使用文本/Unicode 字符，粗细和 Android 系统图标语言不一致。

## 11. Material 主题与图标现状

### 11.1 主题

- `MainActivity` 直接使用无参数 `MaterialTheme { ... }`。
- 当前没有 App 自有 `ColorScheme`、`Typography`、`Shapes` 或 spacing tokens 文件。
- 当前没有 dark/light mode 设置。
- 当前没有 dynamic color 策略。
- 当前没有透明背景、壁纸 layer、scrim 或内容对比保护模型。
- 课程色板是独立硬编码 12 色，不随 Material light/dark scheme 切换。

### 11.2 图标

当前主界面可见操作以文字字符模拟图标：

- 刷新：`↻`
- 设置：`⚙`
- 上一周/下一周：`‹` / `›`
- 周概览：`▦`
- Settings 返回：`‹`

项目当前没有声明 Compose Material Icons dependency。若改成标准矢量图标，需要明确采用的 icon authority 和依赖边界；不能继续依赖字体对 Unicode 字形的不同渲染。

## 12. 当前自动化约束

与布局直接相关的既有测试包括：

### 12.1 网格

- 七列全部存在，周末为空也保留。
- 七列占满 gutter 后全部宽度。
- header 与 grid 列对齐。
- time gutter 标签来自 profile，axis 边界去重。
- SCHOOL/MANUAL 点击 identity 正确。
- 空白长按和 block 长按事件边界。
- overlap 窄卡隐藏教师/时间。
- 宽高足够时显示教师/时间。
- 课程名多行后省略、地点独立一行。
- 无障碍仍含隐藏信息。
- ghost hidden/faded/current alpha。
- 色板稳定性、12 配对和对比度。
- teaching groups 由 profile break 推导。
- today header 语义存在。
- `nowLine` 只在提供时绘制。

### 12.2 主界面/周导航

- 学期、周数、日期范围存在。
- 网格存在。
- 上/下一周 callback。
- 首页刷新显示条件。
- 设置入口 callback。
- SCHOOL/MANUAL 点击路由。
- 无 Dashboard。
- WeekOverview 默认收起、可展开/收起、卡片数量、选中高亮、点击切周。
- WeekOverview active-only，不混入 ghost。
- Overview 展开状态不持久化，切学期收起。

### 12.3 Settings/学期

- 四个设置分组和必需动作存在。
- 禁止的周起始/隐藏周末/日周视图等设置不存在。
- denied notification hint。
- 返回/Profile Editor 导航。
- 四个 compact group card 存在。
- 学期标签、排序、viewed-only 写入和跨重建持久化。

后续布局重构必须更新行为已经改变的测试，同时保留数据、导航、无障碍和 identity 不变量；不能通过删除旧测试规避冲突。

## 13. 2026-09-03 新需求清单

以下是用户提出的新方向。除 13.1 外均仍需一次性确认细节。

### 13.1 已确认

**休息时段压缩：**

- 午休和晚间长间隔不再按真实分钟占用纵向高度。
- teaching groups 之间保留一条随主题颜色变化的间隔线。
- 不使用大片空白或灰色休息带。

仍需在最终规格中明确：分隔线自身占用的固定高度、短课间是否仍按真实分钟比例、分段轴对 MANUAL 任意分钟和 WeekOverview 的映射规则。

### 13.2 主课表头部候选变更

- 缩短状态栏下缘与第一行内容的视觉间距。
- 顶部第一信息位从 viewed semester 改为“第 N 周”。
- 本周日期范围放在周数右侧。
- 再向右依次放周缩略图和设置。
- 首页移除刷新。
- 首页移除学期选择。
- 刷新和学期选择进入 Settings。
- 星期与日期改成两行：第一行星期，第二行 `M-dd`。
- 通过重排释放更多课表主体高度。

### 13.3 图标候选变更

- 刷新改用标准矢量刷新图标。
- 设置改用常规 Material gear 图标。
- 周概览、周箭头、返回等是否一并迁移为同一图标体系待确认。

### 13.4 定位按钮候选变更

- 右下角增加浮动定位控件。
- 查看非本周/非本日时显示。
- 点击快速回到当前周。
- 当前处于本周时隐藏。

“非本日”目前没有对应状态：现有 UI 是整周视图，没有 selected day，也没有横向日分页。需要明确按钮判断只看 `viewedWeek != naturalWeek`，还是还要引入新的“当天定位/纵向当前时间”概念。

### 13.5 课程卡候选变更

- 缩小课程块字体，提高窄列信息密度。
- 保持课程名比地点/教师/时间更高优先级。
- 不扩大七列宽度，不破坏 overlap。

需要明确：标题与元数据各自字号/行高、最小可读字号、不同系统 font scale 下是否保持固定视觉密度，以及缩小后原有高度阈值是否重算。

### 13.6 当前时间红线候选变更

- 移除“有些周出现的多余红线”。

当前代码中该线就是自然周的 now-line。待确认是：

1. 完全删除 now-line；或
2. 只在今天列内画短线；或
3. 改为更弱的主题色 marker；或
4. 保留逻辑但修复被认为“多余”的具体场景。

### 13.7 Settings 候选变更

- 更接近 Google 原生应用的间距、字号、行布局和分组 surface。
- 为 navigation、toggle、只读信息、账户动作建立一致的行模板。
- 将学期选择加入 Settings。
- 首页刷新移入 Settings（当前已有“立即同步”）。
- 保留课表、同步、学校账户、关于的业务动作，是否重组分组待确认。

### 13.8 主题和透明壁纸候选准备

- 预留透明壁纸能力。
- 预留白昼/light UI 选项。

当前尚未定义：

- “透明壁纸”是 App 内选择图片、跟随系统壁纸，还是仅让内容 surface 半透明。
- 壁纸是否只作用于课表主体或包含 Settings/Sheet。
- 透明度、模糊、scrim、课程卡不透明度和可读性底线。
- light/dark/system 三态还是仅“白昼模式”二态。
- 是否使用系统 dynamic color。
- 新设置 key、默认值、迁移和进程重启持久化。

## 14. 规格冲突与影响面

### 14.1 线性真实时间轴 vs 压缩休息时段

这是本轮最大的架构冲突。

旧规格明确要求：

- Y 坐标按全天真实分钟线性映射。
- 午休/晚饭间隔真实体现。

新方向明确要求：

- 午休/晚间长间隔不占纵向空间，只保留主题分隔线。

二者不能同时成立。若批准新方向，必须显式修订原规格，并建立一个共同的可逆分段坐标 authority，至少同时服务：

- SCHOOL block placement。
- MANUAL 任意分钟 placement。
- 空白长按 Y → LocalTime。
- 时间 gutter。
- grid 横线和 teaching-group divider。
- now indicator（若保留）。
- WeekOverview 小地图。

只在 `WeeklyTimetableGrid` 隐藏灰带不会释放空间，也不能满足新需求。

### 14.2 顶部移除学期入口 vs viewed-semester authority

学期选择可以移动，但不能改变其数据语义：

- 唯一 persisted authority 仍是 `SettingsStore.viewedSemesterId`。
- 选择仍不得修改 academic-current 或触发同步。
- Settings 需要消费 semester list 和 viewed identity，或通过一个明确的共享 UI boundary 调用现有 `TimetableViewModel.onSemesterSelected`。
- 不应在 Settings 新建第二套 local selected-semester state。

### 14.3 首页移除刷新 vs 同步反馈

Settings 已有“立即同步”，因此入口迁移本身不需要新 engine。但需要确认：

- 同步中状态在 Settings 行内如何显示。
- 成功/失败 snackbar 由哪个页面承载。
- 用户同步后立即返回课表时，结果反馈是否仍可见。
- reauth dialog 是否允许跨 Settings/Timetable destination 显示。

### 14.4 压缩轴 vs 任意分钟手动项目

如果两个 teaching group 之间的真实时间整体折叠为一条线：

- 休息时段内创建/编辑的 MANUAL 项目如何显示必须明确。
- 休息时段内长按无法与某个唯一真实分钟一一对应。
- 现有 domain 允许 day window 内任意分钟，不只允许上课时段。

可选政策包括但不限于：

- 只压缩“没有任何 block 占用”的 gap，并动态保留含 MANUAL block 的 gap。
- 所有 gap 固定成很小的可映射 segment，而不是数学意义的 0 高度。
- 休息时段 MANUAL block 映射到相邻 divider 的展开区域。

该政策必须先确定，不能在实现时猜。

### 14.5 透明壁纸 vs 对比度

现有课程色板对固定 container/on-container 有对比度测试，但透明壁纸会改变最终合成背景。若支持透明：

- 不能只给 Surface 设置 alpha。
- 需要定义最低 scrim/tonal overlay。
- 需要对浅色、深色、高纹理壁纸分别验收。
- Settings 和正文必须维持可读对比，而不是只追求“透明”。

## 15. 下一轮一次性决策表

请在后续回复中一次性确定下表。可以直接复制“决策”列填写。

| 编号 | 决策主题 | 可选方向 | 当前状态 |
|---|---|---|---|
| D-01 | 休息段高度 | 固定极小 segment / 数学 0 高度 / 仅视觉去底色 | 已确认“不按真实时长”，细节待定 |
| D-02 | 休息段 MANUAL 项目 | 动态展开 / 极小 segment 映射 / 禁止落入折叠段 | 待定 |
| D-03 | 短课间 | 仍按真实分钟 / 同样压缩 / 只保留节次线 | 待定 |
| D-04 | 当前红线 | 删除 / 今天列短线 / 弱化 marker / 修场景 | 待定 |
| D-05 | 第一行结构 | 周数 + 日期 + 概览 + 设置 | 用户已提出，精确尺寸待定 |
| D-06 | 周箭头 | 保留第一行 / 移到边缘 / 仅手势+周选择 | 待定 |
| D-07 | 日期格式 | 星期一行 + `M-dd` 一行 | 用户已提出 |
| D-08 | 首页学期名 | 完全移除 / 次级文字 / 长按或菜单 | 倾向移入 Settings，待定 |
| D-09 | 首页刷新 | 完全移除，复用 Settings“立即同步” | 倾向已明确，反馈细节待定 |
| D-10 | 标准图标范围 | 只刷新/设置 / 顶部全部 / 全 App 导航 | 待定 |
| D-11 | 定位按钮显示 | 非自然周显示 / 非自然周或偏离今天显示 | 待定 |
| D-12 | 定位按钮动作 | 只回自然周 / 回自然周并定位当前时间 | 待定 |
| D-13 | 课程字体 | 固定更小 token / 按 card 尺寸分级 / 随窗口宽度 | 待定 |
| D-14 | WeekOverview | 仍展开为 104dp strip / 更紧凑 overlay/sheet | 待定 |
| D-15 | Settings 信息架构 | 保留四组 / 重组为显示、课表、同步、账户、关于 | 待定 |
| D-16 | Settings 行模板 | leading icon + title/supporting + trailing | 待定 |
| D-17 | 主题模式 | system/light/dark / 仅 light toggle | 待定 |
| D-18 | 透明壁纸来源 | 系统壁纸 / App 选图 / 仅半透明 surface | 待定 |
| D-19 | 壁纸作用范围 | 仅课表 / 全 App / 可配置 | 待定 |
| D-20 | 窗口验收 | 手机竖屏 + 横屏 + 自由小窗的断点与降级 | 待定 |

## 16. 后续设计完成条件

下一版正式设计规格至少应同时给出：

1. 主课表在手机竖屏的精确信息层级和尺寸 token。
2. compact/freeform window 下的降级顺序。
3. 新分段时间轴的正向与反向映射定义。
4. SCHOOL、MANUAL、long press、WeekOverview 对新轴的一致行为。
5. 当前时间/今天/返回本周三种概念的视觉与状态边界。
6. 顶部所有操作的标准矢量 icon、touch target 和 content description。
7. Settings 的 section、row、supporting text、危险动作和 trailing control 模板。
8. 课程卡标题与元数据 typography token，以及大字体策略。
9. light/dark/transparent wallpaper 的分层、对比度和 persistence contract。
10. 需要修改、保留或废止的旧测试与旧规格条目。

在这些项目得到一次性确认前，不应开始 production UI 修改。
