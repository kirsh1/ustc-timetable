# 子计划 03 — Portal + Sync（E1, E2, F1, F5, F2–F4, F6, H1, G1, G2, G3, H2, H3）

- 需求来源：[SPEC r3.1](../specs/2026-08-30-ustc-timetable-design.md)；路线图：[2026-08-30-ustc-timetable.md](2026-08-30-ustc-timetable.md)
- 前置：子计划 01、02 完成。
- 依赖顺序：**H1 完整交付后再做 G2**（SyncEngine 依赖指纹与 Differ）；G2 只依赖 parser/source **接口**（F1 定义），用 fake 注入完成全部 TDD，**不依赖真实 portal 证据**。gated 分支 F2→F3→F4→F6→G1 只产出注入实现，**不重写 SyncEngine**。
- 证据门：F2/F3/F4/F6 与 G1 真实接线在用户交付 SPEC §13 证据前**不得执行**。
- 路径约定：相对仓库根；命令在仓库根执行；测试过滤用完整 FQCN（可带包级后缀通配）；每个回归命令都是可直接执行的完整 `./gradlew` 命令。

---

## Task E1 — SessionStore（Keystore AES-GCM）+ SessionCookieHeader

- SPEC §7.2（URL-aware raw Cookie header 模型）。**不使用**已弃用的 androidx security-crypto。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/auth/SessionCookieHeader.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/auth/SessionBlob.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/auth/SecretKeyProvider.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/auth/AndroidKeystoreKeyProvider.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/auth/SessionStore.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/auth/SessionStoreTest.kt`。

接口与关键代码：
```kotlin
data class SessionCookieHeader(val requestUrl: String, val cookieHeader: String) {
    /** scope 键 = 标准化 scheme + host + effectivePort + path（默认端口归一，空 path 归一为 /，query/fragment 不参与） */
    data class Scope(val scheme: String, val host: String, val port: Int, val path: String) {
        companion object {
            fun of(raw: String): Scope {
                val uri = java.net.URI(raw)
                val scheme = uri.scheme.lowercase()
                val port = if (uri.port == -1) (if (scheme == "https") 443 else 80) else uri.port
                val path = if (uri.path.isNullOrEmpty()) "/" else uri.path
                return Scope(scheme, uri.host.lowercase(), port, path)
            }
        }
    }
    val scope: Scope = Scope.of(requestUrl)

    companion object {
        /** 只允许精确 scope 匹配；找不到返回 null——绝不借用兄弟 path / 其他 host / 其他 port 的 raw header
         *  （CookieManager.getCookie(url) 已按具体 URL 做 scope 过滤，二次放宽只会扩大作用域）。
         *  cookieHeader 原样返回：不重排、不拆解、不按 cookie-name 去重。
         *  同 scope 多条仅在 header 逐字节一致时可返回其一；冲突时 fail closed 返回 null。 */
        fun pickFor(requestUrl: String, headers: List<SessionCookieHeader>): SessionCookieHeader? {
            val matches = headers.filter { it.scope == Scope.of(requestUrl) }
            if (matches.isEmpty()) return null
            return matches.first().takeIf { first -> matches.all { it.cookieHeader == first.cookieHeader } }
        }
    }
}

data class SessionBlob(val headers: List<SessionCookieHeader>, val capturedAt: Instant)  // 绝不含用户名/密码

interface SecretKeyProvider { fun getOrCreateKey(): SecretKey }

class AndroidKeystoreKeyProvider : SecretKeyProvider {
    override fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("ustc_session_key", null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("ustc_session_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return kg.generateKey()
    }
}

@Serializable private data class SessionBlobDto(val headers: List<HeaderDto>, val capturedAtEpochMilli: Long)
@Serializable private data class HeaderDto(val requestUrl: String, val cookieHeader: String)

class SessionStore(
    private val keys: SecretKeyProvider,
    private val storage: SessionStorage,
    private val random: SecureRandom = SecureRandom(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun save(blob: SessionBlob) = withContext(ioDispatcher) {
        val iv = ByteArray(12); random.nextBytes(iv)
        val aad = "USTCSES1".toByteArray(Charsets.US_ASCII) + byteArrayOf(1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(Json.encodeToString(SessionBlobDto.from(blob)).toByteArray())
        storage.write(aad + iv + ct)
    }
    suspend fun load(): SessionBlob? = withContext(ioDispatcher) {
        val raw = storage.read() ?: return@withContext null
        val magic = "USTCSES1".toByteArray(Charsets.US_ASCII)
        if (raw.size < 8 + 1 + 12 + 16 ||
            !raw.copyOfRange(0, 8).contentEquals(magic) || raw[8] != 1.toByte()
        ) throw SessionStoreCorruptedException()
        val aad = raw.copyOfRange(0, 9)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys.getOrCreateKey(), GCMParameterSpec(128, raw.copyOfRange(9, 21)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(raw.copyOfRange(21, raw.size))
        Json.decodeFromString<SessionBlobDto>(plain.decodeToString()).toDomain()
    }
    suspend fun clear() = withContext(ioDispatcher) { storage.write(null) }
}
interface SessionStorage { fun read(): ByteArray?; fun write(data: ByteArray?) }  // 生产：AtomicFile(context.filesDir/session.bin)；测试：内存
```
- 步骤：
- [ ] 1. 写 failing test（`InMemorySessionStorage` + 测试用 `SecretKeySpec` provider；blob 样例含多条不同 scope 的 header，其中一条 cookieHeader 为 `"A=1; A=2; B=3"` 以覆盖重复 cookie-name）：
  - `roundtrip_preserves_headers_verbatim`（含重复 cookie-name 的 header 字符串逐字还原）；
  - `clear_removes_blob`（load 返回 null）；
  - `tampered_ciphertext_fails`（翻转 1 字节后 load 抛 Exception）；
  - `iv_is_random_per_save`（同一 blob 两次 save 后 storage 内容不同）；
  - `pickFor_exact_scope_match_wins`（scheme+host+effectivePort+path 全等命中）；
  - `same_host_sibling_path_not_reused`（`https://x/a` 的 header 不得用于 `https://x/b`）；
  - `same_path_different_query_can_reuse`（同 scope，仅 query 不同 → 命中）；
  - `different_port_not_reused`（`https://x:8443/a` ≠ `https://x/a`）；
  - `different_host_not_reused`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.SessionStoreTest"`（编译失败即 RED）。
- [ ] 3. 最小实现（如上；生产 `FileSessionStorage` 写 `context.filesDir/session.bin`）。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.SessionStoreTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.*"` → GREEN。（真机 Keystore 冒烟归子计划 04 J2。）
- [ ] 6. commit：`git add app/src && git commit -m "phaseE1: keystore AES-GCM session store over url-aware raw cookie headers"`。

---

## Task E2 — PortalDescriptor + WebView 登录外壳 + captureAndVerify 探测闭环

- SPEC §6.3、§7.1、§7.2。真实 URL 均由 `PortalDescriptor` 注入；**本任务不写任何真实 USTC URL**。
- 文件：`portal/PortalDescriptor.kt`、`portal/CookieAwareFetcher.kt`、`portal/PortalHttpClientFactory.kt`、`dto/UstcPortalPage.kt`、`auth/LoginPageDetector.kt`、`auth/UstcSessionManager.kt`、`auth/LoginCompletionCoordinator.kt`、`auth/WebViewLoginActivity.kt`、`sync/SyncError.kt`；对应 auth/portal tests 分职覆盖。
- OkHttp 客户端约定：`PortalHttpClientFactory` 以 `followRedirects(false)` + `followSslRedirects(false)` + `CookieJar.NO_COOKIES` 构造 portal 专用 client。E2 不接 `AppContainer`；等 concrete descriptor/detector 经证据 gate 确定后再组装。

接口与关键代码：
```kotlin
// portal/PortalDescriptor.kt —— G1 仅在证据门满足后，以证据支持的真实值实现 UstcPortalDescriptor
interface PortalDescriptor {
    val loginUrl: String          // SSO 登录页
    val probeUrl: String          // 低成本已登录可达页（默认=选课结果页 URL；SPEC §13-9）
    val selectionUrl: String
    val timetableUrl: String
    val sessionHosts: List<String> // 已登录门户域（用于判断 WebView 已离开登录域）
}

// dto/UstcPortalPage.kt —— ownership 前移至 E2，F1 不再重复创建
data class UstcPortalPage(val html: String, val finalUrl: String)

// auth/LoginPageDetector.kt —— 输入闭合：任何判定都基于一个真实响应的 (url, html)
interface LoginPageDetector { fun isLoginPage(url: String, html: String): Boolean }

// portal/CookieAwareFetcher.kt —— 手动重定向：逐跳按目标 URL 重新 pickFor；手工 raw header 绝不跨 origin 泄漏
class CookieAwareFetcher(private val http: OkHttpClient, private val maxRedirects: Int = 5) {
    suspend fun fetch(url: String, headers: List<SessionCookieHeader>): UstcPortalPage {
        var current = url
        var redirectCount = 0
        while (true) {
            val h = SessionCookieHeader.pickFor(current, headers)   // 每跳重新 pick；新 scope 无 header 则该跳不带 Cookie
            val request = Request.Builder().url(current).apply { if (h != null) header("Cookie", h.cookieHeader) }.build()
            val call = http.newCall(request)
            val response = call.awaitCancellable()                 // coroutine cancel → Call.cancel()
            response.use { resp ->
                if (resp.code in setOf(301, 302, 303, 307, 308)) {
                    if (redirectCount >= maxRedirects) throw SyncError.NetworkFailed.asFailure()
                    current = validateHttpRedirect(resp.request.url.resolve(requireNotNull(resp.header("Location"))))
                    redirectCount++                                // initial request + 最多 5 次被跟随 redirect
                    continue
                }
                if (!resp.isSuccessful) throw SyncError.NetworkFailed.asFailure()
                return UstcPortalPage(html = resp.body.string(), finalUrl = current)
            }
        }
    }
}

// auth/UstcSessionManager.kt
interface CookieRetriever { fun cookieHeaderFor(url: String): String? }  // 生产：CookieManager.getInstance().getCookie(url)

class UstcSessionManager(
    private val descriptor: PortalDescriptor,
    private val store: SessionStore,
    private val cookies: CookieRetriever,
    private val fetcher: CookieAwareFetcher,
    private val detector: LoginPageDetector,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun hasSession(): Boolean = store.load() != null

    /** 探测闭环：对三个目标 URL 分别收集 raw header（去重）→ fetcher 按 scope 选头抓 probeUrl → detector((finalUrl, html)) → 保存或抛失效 */
    suspend fun captureAndVerify(): SessionBlob {
        val targets = listOf(descriptor.probeUrl, descriptor.selectionUrl, descriptor.timetableUrl).distinct()
        val headers = targets.mapNotNull { url ->
            cookies.cookieHeaderFor(url)?.takeUnless(String::isBlank)?.let { SessionCookieHeader(url, it) }
        }.distinct()
        if (SessionCookieHeader.pickFor(descriptor.probeUrl, headers) == null)
            throw SyncError.AuthenticationExpired.asFailure()      // 无/冲突 probe scope：零 HTTP、零写入
        val page = fetcher.fetch(descriptor.probeUrl, headers)
        if (page.html.isBlank()) throw SyncError.NetworkFailed.asFailure()
        if (detector.isLoginPage(page.finalUrl, page.html)) throw SyncError.AuthenticationExpired.asFailure()
        val blob = SessionBlob(headers, clock.instant())
        store.save(blob)                                           // 只在 probe + detector 通过后覆盖旧 session
        return blob
    }
    suspend fun clear() = store.clear()
}
```
- `WebViewLoginActivity` 行为（自动完成检测为最终主路径）：
  1. `application as? WebViewLoginDependenciesProvider` 未配置时 `RESULT_CANCELED` 并安全关闭；不伪造 placeholder descriptor/detector；
  2. 有配置时加载 `descriptor.loginUrl`；`LoginCompletionCoordinator` 对 exact session-host 的 `onPageFinished` 执行 single-flight 自动 probe，in-flight 重复事件忽略且不计失败；
  3. 成功 → `setResult(RESULT_OK)` + `finish()`；第 3 次真实自动失败后显示 fallback 按钮「我已完成登录」，手动 probe 不依赖当前 page host；
  4. 结果经 `ActivityResultContract` 返回调用方（G3/I2 使用）。
- 步骤：
- [ ] 1. 写 failing test（fake `PortalDescriptor`（`https://fixture.example/login` 等，probe/selection/timetable 跨两个 host 以覆盖多 host 路径）、fake `LoginPageDetector`、fake `CookieRetriever` 按返回 URL 派发不同 header、MockWebServer 作 probe 目标、followRedirects(false) 的 OkHttp）：
  - `capture_stores_raw_headers_for_all_three_targets`（headers.size 覆盖三个目标，requestUrl 一一对应）；
  - `capture_dedupes_identical_url_and_header_pairs`（两目标返回完全相同 header 且 URL 相同场景 → 去重后 1 条）；
  - `duplicate_cookie_names_preserved_verbatim`（`"A=1; A=2; B=3"` 原样保存，未被拆解/合并）；
  - `probe_uses_header_matching_probe_url`（MockWebServer 记录的 `Cookie:` 请求头 == probeUrl 对应的 raw header）；
  - `capture_when_probe_is_login_page_throws_AuthExpired`（detector=true → 抛异常且 store 为空）；
  - `capture_without_any_cookie_header_throws`（三个目标都取不到 header）；
  - `clear_wipes_store`；
  - fetcher（`CookieAwareFetcherTest`）：`redirect_repick_header_for_new_origin`（302 → 不同 origin 且该 origin 无 header → 第二跳请求不带 Cookie，返回最终页）、`redirect_same_scope_keeps_header`（302 同 scope → 第二跳带同一 header）、`manual_cookie_header_not_leaked_to_other_origin`（断言第二跳请求头不含第一跳 cookie）、`redirect_hop_limit_throws_NetworkFailed`（>5 跳）。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.UstcSessionManagerTest" --tests "com.ustc.timetable.school.ustc.portal.CookieAwareFetcherTest"`。
- [ ] 3. 最小实现（如上；`sealed class SyncError` 本任务在 `app/src/main/java/com/ustc/timetable/sync/SyncError.kt` 先落，G2 只增不挪。同文件一并定义异常包装与解包助手：
```kotlin
class SyncFailure(val error: SyncError, cause: Throwable? = null) : Exception("Sync failed: ${error::class.simpleName}", cause)
fun SyncError.asFailure(cause: Throwable? = null): SyncFailure = SyncFailure(this, cause)
fun Throwable.syncErrorOrNull(): SyncError? = (this as? SyncFailure)?.error
```）。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.UstcSessionManagerTest" --tests "com.ustc.timetable.school.ustc.portal.CookieAwareFetcherTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseE2: webview login shell with cookie-aware manual redirect fetcher and probe-based capture"`。

---

## Task F1 — DTO + parser 接口 + fixture 机制 + 证据请求发出

- SPEC §6.2、§6.3、§6.4、§13。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/dto/Dtos.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/parser/ParserInterfaces.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/portal/SchoolPortalSource.kt`、`app/src/test/java/com/ustc/timetable/school/ustc/parser/UstcFixtureLoader.kt`、`app/src/test/resources/fixtures/ustc/README.md`、`app/src/test/resources/fixtures/ustc/sample_minimal.html`（自制教学样例，仅验证机制）。
- DTO（完整）：
```kotlin
package com.ustc.timetable.school.ustc.dto

data class UstcCourseSummary(
    val courseCode: String, val name: String, val credits: Double?,
    val department: String?, val courseType: String?,
    val teacherSummary: String?, val weeksText: String?,
)
data class UstcTimetableEntry(
    val courseName: String, val courseCode: String?, val weekdayText: String,
    val periodText: String, val weekText: String, val locationText: String, val teacherText: String,
)
data class UstcSemesterMetaPartial(
    val displayName: String?, val academicYear: String?, val term: Term?,
    val week1Start: java.time.LocalDate?, val totalWeeks: Int?,
    val startDate: java.time.LocalDate?, val endDate: java.time.LocalDate?,
)
```
- `UstcPortalPage` 已由 E2 落在 `dto/UstcPortalPage.kt`；F1 仅新增其余 DTO，不重复声明该类型。
- `ParserInterfaces.kt` 与 `SchoolPortalSource.kt`（**接口**；G2/SyncEngine 只依赖这些 F1 边界，因此 evidence-free）：
```kotlin
package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry

interface CourseSelectionPageParser { fun parse(page: UstcPortalPage): List<UstcCourseSummary> }
interface TimetablePageParser { fun parse(page: UstcPortalPage): List<UstcTimetableEntry> }
data class SemesterMetaResult(val meta: UstcSemesterMetaPartial, val isConfident: Boolean)
interface SemesterMetaParser { fun parse(selection: UstcPortalPage, timetable: UstcPortalPage): SemesterMetaResult }
```
- `SchoolPortalSource.kt` 只暴露两个原始页面获取边界：
```kotlin
interface SchoolPortalSource {
    suspend fun fetchCourseSelectionPage(): UstcPortalPage
    suspend fun fetchTimetablePage(): UstcPortalPage
}
```
- `UstcFixtureLoader.kt`：
```kotlin
object UstcFixtureLoader {
    fun load(name: String): String {
        require(name.isNotBlank() && !name.contains("..") && !name.contains('/') && !name.contains('\\'))
        return checkNotNull(javaClass.getResourceAsStream("/fixtures/ustc/$name")) { "missing fixture $name" }
            .use { it.readBytes().decodeToString() }
    }
}
```
- fixture README 内容：来源要求（登录后保存的完整 HTML）、basename-only 读取、脱敏规则（姓名→`学生A`、教师→`教师A`、学号→`PB00000000`、其余个人字段→稳定 `X_*`）、URL 只遮盖 secret value 且保留结构证据、文件命名（`course_selection.html`、`timetable.html`、`login_page.html`、`auth_expired.html`）。四个真实 fixture 当前均未入库；`sample_minimal.html` 只验证 classpath 机制，不是 portal 证据。
- 步骤：
- [x] 1. 先写 `ParserContractsTest` 与 `UstcFixtureLoaderTest`，覆盖 raw/nullable DTO、fake interface、双页面 source、basename-only loader、缺失资源与样例脱敏。
- [x] 2. production 不动时运行两个测试类，观察 DTO/interfaces/loader 缺失导致的预期编译 RED；E2 `UstcPortalPage` 正常解析。
- [x] 3. 最小实现 Dtos、ParserInterfaces、SchoolPortalSource、test-only UstcFixtureLoader、README 与 mechanism-only `sample_minimal.html`。
- [x] 4. 两个 F1 测试类 targeted GREEN，并完成 `school.ustc.*` 与 full regression。
- [x] 5. **向用户发出 SPEC §13 证据请求**（9 项），并在本文件末尾「证据状态」记录 PENDING。
- [x] 6. commit：`git add app/src && git commit -m "phaseF1: ustc boundary contracts and sanitized fixture harness"`。

---

## Task F5 — UstcSnapshotNormalizer（不依赖真实 HTML，evidence-free）

- SPEC §6.3、§3.3、§8.3.1。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/parser/NormalizedSchoolSnapshot.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcSnapshotNormalizer.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/parser/UstcSnapshotNormalizerTest.kt`。

接口：
```kotlin
data class NormalizationIssue(val severity: Severity, val message: String) { enum class Severity { WARNING, HARD } }
data class NormalizedSchoolSnapshot(
    val courses: List<Course>,
    val meetings: List<CourseMeeting>,
    val issues: List<NormalizationIssue>,
)
class UstcSnapshotNormalizer {
    fun normalize(
        selection: List<UstcCourseSummary>,
        timetable: List<UstcTimetableEntry>,
        meta: UstcSemesterMetaPartial,
        semesterId: SemesterId,
    ): NormalizedSchoolSnapshot
}
```
关键规则实现要点：
- 教师-周次分段：只有 slash 分隔后的**每一段**都完整匹配批准的 `teacher + numeric/parity week` grammar 时才拆 assignment；每段周集合必须是 entry `baseWeeks` 的子集，否则 `ValidationFailed`。任一段不匹配即把整个 `teacherText` 当普通教师列表，禁止部分猜测。
- 普通教师列表接受 `/、,，;；`，trim、去 blank、去重、排序；不 lowercase、转写、拆姓或删除姓名内部空白。
- canonical merge 两阶段：先按 course/day/period/week/location 合并 teacher names，再按 course/day/period/location/identical teacher set union week patterns；不同教师 assignment 不得错误 union。
- `sourceCourseKey`：选课页有稳定编号列时用 `courseCode` 值；否则 `"name:" + name`（SPEC §8.3.1）。
- `weekdayText` 映射：`星期X/周X/一..日 → 1..7`；`periodText`：`"第3-5节"/"3-5节"/"3-5"` → `3..5`。
- 匹配 fail closed：entry 有 code 时只允许 exact trimmed code；没有 code 才允许 exact trimmed name fallback。unknown code、ambiguous name、duplicate code/source key、unmatched timetable course 均抛 `ValidationFailed`；缺 credits/courseType 只生成 deterministic WARNING。
- 每个 Course/Meeting 使用完整 SHA-256 派生的 deterministic unique temporary normalized ID，meeting 精确引用 normalized course ID；不使用 UUID/Clock/DB。G2 `FreshLocalIds.assign()` 在 Room persistence 前替换为真正 local UUID，临时 ID 不参与 source identity/fingerprint。
- location-by-week 在 F5 由多条 `UstcTimetableEntry` 表达；F3 证据到位前不猜测 `locationText` 内嵌 grammar。
- 步骤：
- [x] 1. 先写 40 个 DTO-only behavior tests，覆盖 matching、identity、strict grammar、teacher assignment、location split、canonical merge、warnings/errors 与 input permutation。
- [x] 2. production 不动时运行 targeted test，观察 F5 snapshot model/normalizer unresolved 的 genuine RED。
- [x] 3. 最小实现 snapshot model 与 evidence-free normalizer；无 HTML/DOM/network/DB/Clock/UUID 依赖。
- [x] 4. targeted `UstcSnapshotNormalizerTest` 40/40 GREEN。
- [x] 5. `school.ustc.*` 158/158、full 566/566、Debug/Release assemble GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseF5: deterministic ustc snapshot normalization"`。

---

## Task F2 — 选课结果页 Parser 实现类 UstcCourseSelectionPageParser【证据门】

- 前置：SPEC §13-1/2/4/5 已交付，fixture `app/src/test/resources/fixtures/ustc/course_selection.html` 已脱敏入库。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcCourseSelectionPageParser.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/parser/CourseSelectionPageParserTest.kt`。
- 步骤：
- [ ] 1. 通读 fixture，**从真实 HTML 确定表格结构/列头/分页形态**，把期望解析结果写成 failing test 的 expected 列表（逐条来自 fixture 内容，不由猜测产生）：`parses_all_visible_courses`（expected = fixture 中全部课程的 `UstcCourseSummary` 列表）、`handles_missing_department`（缺院系行 → department=null）、`malformed_html_throws_ParseFailed`（截断 HTML → 抛 ParseFailed）、`parses_js_embedded_data_if_present`（若 §13-4/5 证明数据在 XHR JSON/内嵌脚本则按真实结构解析，expected 同上）。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.CourseSelectionPageParserTest"`。
- [ ] 3. 最小实现：`class UstcCourseSelectionPageParser : CourseSelectionPageParser`，Jsoup 按 fixture 真实结构取数；选择器常量集中文件顶部，每条注释标明对应 fixture 文件与行号证据。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.CourseSelectionPageParserTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseF2: course selection parser against real sanitized fixture"`。

---

## Task F3 — 我的课表页 Parser 实现类 UstcTimetablePageParser【证据门】

- 前置：§13-1/2/6 已交付，fixture `app/src/test/resources/fixtures/ustc/timetable.html` 入库。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcTimetablePageParser.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/parser/TimetablePageParserTest.kt`。
- 步骤：
- [ ] 1. 通读 fixture 写 failing test：`parses_entries_with_weekday_period_week_location_teacher`（expected=fixture 全部条目）、`handles_multiple_rows_per_course`、`parses_week_switcher_if_present`（§13-6：周次控件参数 → 供 F4 使用的数据；控件不存在则断言其不存在）、`malformed_html_throws_ParseFailed`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.TimetablePageParserTest"`。
- [ ] 3. 最小实现：`class UstcTimetablePageParser : TimetablePageParser`，Jsoup 真实结构；选择器带 fixture 行号注释。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.TimetablePageParserTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseF3: timetable page parser against real sanitized fixture"`。

---

## Task F4 — SemesterMetaParser 实现类 UstcSemesterMetaParser【证据门】

- 前置：§13-2/6 已交付。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcSemesterMetaParser.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/parser/SemesterMetaParserTest.kt`。
- 置信规则：`displayName && week1Start && totalWeeks` 三者齐 → `isConfident=true`，否则 false（其余字段尽量提取）。
- 步骤：
- [ ] 1. 写 failing test：`confident_when_name_week1start_totalweeks_present`、`not_confident_triggers_confirm_sheet_flag`（缺 totalWeeks → false）、`parses_week1start_from_week_switcher_dates`（用 §13-6 证据中的真实日期推导）。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.SemesterMetaParserTest"`。
- [ ] 3. 最小实现：`class UstcSemesterMetaParser : SemesterMetaParser`，从两页真实结构提取元数据，特征带 fixture 行号注释。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.parser.SemesterMetaParserTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseF4: semester meta parser with confidence flag"`。

---

## Task F6 — HeuristicLoginPageDetector【证据门】

- 前置：§13-3/7 已交付，fixture `login_page.html`、`auth_expired.html` 入库。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/auth/HeuristicLoginPageDetector.kt`；测试 `app/src/test/java/com/ustc/timetable/school/ustc/auth/HeuristicLoginPageDetectorTest.kt`。
- 步骤：
- [ ] 1. 写 failing test：`real_login_page_detected`（fixture login_page.html + 其真实登录 URL → true）、`auth_expired_page_detected`（auth_expired.html → true）、`timetable_page_not_detected`（timetable.html + portal URL → false）、`course_selection_page_not_detected`。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.HeuristicLoginPageDetectorTest"`。
- [ ] 3. 最小实现：特征仅取自 fixture（URL host/path 特征 + HTML 表单/标题特征），每条特征注释 fixture 证据。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.HeuristicLoginPageDetectorTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.auth.*"` → GREEN。
- [ ] 6. commit：`git add app/src && git commit -m "phaseF6: login page detector from real fixtures"`。

---

## Task H1 — FingerprintedSchoolContent + 指纹 + SnapshotDiffer（多阶段最小代价配对）+ ChangeFormatter

- SPEC §8.3。**完整交付，位于 G2 之前**（SyncEngine 依赖本任务的指纹与 Differ）。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/domain/FingerprintedSchoolContent.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/SchoolSnapshotFingerprint.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/SnapshotDiffer.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/ScheduleChange.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/ChangeFormatter.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/domain/SchoolSnapshotFingerprintTest.kt`、`app/src/test/java/com/ustc/timetable/timetable/domain/SnapshotDifferTest.kt`、`app/src/test/java/com/ustc/timetable/timetable/domain/ChangeFormatterNotificationTest.kt`。

指纹内容与计算（关键代码）：
```kotlin
@Serializable
data class FingerprintedSemesterMeta(
    val displayName: String, val academicYear: String, val term: String,
    val week1StartEpochDay: Long, val totalWeeks: Int,
    val startDateEpochDay: Long, val endDateEpochDay: Long,
)
@Serializable
data class FingerprintedCourse(
    val sourceCourseKey: String, val courseCode: String, val name: String,
    val credits: Double?, val courseType: String?,
)
@Serializable
data class FingerprintedMeeting(
    val sourceCourseKey: String, val weekday: Int, val startPeriod: Int, val endPeriod: Int,
    val weekPatternMask: Long, val location: String, val teacherNames: List<String>,
)
@Serializable
data class FingerprintedSchoolContent(
    val semesterMeta: FingerprintedSemesterMeta,
    val courses: List<FingerprintedCourse>,      // compute 时按 sourceCourseKey 排序
    val meetings: List<FingerprintedMeeting>,    // compute 时按全字段排序
) {
    companion object {
        fun of(semester: Semester, courses: List<Course>, meetings: List<CourseMeeting>): FingerprintedSchoolContent
    }
}

object SchoolSnapshotFingerprint {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    fun compute(content: FingerprintedSchoolContent): String {
        val canonical = json.encodeToString(
            FingerprintedSchoolContent.serializer(),
            content.copy(
                courses = content.courses.sortedBy { it.sourceCourseKey },
                meetings = content.meetings.sortedWith(compareBy({ it.sourceCourseKey }, { it.weekday },
                    { it.startPeriod }, { it.endPeriod }, { it.weekPatternMask }, { it.location }, { it.teacherNames })),
            ),
        )
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
```

H1 保持纯 domain：factory 只接收 `Semester + List<Course> + List<CourseMeeting>`，不依赖 Room、DAO 或
`TimetableDatabase`。G2 负责从 Room 读取学校行并转换成上述 domain lists，再调用 H1 factory。

Diff 多阶段配对（关键代码；SPEC §8.3.3）：
```kotlin
class SnapshotDiffer {
    fun diff(old: FingerprintedSchoolContent, new: FingerprintedSchoolContent): List<ScheduleChange> {
        val changes = mutableListOf<ScheduleChange>()
        val oldByKey = old.courses.associateBy { it.sourceCourseKey }
        val newByKey = new.courses.associateBy { it.sourceCourseKey }
        for (key in (oldByKey.keys + newByKey.keys).toSortedSet()) {
            val o = oldByKey[key]; val n = newByKey[key]
            val courseName = (n ?: o)!!.name
            if (o == null) { changes += ScheduleChange.CourseAdded(courseName); continue }
            if (n == null) { changes += ScheduleChange.CourseRemoved(courseName); continue }
            diffMeetingsPerWeekday(o, n, old.meetings, new.meetings, courseName, changes)
        }
        return changes
    }

    private fun diffMeetingsPerWeekday(oldCourse: FingerprintedCourse, newCourse: FingerprintedCourse,
                                       oldAll: List<FingerprintedMeeting>, newAll: List<FingerprintedMeeting>,
                                       courseName: String, out: MutableList<ScheduleChange>) {
        for (weekday in 1..7) {
            val os = oldAll.filter { it.sourceCourseKey == oldCourse.sourceCourseKey && it.weekday == weekday }
                .sortedBy(::stableKey).toMutableList()
            val ns = newAll.filter { it.sourceCourseKey == newCourse.sourceCourseKey && it.weekday == weekday }
                .sortedBy(::stableKey).toMutableList()
            val pairs = mutableListOf<Pair<FM, FM>>()
            // Stage 1：恒等保持——完全相等直接配对，未变化 meeting 优先保住原身份
            for (o in os.toList()) {
                val n = ns.firstOrNull { it == o }
                if (n != null) { pairs += o to n; os.remove(o); ns.remove(n) }
            }
            // Stage 2：剩余项做带虚拟 unmatched 节点的最小代价指派（桶规模个位数，穷举可接受）；
            // CONFIDENCE_THRESHOLD=2（仅共享 ≥2 字段的候选可配对）+ UNMATCHED_PENALTY=3；
            // objective = Σ pairCost + UNMATCHED_PENALTY × 未匹配数，取全局最小，并列按字典序唯一解
            pairs += minCostMatching(os, ns)
            for ((o, n) in pairs) emitFieldDiffs(o, n, courseName, out)
            os.forEach { out += ScheduleChange.MeetingRemoved(courseName, it.toSummary()) }
            ns.forEach { out += ScheduleChange.MeetingAdded(courseName, it.toSummary()) }
        }
    }

    private fun minCostMatching(os: MutableList<FM>, ns: MutableList<FM>): List<Pair<FM, FM>> {
        // 目标函数（SPEC §8.3.3）：objective = Σ pairCost + UNMATCHED_PENALTY × 未匹配数，取全局最小。
        // CONFIDENCE_THRESHOLD = 2：仅 pairCost ≤ 2（共享 ≥2 字段）的候选允许配对；
        // UNMATCHED_PENALTY = 3：未匹配的 old/new 各计 3。因此空 pairing 不可能胜过任何允许配对（≤2 < 2×3），
        // 低置信候选永不配对：3 differences = cost 3，4 differences = cost 4；二者都 > 2，
        // 算法不为提高匹配数量强迫低置信 pair，宁可 Removed + Added。
        data class Candidate(val o: FM, val n: FM, val cost: Int)
        val candidates = mutableListOf<Candidate>()
        for (o in os) for (n in ns) {
            val cost = diffCount(o, n)   // 节次/周次/地点/教师 逐字段比较（1..4）
            if (cost <= CONFIDENCE_THRESHOLD) candidates += Candidate(o, n, cost)
        }
        // 穷举全部互不相交候选子集 S：objective(S) = Σ cost(S) + UNMATCHED_PENALTY × (|os|+|ns|-2|S|)；
        // 取最小；并列按配对序列 (stableKey(o), stableKey(n)) 字典序取唯一解
        return bestDisjointSubset(candidates.sortedWith(compareBy({ it.cost }, { stableKey(it.o) }, { stableKey(it.n) })), os, ns)
    }

    companion object { const val CONFIDENCE_THRESHOLD = 2; const val UNMATCHED_PENALTY = 3 }
}
```
（`stableKey(m) = (m.startPeriod, m.endPeriod, m.weekPatternMask, m.location, m.teacherNames)`；`diffCount` = 该配对将产生的 change 条数；实现说明：`bestDisjointSubset` 用递归穷举全部互不相交子集并按目标函数取最优，桶内元素个位数。）

- 步骤：
- [x] 1. 写 failing test：
  - 指纹：`fingerprint_order_independent`、`fingerprint_excludes_local_ids_and_audit_fields`（courseId/meetingId/本地 semesterId/importedAt/lastSyncedAt/isCurrentAcademicSemester/portalLinked/profileId 变化 → 指纹不变）、`fingerprint_sensitive_to_every_school_field`（逐学校字段扰动 → 变化）、`fingerprint_excludes_manual_items`。
  - 配对：`insert_earlier_meeting_does_not_shift_existing_pairing`（旧 [A, B]，新 [C(更早), A, B] → 恰一条 MeetingAdded(C)，A/B 零 change）；
    `remove_middle_meeting_does_not_shift_existing_pairing`（旧 [A, B, C]，新 [A, C] → 恰一条 MeetingRemoved(B)）；
    `time_change_with_other_unchanged_meetings_pairs_correctly`（三 meeting 中仅一个改节次 → 恰一条 TimeChanged，其余零 change）；
    `minimum_cost_does_not_choose_empty_matching_for_single_field_change`（单字段时间变化场景：objective 必选 pairCost=1 的配对，不得以 Removed+Added 收场）；
    `ambiguous_low_confidence_pair_prefers_remove_add`（仅 weekday 相同、节次/周次/地点/教师全不同 → pairCost=4 超 CONFIDENCE_THRESHOLD=2 → 不配对，MeetingRemoved + MeetingAdded，零伪造精确修改）；
    `diff_time_change_is_TimeChanged_not_remove_add`、`diff_exact_on_location_change`（TH-B301→TH-C204）、`diff_teacher_and_weekpattern_changes`、`diff_meeting_added_removed`、`diff_course_added_removed_by_sourceCourseKey`、`identical_snapshots_empty_diff`、`unpairable_leftovers_become_removed_and_added_not_forced_changes`（新旧完全无相似项 → Removed+Added，零伪造精确修改）。
  - 文案：`formatter_第10周教室格式`（单周 → "第10周教室：TH-B301 → TH-C204"；多周 → "第7–12周教室：…"）、`formatter_course_added_removed_lines`。
- [x] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprintTest" --tests "com.ustc.timetable.timetable.domain.SnapshotDifferTest" --tests "com.ustc.timetable.timetable.domain.ChangeFormatterNotificationTest"`。
- [x] 3. 最小实现（如上；`ScheduleChange` 类型清单与 SPEC §8.3.3 一字不差；`MeetingSummary` 保持 typed domain form：`weekday: Int`、`startPeriod: Int`、`endPeriod: Int`、`weeks: WeekPattern`、`location: String`、`teacherNames: List<String>`；本地化字符串只由 notification formatter 生成）。
- [x] 4. 运行确认 GREEN：上述三组 H1 测试 **67/67 GREEN**。
- [x] 5. 定向回归：domain **120/120**、school boundary **158/158**、full **633/633**，`assembleDebug` 与 `assembleRelease` 均 GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseH1: deterministic school fingerprint and snapshot diff"`。

---

## Task G1 — UstcHttpPortalSource【证据门分支末梢；只产出注入实现】

- SPEC §7.3、§7.4、§10。G1 完成后**只替换注入实现**（descriptor/parser/detector 的真实版本），SyncEngine 及下游不改写。
- 文件：`app/src/main/java/com/ustc/timetable/school/ustc/portal/UstcHttpPortalSource.kt`、`app/src/main/java/com/ustc/timetable/school/ustc/portal/UstcPortalDescriptor.kt`（真实 URL 常量唯一落点，**证据门后填写**）；测试 `app/src/test/java/com/ustc/timetable/school/ustc/portal/UstcHttpPortalSourceTest.kt`（MockWebServer3）。

接口与关键代码：
```kotlin
class UstcHttpPortalSource(
    private val descriptor: PortalDescriptor,
    private val session: SessionStore,
    private val detector: LoginPageDetector,
    private val fetcher: CookieAwareFetcher,   // E2 交付；http 客户端 followRedirects(false)，重定向手动逐跳重选头
) : SchoolPortalSource {
    override suspend fun fetchCourseSelectionPage(): UstcPortalPage = fetch(descriptor.selectionUrl)
    override suspend fun fetchTimetablePage(): UstcPortalPage = fetch(descriptor.timetableUrl)

    private suspend fun fetch(url: String): UstcPortalPage {
        val blob = session.load() ?: throw SyncError.AuthenticationExpired.asIllegalState()
        val page = try { fetcher.fetch(url, blob.headers) } catch (e: java.io.IOException) { throw SyncError.NetworkFailed.asIllegalState() }
        if (detector.isLoginPage(page.finalUrl, page.html)) throw SyncError.AuthenticationExpired.asIllegalState()
        return page
    }
}
```
- 步骤：
- [ ] 1. 写 failing test（MockWebServer + fake detector + 内存 SessionStore + E2 的 `CookieAwareFetcher`；预置两条不同 host 的 header 覆盖多 host）：`fetch_returns_page`、`picks_matching_header_per_target_url`（selection 与 timetable 各带各自的 raw header，MockWebServer 记录值逐一断言）、`missing_header_for_url_omits_cookie_header`、`redirect_then_detector_runs_on_final_url`（302 → 新 URL → detector 收到最终页的 (finalUrl, html)）、`login_page_response_throws_AuthExpired`（detector 命中 → AuthenticationExpired 且不重试）、`network_error_throws_NetworkFailed`（`server.shutdown()` 后请求）。
- [ ] 2. 运行并观察预期 RED：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.portal.UstcHttpPortalSourceTest"`。
- [ ] 3. 最小实现（如上；本任务受证据门约束，production `UstcPortalDescriptor` 只使用已交付证据支持的真实值，并加注释指向对应 fixture/证据。MockWebServer 所需的 fixture descriptor 只定义在 test source，不以测试域常量创建 production descriptor）。
- [ ] 4. 运行确认 GREEN：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.portal.UstcHttpPortalSourceTest"`。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.school.ustc.*" --tests "com.ustc.timetable.sync.*"` → GREEN（验证替换注入实现未破坏 SyncEngine）。
- [ ] 6. commit：`git add app/src && git commit -m "phaseG1: http portal source with per-url cookie header selection and auth detection"`。

---

## Task G2 — SyncEngine 管线 + 单事务写入（evidence-free，fake 注入）

- SPEC §8.1、§8.2、§8.3、§10。只依赖 F1 的 parser/source **接口**与 F5 normalizer、H1 指纹/Differ；portal 用 fake 实现。
- 文件：`app/src/main/java/com/ustc/timetable/sync/SyncResult.kt`、`app/src/main/java/com/ustc/timetable/sync/SyncEngine.kt`（`SyncError.kt` 已在 E2 落）、`app/src/main/java/com/ustc/timetable/sync/FreshLocalIds.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/ApplySchoolSnapshot.kt` 增补 `importNewSemesterWithSnapshot`；测试 `app/src/test/java/com/ustc/timetable/sync/SyncEngineTest.kt`、`FreshLocalIdsTest.kt` 与 `timetable/data/db/ImportSchoolSnapshotTest.kt`。

接口与关键代码：
```kotlin
sealed class SyncResult {
    data object NoChange : SyncResult
    data class Success(val changes: List<ScheduleChange>) : SyncResult   // changes 为空 = 指纹漂移无通知语义，静默
    data class Failed(val error: SyncError) : SyncResult
}

class SyncEngine(
    private val portal: SchoolPortalSource,                // 接口（F1 定义），测试注入 fake
    private val selectionParser: CourseSelectionPageParser, // 接口
    private val timetableParser: TimetablePageParser,       // 接口
    private val metaParser: SemesterMetaParser,             // 接口
    private val normalizer: UstcSnapshotNormalizer,
    private val differ: SnapshotDiffer,
    private val db: TimetableDatabase,
    private val clock: Clock,
) {
    private val syncMutex = Mutex()

    /** 目标学期恒为 academic-current && portalLinked（SPEC §8.1）；viewedSemesterId 不参与。
     *  gate 权威在本方法：纯手动学期返回 NoChange 且绝不调用 SchoolPortalSource；
     *  WeeklySyncWorker 只调 engine，不复制 gate 逻辑（见 H3 测试契约）。 */
    suspend fun syncCurrentAcademicSemester(): SyncResult = syncMutex.withLock {
        val target = db.semesterDao().academicCurrentPortalLinked()?.let { Mappers.toDomain(it) }
            ?: return SyncResult.NoChange                       // 纯手动学期：静默空转，零 portal 调用
        syncLocked(target)
    }

    private suspend fun syncLocked(target: Semester): SyncResult = try {
        val selectionPage = portal.fetchCourseSelectionPage()
        val timetablePage = portal.fetchTimetablePage()
        val selection = selectionParser.parse(selectionPage)
        val entries = timetableParser.parse(timetablePage)
        val metaResult = metaParser.parse(selectionPage, timetablePage)
        val normalized = normalizer.normalize(selection, entries, metaResult.meta, target.id)
        validateSnapshot(normalized, target)                    // empty/HARD/cross-snapshot guard
        val newContent = FingerprintedSchoolContent.of(target, normalized.courses, normalized.meetings)
        val newFp = SchoolSnapshotFingerprint.compute(newContent)
        if (target.sourceFingerprint == newFp) return SyncResult.NoChange
        // G2 owns Room -> domain extraction; H1 never receives a database handle.
        val (oldCourses, oldMeetings) = loadSchoolDomainListsFromDb(target.id)
        val changes = if (target.sourceFingerprint == null) emptyList()
            else differ.diff(FingerprintedSchoolContent.of(target, oldCourses, oldMeetings), newContent)
        val ided = FreshLocalIds.assign(normalized)              // 仅确定需要写入后生成 persistence IDs
        db.applySchoolSnapshot(target.id, ided.courses, ided.meetings, newFp, clock.instant())
        SyncResult.Success(changes)
    } catch (e: SyncFailure) {
        SyncResult.Failed(e.error)
    }
}
```
- `FreshLocalIds.assign(normalized)` 是 sync/storage concern，不属于 H1 domain：遍历 courses 生成新 `CourseId`，以旧 id→新 id 映射重写 meetings 的 `courseId`，并生成新 `MeetingId`；NoChange 路径不调用它。
- `SyncEngine` 是唯一 single-flight authority；mutex 覆盖 target lookup 与完整 pipeline。第二个调用可在第一个完成后重新查询 target 并执行，但不得并发读取旧 fingerprint/diff/replace。
- G2 destructive-write guard 拒绝空课程 snapshot，直到真实 portal evidence 提供明确的“合法零课程”语义；9 项证据仍 PENDING 时不得猜测。
- Room→domain 旧快照提取归 G2 所有，只读取目标 semester 的 SCHOOL rows；H1 始终只接收 `Semester + List<Course> + List<CourseMeeting>`。
- 导入事务（SPEC §8.2 顺序：boundProfile → semester → academic switch → 学校行 → meta；I2 使用）：
```kotlin
suspend fun TimetableDatabase.importNewSemesterWithSnapshot(
    boundProfile: ScheduleProfileEntity,      // 该学期私有 profile 克隆行
    semester: SemesterEntity,                 // semester.profileId 必须等于 boundProfile.id
    courses: List<Course>, meetings: List<CourseMeeting>,
    fingerprint: String, syncedAt: Instant,
) = withTransaction {
    scheduleProfileDao().insert(boundProfile)
    semesterDao().insert(semester)
    semesterDao().setExclusiveAcademicCurrent(semester.id)
    courseDao().insertCourses(courses.map { Mappers.toEntity(it, semester.id) })
    courseDao().insertMeetings(meetings.map { Mappers.toEntity(it) })
    semesterDao().updateSyncMeta(semester.id, fingerprint, syncedAt.toEpochMilli())
}
```
- `importNewSemesterWithSnapshot` 仅是后续 import flow 使用的 atomic transaction primitive；本任务不接 UI、登录、确认、viewed semester 或首次导入流程。
- 步骤：
- [x] 1. 写 failing test（fake `SchoolPortalSource` + fake 三 parser 接口 + 真 normalizer + in-memory Room；旧学期数据由 `applySchoolSnapshot` 预置）：
  - `success_replaces_school_rows`；
  - `parse_failure_keeps_old_data`（fake parser 抛 → Failed(ParseFailed)，Room 数据与指纹不变）；
  - `network_failure_keeps_old_data`；
  - `auth_expired_keeps_old_data`（fake detector/portal 命中 → Failed(AuthenticationExpired)，数据不变）；
  - `manual_items_survive_sync`；
  - `fingerprint_equal_no_writes_no_changes`（同内容重跑 → NoChange，`lastSyncedAt` 不变——NoChange 路径不触碰 DB）；
  - `fingerprint_drift_without_user_visible_changes_updates_fingerprint_silently`（仅 credits 变化 → Success(empty)，指纹已更新）；
  - `changed_snapshot_produces_exact_diff_list`（改一处教室 → changes 恰含一条 LocationChanged）；
  - `sync_target_is_academic_current_portal_linked_only`（库中含历史学期 → 只替换 academic-current；纯手动学期 → NoChange 静默，且 fake `SchoolPortalSource` 调用计数 == 0——gate 权威在 engine）；
  - `assign_keeps_referential_integrity`（FreshLocalIds 单测）；
  - `import_success_semester_points_to_private_profile_clone`（`importNewSemesterWithSnapshot` 后 semester.profileId == boundProfile.id，academic-current 已切换）；
  - `import_failure_leaves_no_orphan_profile`（meetings 引用不存在的 courseId 触发 FK 失败 → 回滚后 `schedule_profiles` 表无 boundProfile 行、semesters 表无新学期行）。
- [x] 2. 运行并观察预期 RED：G2 targeted compile 因 `SyncResult`、`SyncEngine`、`FreshLocalIds`、`importNewSemesterWithSnapshot` 缺失而失败。
- [x] 3. 最小实现（如上；两条事务函数按 SPEC §8.2 顺序）。
- [x] 4. 运行确认 GREEN：G2 targeted **24/24 GREEN**；sync package **21/21 GREEN**。
- [x] 5. 定向回归：domain **120/120**、school boundary **158/158**、DB **16/16**、full **657/657**，debug/release 均 GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseG2: atomic evidence-free school sync pipeline"`。

---

## Task G3 — 手动同步 UX + 失效弹窗 + 重登续跑

- SPEC §5.1、§5.5、§7.4。
- 文件：`app/src/main/java/com/ustc/timetable/sync/ManualSyncController.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/AuthExpiredDialog.kt`；修改 `app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt`；测试 `app/src/test/java/com/ustc/timetable/sync/ManualSyncFlowTest.kt` 与既有 `TimetableScreenTest.kt`。`TimetableViewModel` 不接 manual-sync runtime state。

接口：
```kotlin
sealed interface ManualSyncState {
    data object Idle : ManualSyncState
    data object Syncing : ManualSyncState
    data object AwaitingReauth : ManualSyncState
}

sealed interface ManualSyncEvent {
    data class Updated(val changeCount: Int) : ManualSyncEvent
    data class FailedOther(val error: SyncError) : ManualSyncEvent
}

fun interface ManualSyncRunner { suspend fun sync(): SyncResult }

class ManualSyncController(private val runner: ManualSyncRunner, private val scope: CoroutineScope) {
    val state: StateFlow<ManualSyncState>      // durable：Idle / Syncing / AwaitingReauth
    val events: SharedFlow<ManualSyncEvent>    // one-shot：Updated / FailedOther
    fun start()                    // 非 Idle 时 no-op；runner adapter 最终只调用 SyncEngine
    fun onReloginSuccess()         // AwaitingReauth 时清 pending → 恰好一次自动 retry
    fun onReloginCanceled()        // 保持 AwaitingReauth，dialog 继续显示
    fun onCancelAuthExpired()      // 回 Idle，不重试
}
```
- `SyncEngine.syncCurrentAcademicSemester()` 继续是同步 target 的唯一 authority；UI/controller 不传 viewed semester 或 semester id。
- manual path 不读取或写入 `SettingsStore.needReauth`；该持久化状态属于后续 H3 background sync。
- `TimetableRoute(manualSyncController = null)` 是当前 evidence-gated production 默认：真实 portal/parser/detector stack 未完成前 refresh 不可用；后续 composition 只注入 `ManualSyncRunner { syncEngine.syncCurrentAcademicSemester() }`，不重写 G3。
- controller 构造和 Route composition 均不自动 `start()`；网络只来自明确 refresh 点击，或一次新的 E2 `RESULT_OK` 所授权的 retry。
- 步骤：
- [x] 1. 写 failing test：result mapping/silence/safe failures、auth dialog、cancel/relogin/retry、double tap、refresh visibility/disabled、grid/loading 与 Settings 隔离，共 **24** 个 G3 tests。
- [x] 2. 运行并观察预期 RED：targeted compile 因 `ManualSyncController/State/Event/Runner`、`AuthExpiredDialog`、manual-sync Screen callbacks 与 login-result wiring 缺失而失败。
- [x] 3. 最小实现：`TimetableScreen` 接 `↻`（viewed current+portal + runtime dependency）；syncing 显示局部 progress 且 grid 保留；AuthExpiredDialog 固定文案与按钮；E2 contract false 保持 dialog，true 授权一次 retry。
- [x] 4. 运行确认 GREEN：G3 targeted **24/24 GREEN**。
- [x] 5. 定向回归：sync **45/45**、UI **110/110**、domain **120/120**、full **681/681**，debug/release 均 GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseG3: manual sync ux with reauth resume"`。

---

## Task H2 — SyncNotification + POST_NOTIFICATIONS 权限控制

- SPEC §8.3、§8.5。
- 文件：`app/src/main/java/com/ustc/timetable/notification/SyncNotification.kt`、`app/src/main/java/com/ustc/timetable/notification/NotificationPermissionController.kt`；测试 `app/src/test/java/com/ustc/timetable/notification/SyncNotificationTest.kt`（Robolectric）。

接口与关键代码：
```kotlin
object SyncNotification {
    const val CHANNEL_SYNC = "sync_updates"
    fun ensureChannel(context: Context)
    fun postChanges(context: Context, changes: List<ScheduleChange>) {
        if (changes.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return   // §8.5-4：未授权 no-op
        val lines = changes.flatMap { ChangeFormatter.notificationLines(it) }
        if (lines.isEmpty()) return
        NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification_timetable)
            .setContentTitle("课表已更新").setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true).build()
            .let { NotificationManagerCompat.from(context).notify(1001, it) }
    }
    fun postReauthNeeded(context: Context)   // 同样先查 areNotificationsEnabled；仅供 H3 后台认证失效路径消费
}
class NotificationPermissionController(private val settings: SettingsStore) {
    suspend fun shouldRequestNow(areNotificationsEnabled: Boolean): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            !areNotificationsEnabled &&
            !settings.notificationRequestShown.first()
    suspend fun markRequested() = settings.markNotificationRequestShown()
}
```
- `postChanges(emptyList())` 与 formatter 产出空行都是真正 no-op；禁用通知时两个发送 API 都不建 channel、不发送、不重试。
- H2 只提供 Android notification sink 与 permission policy/controller。`postReauthNeeded` 不写 `needReauth`；该状态及通知的组合属于 H3 后台 orchestration。
- `shouldRequestNow` 只判断，`markRequested` 只持久化“已发起过”。H2 不调用 `requestPermissions`、不持有 ActivityResult launcher；真正前台请求触发点留给 I3/H3 wiring，并在实际发起后无论 grant/deny 调用 `markRequested()`。
- 步骤：
- [x] 1. tests-first：Robolectric 锁定 empty/disabled no-op、channel、formatter 顺序、双 ID/替换；真实 `SettingsStore` 锁定 API 32/33/36 policy、controller recreation 与 weekly-sync 隔离。
- [x] 2. RED：production 未动时 `SyncNotification` / `NotificationPermissionController` unresolved，`exit 1`。
- [x] 3. 最小实现：notification sink + permission policy/controller；无 UI、Worker、sync execution 或 Settings mutation 越界。
- [x] 4. 定向 GREEN：notification **17/17**；H1 formatter **11/11**。
- [x] 5. 回归：sync **45/45**、domain **120/120**、full **698/698**；debug/release 与 merged manifests GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseH2: gated sync and reauth notifications"`。

---

## Task H3 — WeeklySyncWorker + SyncScheduler

- SPEC §8.1、§8.2、§8.5、§7.4。
- 文件：`app/src/main/java/com/ustc/timetable/sync/WeeklySyncWorker.kt`、`app/src/main/java/com/ustc/timetable/sync/SyncScheduler.kt`、`app/src/main/java/com/ustc/timetable/sync/WeeklySyncWorkerFactory.kt`；测试使用 Robolectric `TestListenableWorkerBuilder` 与官方 WorkManager test harness。

接口与关键代码：
```kotlin
class WeeklySyncWorker(
    context: Context, params: WorkerParameters,
    private val runner: BackgroundSyncRunner,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.weeklySyncEnabled.first()) return Result.success()
        return when (val r = runner.run()) {
            is SyncResult.NoChange -> Result.success()                       // 完全静默（含纯手动学期空转）
            is SyncResult.Success ->
                if (r.changes.isEmpty()) Result.success()
                else { SyncNotification.postChanges(applicationContext, r.changes); Result.success() }
            is SyncResult.Failed -> when (r.error) {
                SyncError.AuthenticationExpired -> {
                    settings.setNeedReauth(true)
                    SyncNotification.postReauthNeeded(applicationContext)    // 受 areNotificationsEnabled 约束
                    Result.success()
                }
                else -> Result.success()                                     // 网络/解析失败：静默保留，等下周期
            }
        }
    }
}
object SyncScheduler {
    const val UNIQUE_NAME = "ustc_weekly_sync"
    fun enqueue(context: Context) {
        val req = PeriodicWorkRequestBuilder<WeeklySyncWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
}
```
- `BackgroundSyncRunner` 是 Worker 的中性 orchestration seam；`SyncEngineBackgroundRunner` 只转发到 `SyncEngine.syncCurrentAcademicSemester()`。semester gate 权威仍在 engine（自读 academic-current && portalLinked），Worker 不接受 semester id、不读 viewed semester、不查询 Room。
- Worker 自己持有 `weeklySyncEnabled.first()` race gate；设置关闭后，即使旧 Worker 已开始，也在调用 runner 前静默成功。
- H3 只交付 evidence-free Worker/Scheduler/WorkerFactory seam。真实 `SyncEngine` 尚不能在 9 项 portal evidence PENDING 时诚实组装，因此本轮不修改 `TimetableApp`/`AppContainer`，不安装 production WorkerFactory，也不在启动时 enqueue。runtime composition 与 scheduler reconciliation 等真实 portal stack 可用后一次接线。
- 周期请求使用 7 天 interval、7 天 initial delay、CONNECTED constraint 与 unique KEEP；注册/重建调度不等于启动时立即联网。
- `lastSyncFinishedAt` 的产品语义（最后成功、最后完成 attempt、manual/background 是否共享）仍待 Settings integration 明确；H3 不写该字段。
- 步骤：
- [x] 1. tests-first：Worker result mapping/race gate、真实 G2 manual-only NoChange、permission no-op、Scheduler interval/delay/constraint/KEEP/cancel/setEnabled、Factory create/null 共 **21** 个用例。
- [x] 2. RED：production 未动时 H3 Worker/runner/Scheduler/Factory symbols unresolved；`setEnabled` 另行完成一次 focused RED→GREEN。
- [x] 3. 最小实现仅新增 Worker/Scheduler/Factory；无 App runtime、fake portal、Settings/UI/Room 越界。
- [x] 4. targeted **21/21 GREEN**。
- [x] 5. 定向回归：notification **17/17**、sync **66/66**、domain **120/120**、full **719/719**；debug/release GREEN。
- [x] 6. commit：`git add app/src && git commit -m "phaseH3: weekly silent sync worker and scheduler core"`。

---

## 证据状态（F1 后维护）

E2 测试中的 `fixture.example` URL、合成 HTML 和 fake detector 只证明通用登录/抓取机制，**不计为以下真实 portal 证据**。真实 fixture 必须按 `app/src/test/resources/fixtures/ustc/README.md` 脱敏并人工检查 staged diff。

| 证据项（SPEC §13） | 状态 |
|---|---|
| 1 选课结果页与课表页登录后的完整最终 URL（含 scheme/host/port/path/query names 与非 secret functional values） | **PENDING** |
| 2 两页完整、已脱敏 HTML：`course_selection.html` 与 `timetable.html` | **PENDING** |
| 3 登录方式、登录页、SSO/重定向链特征与成功后跳转特征 | **PENDING** |
| 4 两页是否由 XHR/fetch 填充；如有，提供已脱敏响应样例及请求触发关系 | **PENDING** |
| 5 浏览器“查看网页源代码”（Ctrl+U）是否已经包含课程/课表数据 | **PENDING** |
| 6 周次切换控件行为：是否请求新页面/参数、仅前端切换，及其真实字段/URL 变化 | **PENDING** |
| 7 登录页与会话失效页的完整、已脱敏 HTML：`login_page.html`、`auth_expired.html` | **PENDING** |
| 8 会话实际有效期、跨端/重复登录是否互踢，以及失效表现 | **PENDING** |
| 9 建议的低成本 `probeUrl`（登录后可达、稳定、响应小） | **PENDING** |

## 本子计划完成判定

- 证据未交付时：E/F1/F5/H1/G2(fake)/G3/H2/H3 全绿——同步核心 evidence-free。
- 证据交付后：gated 分支 F2→F3→F4→F6→G1 产出注入实现；G1 合入后只替换注入实现并重跑 G1 步骤 5 的定向回归。
- `./gradlew :app:testDebugUnitTest` 全绿；`./gradlew :app:lintDebug` 无 error。
