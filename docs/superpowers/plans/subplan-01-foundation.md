# 子计划 01 — Foundation（A0–A6, B1）

- 需求来源：[SPEC r3.1](../specs/2026-08-30-ustc-timetable-design.md)；路线图：[2026-08-30-ustc-timetable.md](2026-08-30-ustc-timetable.md)
- 交付物：可构建的 Android 工程、完整 domain 层、Room 持久层（含 `applySchoolSnapshot` 单事务）、作息 profile（含克隆绑定）、布局纯函数。全部逻辑 TDD。
- 路径约定：相对仓库根；命令在仓库根执行；测试过滤用完整 FQCN。

---

## Task A0 — git init + Gradle 脚手架（固定版本组合）

目标：空 workspace 变为可 `assembleDebug` 的工程；测试基建议器可用；docs 两份文档入库。

文件（全部为新建）：
- `.gitignore`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `local.properties`（不入库）
- `gradle/wrapper/gradle-wrapper.properties`（`distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip`）及 wrapper jar（`gradle wrapper --gradle-version 9.5.0` 生成）
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ustc/timetable/MainActivity.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ustc/timetable/SanityTest.kt`

**Built-in Kotlin 约束（AGP 9.3 官方迁移规范）**：不应用 `org.jetbrains.kotlin.android`（catalog / 根 alias / app application 三处均不得出现）；不设置 `android.builtInKotlin=false` 或 `android.newDsl=false`；Compose 编译器插件与 serialization 插件仍按原 plugin ID 显式应用（版本随 Kotlin 2.4.10）；KSP ≥ 2.3.1 支持 built-in Kotlin；`kotlin { compilerOptions {} }` 为官方 DSL，`jvmTarget` 默认取 `android.compileOptions.targetCompatibility`，无需显式设置。

步骤：
- [ ] 1. 建立 git 基线：`git init` → `git status --short`（预期 untracked 只有 `docs/`，若出现其他内容先停下核对）→ `git add docs && git commit -m "phaseA0: approved spec r3.1 and roadmap docs"` → 再次 `git status --short`（预期输出为空 = clean）。
- [ ] 2. 写入以下构建文件（内容为最终形态，版本一律来自 catalog）：

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ustc-timetable"
include(":app")
```

`build.gradle.kts`（根；无 kotlin-android alias——built-in Kotlin 由 `com.android.application` 自带）
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle/libs.versions.toml`
```toml
[versions]
agp = "9.3.0"
kotlin = "2.4.10"
ksp = "2.3.11"
composeBom = "2026.06.00"
activityCompose = "1.13.0"
coreKtx = "1.19.0"
lifecycle = "2.11.0"
navigationCompose = "2.10.0"
room = "2.8.4"
work = "2.11.2"
datastore = "1.2.1"
okhttp = "5.5.0"
jsoup = "1.23.2"
kotlinxSerialization = "1.11.0"
coroutines = "1.11.0"
junit = "4.13.2"
robolectric = "4.16.1"
turbine = "1.2.1"
androidxTestJunit = "1.3.0"
androidxTestRunner = "1.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver3 = { group = "com.squareup.okhttp3", name = "mockwebserver3", version.ref = "okhttp" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-test-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestJunit" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```
（catalog 中**不得**出现 `kotlin-android`；`gradle.properties` 中**不得**出现 `android.builtInKotlin` / `android.newDsl`。）

`gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

`app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)      // AGP 9.3：Built-in Kotlin 默认启用，禁止再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)           // Compose 编译器插件：built-in Kotlin 下仍需显式应用，版本随 Kotlin
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)                      // KSP ≥ 2.3.1 支持 AGP 9 built-in Kotlin
}

android {
    namespace = "com.ustc.timetable"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.ustc.timetable"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

// Built-in Kotlin：kotlin.compilerOptions 为官方 DSL；jvmTarget 默认取
// android.compileOptions.targetCompatibility（=17），官方明确无需再显式设置，故此处不写 kotlin{} 块。

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(platform(libs.compose.bom))       // B2 的 Compose tests 位于 app/src/test，由 Robolectric 执行
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver3)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

`app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.UstcTimetable"
        android:allowBackup="false">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
（A6 创建 `TimetableApp` 时在本文件 `<application>` 增加 `android:name=".TimetableApp"`。）

`app/src/main/res/values/themes.xml`
```xml
<resources>
    <style name="Theme.UstcTimetable" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values/strings.xml`
```xml
<resources><string name="app_name">课表</string></resources>
```

`app/src/main/java/com/ustc/timetable/MainActivity.kt`
```kotlin
package com.ustc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text(text = "USTC Timetable") }
    }
}
```

`local.properties`（不入库）：`sdk.dir=C\:\\Users\\Forstargazing\\AppData\\Local\\Android\\Sdk`

- [ ] 3. 写 failing test `app/src/test/java/com/ustc/timetable/SanityTest.kt`（含 serialization 编译器插件冒烟，使 A0 验证覆盖真实编译而非仅依赖解析）：
```kotlin
package com.ustc.timetable

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@Serializable
data class SerializationProbeDto(val name: String, val value: Int)

class SanityTest {
    @Test fun testRunnerWired() { assertEquals(4, 2 + 2) }

    @Test fun serializationPluginWired() {
        val encoded = Json.encodeToString(SerializationProbeDto("probe", 1))
        assertEquals(SerializationProbeDto("probe", 1), Json.decodeFromString<SerializationProbeDto>(encoded))
    }
}
```
- [ ] 4. 生成 wrapper 并观察 RED→构建：本机无全局 Gradle，用确定的 `%TEMP%` bootstrap 目录获取一次性 Gradle 9.5.0（三条命令均按原样可执行）：
  `powershell -Command "Invoke-WebRequest -Uri https://services.gradle.org/distributions/gradle-9.5.0-bin.zip -OutFile $env:TEMP\gradle-9.5.0-bin.zip"`
  `powershell -Command "Expand-Archive -Path $env:TEMP\gradle-9.5.0-bin.zip -DestinationPath $env:TEMP\gradle-bootstrap -Force"`
  在仓库根执行：`cmd /c "%TEMP%\gradle-bootstrap\gradle-9.5.0\bin\gradle.bat wrapper --gradle-version 9.5.0"`；此后一律使用 `./gradlew`。随后运行：
  `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.SanityTest"` → GREEN。
  若依赖解析失败：按 roadmap A0 回退规则调整并在 commit 注明。
- [ ] 5. 全量构建验证（必须证明**真实编译**，不是仅依赖解析）：
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL，且 `ls app/build/outputs/apk/debug/app-debug.apk` 确认 APK 产物存在；
  `./gradlew :app:compileDebugKotlin` → built-in Kotlin 编译任务真实执行；
  版本钉死核查：`./gradlew :app:dependencies --configuration debugRuntimeClasspath` 实测解析 `androidx.compose.ui:ui:1.11.3`（BOM 2026.06.00），且不含任何 compose 1.12.x；
  `serializationPluginWired` 通过 = serialization 编译器插件生效；`MainActivity` 中 `setContent { Text(text = "USTC Timetable") }` 编译通过 = Compose 编译器插件生效；KSP 的真实验证在 A5 的 RED→GREEN（Room 注解处理）完成。任何一步失败按 roadmap A0 回退规则处理并记录（Compose 解析失败先报告，不得擅自换 Robolectric beta / Compose 1.12）。
- [ ] 6. commit：`git add -A && git commit -m "phaseA0: android scaffold on AGP 9.3.0 / Gradle 9.5.0 / Kotlin 2.4.10, builds green"`。

---

## Task A1 — WeekPattern

- SPEC §3.1。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/domain/WeekPattern.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/domain/WeekPatternTest.kt`。

步骤：
- [ ] 1. 写 failing test（用例与断言）：
```kotlin
package com.ustc.timetable.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPatternTest {
    @Test fun contains_outOfRange_false() {
        val p = WeekPattern.range(1, 20)
        assertFalse(p.contains(0)); assertFalse(p.contains(21)); assertFalse(WeekPattern.EMPTY.contains(1))
    }
    @Test fun parse_1_20() { assertEquals(WeekPattern.range(1, 20), WeekPattern.parse("1-20周")) }
    @Test fun parse_2_6() { assertEquals(WeekPattern.range(2, 6), WeekPattern.parse("2-6")) }
    @Test fun parse_2_4_6_8() { assertEquals(WeekPattern.of(2, 4, 6, 8), WeekPattern.parse("2,4,6,8")) }
    @Test fun parse_2_6_8_10_12() { assertEquals(WeekPattern.range(2, 6).union(WeekPattern.of(8)).union(WeekPattern.range(10, 12)), WeekPattern.parse("2-6,8,10-12")) }
    @Test fun parse_中文逗号与装饰字符() { assertEquals(WeekPattern.range(3, 5), WeekPattern.parse("第3–5周，5周")) }
    @Test fun parse_invertedRange_throws() { assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("5-3") } }
    @Test fun parse_empty_throws() { assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("第周") } }
    @Test fun parse_weekOver63_throws() { assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("64") } }
    @Test fun format_canonicalRanges() {
        assertEquals("2-6,8,10-12", WeekPattern.parse("2-6,8,10-12").format())
        assertEquals("1", WeekPattern.of(1).format())
        assertEquals("1-20", WeekPattern.range(1, 20).format())
    }
    @Test fun roundtrip_parse_format() {
        val raw = "2-6,8,10-12"; assertEquals(raw, WeekPattern.parse(raw).format())
    }
    @Test fun oddWithin_单周语义() { assertEquals(WeekPattern.of(1, 3, 5, 7), WeekPattern.oddWithin(1, 7)) }
    @Test fun evenWithin_双周语义() { assertEquals(WeekPattern.of(2, 4, 6), WeekPattern.evenWithin(1, 6)) }
    @Test fun parse_zero_throws() { assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("0") } }
    @Test fun parse_unknown_text_throws() { assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("2foo") } }
    @Test fun of_duplicate_weeks_naturally_dedupe() { assertEquals(WeekPattern.of(2, 3), WeekPattern.of(2, 2, 3)) }
}
```
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.WeekPatternTest"` → 预期 RED（`WeekPattern` 未定义，编译失败）。
- [ ] 3. 最小实现 `WeekPattern.kt`（关键代码，完整落盘）：
```kotlin
package com.ustc.timetable.timetable.domain

@JvmInline
value class WeekPattern(val mask: Long) {
    operator fun contains(week: Int): Boolean = week in 1..63 && (mask shr (week - 1) and 1L) == 1L

    fun union(other: WeekPattern): WeekPattern = WeekPattern(mask or other.mask)

    fun format(): String {
        if (mask == 0L) return ""
        val parts = mutableListOf<String>()
        var w = 1
        while (w <= 63) {
            if (contains(w)) {
                var end = w
                while (end + 1 <= 63 && contains(end + 1)) end++
                parts += if (end == w) "$w" else "$w-$end"
                w = end + 1
            } else w++
        }
        return parts.joinToString(",")
    }

    companion object {
        val EMPTY = WeekPattern(0L)
        fun of(vararg weeks: Int): WeekPattern {
            val p = weeks.fold(EMPTY) { acc, w -> acc + w }
            require(p.mask != 0L) { "empty week set" }
            return p
        }
        private operator fun WeekPattern.plus(week: Int): WeekPattern {
            require(week in 1..63) { "week out of range: $week" }
            return WeekPattern(mask or (1L shl (week - 1)))
        }
        fun range(start: Int, endInclusive: Int): WeekPattern {
            require(start in 1..63 && endInclusive in start..63) { "bad range $start-$endInclusive" }
            var m = 0L
            for (w in start..endInclusive) m = m or (1L shl (w - 1))
            return WeekPattern(m)
        }
        fun oddWithin(start: Int, endInclusive: Int): WeekPattern = parityWithin(start, endInclusive, odd = true)
        fun evenWithin(start: Int, endInclusive: Int): WeekPattern = parityWithin(start, endInclusive, odd = false)
        private fun parityWithin(start: Int, endInclusive: Int, odd: Boolean): WeekPattern {
            require(start in 1..63 && endInclusive in start..63) { "bad range $start-$endInclusive" }
            var m = 0L
            for (w in start..endInclusive) if ((w % 2 == 1) == odd) m = m or (1L shl (w - 1))
            require(m != 0L) { "parity selection produced empty set: $start-$endInclusive" }
            return WeekPattern(m)
        }

        fun parse(raw: String): WeekPattern {
            // 仅规范化明确允许的装饰；任何其他字符直接抛出（绝不静默过滤，防 "2foo"→"2"、"2a3"→"23"）
            val cleaned = buildString {
                for (ch in raw) when (ch) {
                    '，', '、' -> append(',')
                    '–', '—', '至', '到' -> append('-')
                    '第', '周' -> { /* 允许的装饰：丢弃 */ }
                    else -> {
                        if (!(ch.isDigit() || ch == ',' || ch == '-'))
                            throw IllegalArgumentException("unexpected character '$ch' in week pattern: $raw")
                        append(ch)
                    }
                }
            }
            require(GRAMMAR.matches(cleaned)) { "not a week pattern: $raw" }  // 整串 grammar：\d+(-\d+)?(,\d+(-\d+)?)*
            var m = 0L
            for (token in cleaned.split(',')) {
                require(token.isNotEmpty()) { "empty token in: $raw" }
                val seg = token.split('-')
                require(seg.size <= 2) { "bad token: $token" }
                val a = seg[0].toIntOrNull() ?: throw IllegalArgumentException("bad token: $token")
                val b = if (seg.size == 2) seg[1].toIntOrNull() ?: throw IllegalArgumentException("bad token: $token") else a
                require(b >= a) { "inverted range: $token" }
                require(b <= 63) { "week out of range: $b" }
                for (w in a..b) m = m or (1L shl (w - 1))
            }
            require(m != 0L) { "empty week pattern: $raw" }
            return WeekPattern(m)
        }
    }
}
```
（`of` 经由私有 `plus` 做范围校验；`EMPTY.format()` 返回空串仅供内部使用，域模型不持久化空集。）
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseA1: weekpattern bitmask value type with parse/format/contains"`。

---

## Task A2 — Semester / Term / WeekCalculator

- SPEC §3.2。落盘 `Semester`（含 `isCurrentAcademicSemester`、`portalLinked`、`profileId`、`sourceFingerprint`）、`Term`、`SemesterId`、`LocalDateRange`、`SemesterDefaults.AUTUMN_2026`、`WeekCalculator`。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/domain/Semester.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/WeekCalculator.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/domain/WeekCalculatorTest.kt`。

步骤：
- [ ] 1. 写 failing test：
```kotlin
package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WeekCalculatorTest {
    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "p1")

    @Test fun week1_on_2026_08_31() { assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2026, 8, 31), sem)) }
    @Test fun week2_span_2026_09_07_to_09_13() {
        assertEquals(2, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 7), sem))
        assertEquals(2, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 13), sem))
    }
    @Test fun sunday_2026_09_06_still_week1() { assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 6), sem)) }
    @Test fun before_week1_null() { assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2026, 8, 30), sem)) }
    @Test fun beyond_totalWeeks_null() { assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 1, 18), sem)) }
    @Test fun weekStart_mondayInvariant() {
        assertEquals(LocalDate.of(2026, 9, 7), WeekCalculator.weekStart(sem, 2))
        assertEquals(java.time.DayOfWeek.MONDAY, WeekCalculator.weekStart(sem, 10).dayOfWeek)
    }
    @Test fun weekRange_monday_to_sunday() {
        val r = WeekCalculator.weekRange(sem, 2)
        assertEquals(LocalDate.of(2026, 9, 7), r.start); assertEquals(LocalDate.of(2026, 9, 13), r.endInclusive)
    }
    @Test fun semesterTransition_noOverlap() {
        // 自洽 spring fixture（不宣称为学校官方日期）：week1Start=2027-02-22 Monday、18 周
        val spring = Semester(
            id = SemesterId("s2"), displayName = "2026-2027 春季", academicYear = "2026-2027", term = Term.SPRING,
            week1Start = LocalDate.of(2027, 2, 22), totalWeeks = 18,
            startDate = LocalDate.of(2027, 2, 20), endDate = LocalDate.of(2027, 6, 27),
            importedAt = Instant.EPOCH, lastSyncedAt = null,
            isCurrentAcademicSemester = false, portalLinked = true, profileId = ProfileId("p1"), sourceFingerprint = null,
        )
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 1, 10), spring))
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 3, 1), sem))
        assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2027, 2, 22), spring))
    }
    @Test fun autumn2026_official_dates_are_exact() {
        assertEquals(LocalDate.of(2026, 8, 30), sem.startDate)   // 开学注册（≠ week1Start）
        assertEquals(LocalDate.of(2026, 8, 31), sem.week1Start)
        assertEquals(LocalDate.of(2027, 1, 15), sem.endDate)
        assertEquals(20, sem.totalWeeks)
    }
    @Test fun semester_nonMonday_week1Start_throws() {
        assertThrows(IllegalArgumentException::class.java) { sem.copy(week1Start = LocalDate.of(2026, 9, 1)) }
    }
    @Test fun semester_start_after_end_throws() {
        assertThrows(IllegalArgumentException::class.java) { sem.copy(startDate = LocalDate.of(2027, 1, 16)) }
    }
    @Test fun semester_week1Start_outside_semester_dates_throws() {
        assertThrows(IllegalArgumentException::class.java) { sem.copy(startDate = LocalDate.of(2026, 9, 5)) }
    }
    @Test fun semester_totalWeeks_zero_or_over63_throws() {
        assertThrows(IllegalArgumentException::class.java) { sem.copy(totalWeeks = 0) }
        assertThrows(IllegalArgumentException::class.java) { sem.copy(totalWeeks = 64) }
    }
    @Test fun localDateRange_inverted_throws() {
        assertThrows(IllegalArgumentException::class.java) { LocalDateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)) }
    }
    @Test fun weekStart_zero_throws() { assertThrows(IllegalArgumentException::class.java) { WeekCalculator.weekStart(sem, 0) } }
    @Test fun weekStart_after_totalWeeks_throws() { assertThrows(IllegalArgumentException::class.java) { WeekCalculator.weekStart(sem, 21) } }
    @Test fun naturalWeekToday_delegates_to_weekNumberOn() {
        assertEquals(WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 7), sem), WeekCalculator.naturalWeekToday(sem, LocalDate.of(2026, 9, 7)))
        assertNull(WeekCalculator.naturalWeekToday(sem, LocalDate.of(2026, 8, 30)))
    }
}
```
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.WeekCalculatorTest"` → RED（编译失败）。
- [ ] 3. 最小实现：
```kotlin
package com.ustc.timetable.timetable.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

@JvmInline value class SemesterId(val value: String)
@JvmInline value class ProfileId(val value: String)
enum class Term { AUTUMN, SPRING, SUMMER }

data class LocalDateRange(override val start: LocalDate, override val endInclusive: LocalDate) : ClosedRange<LocalDate>

data class Semester(
    val id: SemesterId,
    val displayName: String,
    val academicYear: String,
    val term: Term,
    val week1Start: LocalDate,
    val totalWeeks: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val importedAt: java.time.Instant,
    val lastSyncedAt: java.time.Instant?,
    val isCurrentAcademicSemester: Boolean,
    val portalLinked: Boolean,
    val profileId: ProfileId,
    val sourceFingerprint: String?,
) {
    init {
        require(week1Start.dayOfWeek == java.time.DayOfWeek.MONDAY) { "week1Start must be Monday" }
        require(totalWeeks in 1..63)
    }
}

object SemesterDefaults {
    fun AUTUMN_2026(id: String, profileId: String, now: java.time.Instant = java.time.Instant.now()): Semester = Semester(
        id = SemesterId(id), displayName = "2026-2027 秋季", academicYear = "2026-2027", term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 8, 31), totalWeeks = 20,
        startDate = LocalDate.of(2026, 8, 30),  // 开学注册（≠ week1Start 08-31 上课）
        endDate = LocalDate.of(2027, 1, 15),
        importedAt = now, lastSyncedAt = null,
        isCurrentAcademicSemester = true, portalLinked = false, profileId = ProfileId(profileId), sourceFingerprint = null,
    )
}

object WeekCalculator {
    fun weekNumberOn(date: LocalDate, semester: Semester): Int? {
        if (date.isBefore(semester.week1Start)) return null
        val w = ChronoUnit.WEEKS.between(semester.week1Start, date).toInt() + 1
        return if (w in 1..semester.totalWeeks) w else null
    }
    fun weekStart(semester: Semester, week: Int): LocalDate {
        require(week in 1..semester.totalWeeks) { "week out of range: $week" }
        return semester.week1Start.plusWeeks((week - 1).toLong())
    }
    fun weekRange(semester: Semester, week: Int): LocalDateRange {
        val s = weekStart(semester, week)
        return LocalDateRange(s, s.plusDays(6))
    }
    fun naturalWeekToday(semester: Semester, today: LocalDate): Int? = weekNumberOn(today, semester)
}
```
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseA2: semester model with academic-current/portalLinked/profileId + monday-first week calc"`。

---

## Task A3 — ScheduleProfile + LocalTimeRange + bundled 资产

- SPEC §3.5（含 §3.5.1 的数据基础）。
- 文件：`app/src/main/java/com/ustc/timetable/scheduleprofile/ScheduleProfile.kt`、`app/src/main/java/com/ustc/timetable/scheduleprofile/OfficialProfileLoader.kt`、`app/src/main/assets/profile/official_2026autumn.json`；测试 `app/src/test/java/com/ustc/timetable/scheduleprofile/ScheduleProfileTest.kt`、`app/src/test/java/com/ustc/timetable/scheduleprofile/OfficialProfileLoaderTest.kt`（Robolectric，`@Config(sdk = [36])`）。

步骤：
- [ ] 1. 写 failing test：
```kotlin
package com.ustc.timetable.scheduleprofile

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

class ScheduleProfileTest {
    private val official = OfficialProfileLoader.bundledDefinition()

    @Test fun period3to5_is_0945_1210() {
        val r = official.timeRange(3, 5)
        assertEquals(LocalTime.of(9, 45), r.start); assertEquals(LocalTime.of(12, 10), r.endInclusive)
    }
    @Test fun period11to13_is_1930_2155() {
        val r = official.timeRange(11, 13)
        assertEquals(LocalTime.of(19, 30), r.start); assertEquals(LocalTime.of(21, 55), r.endInclusive)
    }
    @Test fun dayWindow_is_0750_2155() {
        assertEquals(LocalTime.of(7, 50), official.dayWindow().start)
        assertEquals(LocalTime.of(21, 55), official.dayWindow().endInclusive)
    }
    @Test fun invalidPeriod_throws() { assertThrows(IllegalArgumentException::class.java) { official.timeRange(5, 3) } }
    @Test fun localTimeRange_contains() {
        val w = official.dayWindow()
        assertTrue(w.contains(LocalTime.of(14, 20))); assertFalse(w.contains(LocalTime.of(7, 49)))
    }
    @Test fun asset_parses_to_13_periods_monotonic() {
        val ctx = RuntimeEnvironment.getApplication()
        val loaded = OfficialProfileLoader.load(ctx)
        assertEquals(13, loaded.periods.size)
        assertEquals(official, loaded.copy(id = loaded.id, name = loaded.name, isBundledOfficial = true))
        var prev = LocalTime.MIN
        loaded.periods.forEach { p -> assertTrue(p.start > prev); prev = p.start }
    }
}
```
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.scheduleprofile.ScheduleProfileTest"` → RED。
- [ ] 3. 最小实现。`official_2026autumn.json`（完整 13 行）：
```json
{
  "name": "USTC 2026 秋季官方作息",
  "periods": [
    {"number": 1, "start": "07:50", "end": "08:35"},
    {"number": 2, "start": "08:40", "end": "09:25"},
    {"number": 3, "start": "09:45", "end": "10:30"},
    {"number": 4, "start": "10:35", "end": "11:20"},
    {"number": 5, "start": "11:25", "end": "12:10"},
    {"number": 6, "start": "14:00", "end": "14:45"},
    {"number": 7, "start": "14:50", "end": "15:35"},
    {"number": 8, "start": "15:55", "end": "16:40"},
    {"number": 9, "start": "16:45", "end": "17:30"},
    {"number": 10, "start": "17:35", "end": "18:20"},
    {"number": 11, "start": "19:30", "end": "20:15"},
    {"number": 12, "start": "20:20", "end": "21:05"},
    {"number": 13, "start": "21:10", "end": "21:55"}
  ]
}
```
`ScheduleProfile.kt` 关键代码：
```kotlin
package com.ustc.timetable.scheduleprofile

import java.time.LocalTime

data class PeriodTime(val number: Int, val start: LocalTime, val end: LocalTime) {
    init { require(end > start) { "period $number inverted" } }
}

data class LocalTimeRange(override val start: LocalTime, override val endInclusive: LocalTime) : ClosedRange<LocalTime> {
    operator fun contains(t: LocalTime): Boolean = t >= start && t <= endInclusive
}

data class ScheduleProfile(
    val id: ProfileId,
    val name: String,
    val isBundledOfficial: Boolean,
    val periods: List<PeriodTime>,
) {
    init {
        require(periods.size == 13) { "profile must define 13 periods" }
        require(periods.map { it.number } == (1..13).toList()) { "period numbers must be 1..13" }
        periods.zipWithNext().forEach { (a, b) -> require(b.start > a.end) { "periods overlap at ${b.number}" } }
    }
    fun timeRange(startPeriod: Int, endPeriod: Int): LocalTimeRange {
        require(startPeriod in 1..13 && endPeriod in startPeriod..13) { "bad periods $startPeriod-$endPeriod" }
        val s = periods[startPeriod - 1].start
        val e = periods[endPeriod - 1].end
        return LocalTimeRange(s, e)
    }
    fun dayWindow(): LocalTimeRange = LocalTimeRange(periods.minOf { it.start }, periods.maxOf { it.end })
}
```
`OfficialProfileLoader.kt` 关键代码（**官方时间表唯一 production source of truth 是 asset**；strict JSON 默认，无 ignoreUnknownKeys；版本化 bundled id）：
```kotlin
package com.ustc.timetable.scheduleprofile

import android.content.Context
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object OfficialProfileLoader {
    const val ASSET_PATH = "profile/official_2026autumn.json"
    const val BUNDLED_PROFILE_ID = "profile.bundled.ustc.2026-autumn"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Dto(val name: String, val periods: List<PDto>)
    @Serializable private data class PDto(val number: Int, val start: String, val end: String)

    fun load(context: Context): ScheduleProfile =
        fromDto(json.decodeFromString<Dto>(context.assets.open(ASSET_PATH).bufferedReader().readText()), ProfileId(BUNDLED_PROFILE_ID))

    private fun fromDto(dto: Dto, id: ProfileId): ScheduleProfile = ScheduleProfile(
        id = id, name = dto.name, isBundledOfficial = true,
        periods = dto.periods.map { PeriodTime(it.number, LocalTime.parse(it.start), LocalTime.parse(it.end)) },
    )

    private val BUNDLED_JSON = """
    {"name": "USTC 2026 秋季官方作息", "periods": [
      {"number": 1, "start": "07:50", "end": "08:35"},
      {"number": 2, "start": "08:40", "end": "09:25"},
      {"number": 3, "start": "09:45", "end": "10:30"},
      {"number": 4, "start": "10:35", "end": "11:20"},
      {"number": 5, "start": "11:25", "end": "12:10"},
      {"number": 6, "start": "14:00", "end": "14:45"},
      {"number": 7, "start": "14:50", "end": "15:35"},
      {"number": 8, "start": "15:55", "end": "16:40"},
      {"number": 9, "start": "16:45", "end": "17:30"},
      {"number": 10, "start": "17:35", "end": "18:20"},
      {"number": 11, "start": "19:30", "end": "20:15"},
      {"number": 12, "start": "20:20", "end": "21:05"},
      {"number": 13, "start": "21:10", "end": "21:55"}
    ]}
    """
}
```
（A3-local 修正：production/tests 不存在第二份官方时间常量——纯函数测试使用合成 13 节 fixture；官方表真实性由 Robolectric `OfficialProfileLoaderTest` 读取真实 APK asset 逐行 exact 断言，另验证 dayWindow 07:50–21:55 与第 2/7 节后 20 分钟间隔；strict JSON 使误拼字段显式失败。）
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.scheduleprofile.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseA3: bundled official schedule profile with immutable definition and day window"`。

---

## Task A4 — Course / CourseMeeting / ManualScheduleItem + 不变量

- SPEC §3.3、§3.4（`credits: Double?`；meeting 节次 1..13；manual 的 day window 校验在 D2 由编辑器基于绑定 profile 执行，此处只做 end>start 结构不变量）。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/domain/Course.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/CourseMeeting.kt`、`app/src/main/java/com/ustc/timetable/timetable/domain/ManualScheduleItem.kt`（含 `ItemSource`、`CourseId`、`MeetingId`、`ManualItemId`）；测试 `app/src/test/java/com/ustc/timetable/timetable/domain/DomainModelInvariantTest.kt`。

步骤：
- [ ] 1. 写 failing test：
```kotlin
package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainModelInvariantTest {
    private fun sem() = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "p1")

    @Test fun meeting_rejects_badWeekday() {
        assertThrows(IllegalArgumentException::class.java) {
            CourseMeeting(MeetingId("m1"), CourseId("c1"), weekday = 8, startPeriod = 3, endPeriod = 5,
                weekPattern = WeekPattern.range(1, 20), location = "TH-B301", teacherNames = listOf("刘斯"))
        }
    }
    @Test fun meeting_rejects_invertedPeriods() {
        assertThrows(IllegalArgumentException::class.java) {
            CourseMeeting(MeetingId("m1"), CourseId("c1"), weekday = 5, startPeriod = 5, endPeriod = 3,
                weekPattern = WeekPattern.range(1, 20), location = "TH-B301", teacherNames = listOf("刘斯"))
        }
    }
    @Test fun manual_rejects_endBeforeStart() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualScheduleItem(ManualItemId("i1"), sem().id, title = "组会", weekday = 6,
                startTime = LocalTime.of(16, 0), endTime = LocalTime.of(14, 20),
                weekPattern = WeekPattern.range(3, 12), location = null, note = null,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        }
    }
    @Test fun manual_allows_arbitraryMinutes_notOnPeriodBoundary() {
        val item = ManualScheduleItem(ManualItemId("i1"), sem().id, title = "讲座", weekday = 6,
            startTime = LocalTime.of(14, 20), endTime = LocalTime.of(16, 0),
            weekPattern = WeekPattern.of(5), location = null, note = null,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        assert(item.startTime == LocalTime.of(14, 20))
    }
    @Test fun course_credits_nullable() {
        val c = Course(CourseId("c1"), sem().id, sourceCourseKey = "name:高等无机化学", courseCode = "CHEM5013P",
            name = "高等无机化学", credits = null, courseType = null)
        assert(c.credits == null)
        assertThrows(IllegalArgumentException::class.java) {
            Course(CourseId("c2"), sem().id, sourceCourseKey = "", courseCode = "X", name = "Y", credits = null, courseType = null)
        }
    }
}
```
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.DomainModelInvariantTest"` → RED。
- [ ] 3. 最小实现（三个 data class + 构造校验；字段与 SPEC §3.3/§3.4 一字不差，`sourceCourseKey` 非空约束在 Course 构造器）：
```kotlin
package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalTime

enum class ItemSource { SCHOOL, MANUAL }
@JvmInline value class CourseId(val value: String)
@JvmInline value class MeetingId(val value: String)
@JvmInline value class ManualItemId(val value: String)

data class Course(
    val id: CourseId,
    val semesterId: SemesterId,
    val sourceCourseKey: String,
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val courseType: String?,
    val source: ItemSource = ItemSource.SCHOOL,
) { init { require(sourceCourseKey.isNotBlank()) { "sourceCourseKey required" } } }

data class CourseMeeting(
    val id: MeetingId,
    val courseId: CourseId,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPattern: WeekPattern,
    val location: String,
    val teacherNames: List<String>,
    val source: ItemSource = ItemSource.SCHOOL,
) {
    init {
        require(weekday in 1..7) { "weekday out of range: $weekday" }
        require(startPeriod in 1..13 && endPeriod in startPeriod..13) { "bad periods $startPeriod-$endPeriod" }
    }
}

data class ManualScheduleItem(
    val id: ManualItemId,
    val semesterId: SemesterId,
    val title: String,
    val weekday: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val weekPattern: WeekPattern,
    val location: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val source: ItemSource = ItemSource.MANUAL,
) {
    init {
        require(title.isNotBlank()) { "title required" }
        require(weekday in 1..7) { "weekday out of range: $weekday" }
        require(endTime > startTime) { "end must be after start" }
    }
}
```
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseA4: canonical course/meeting/manual models with invariants, credits nullable"`。

---

## Task A5 — Room 持久层 + applySchoolSnapshot 单事务

- SPEC §9.1、§8.2。学校行替换与 semester 元数据更新在**一个 database-level 事务**内。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/data/db/TimetableDatabase.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/entity/Entities.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/dao/Daos.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/Converters.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/Mappers.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/db/ApplySchoolSnapshot.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/data/db/TimetableDatabaseTest.kt`（Robolectric in-memory，`@Config(sdk = [36])`）。

步骤：
- [ ] 1. 写 failing test：
```kotlin
package com.ustc.timetable.timetable.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableDatabaseTest {
    private lateinit var db: TimetableDatabase
    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "profile.bundled.ustc.2026-autumn")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking { db.semesterDao().insert(Mappers.toEntity(sem)) }
    }
    @After fun tearDown() { db.close() }

    private fun snapshot(credits: Double? = 3.0, location: String = "TH-B301"): Pair<List<Course>, List<CourseMeeting>> {
        val course = Course(CourseId("c1"), sem.id, "name:高等无机化学", "CHEM5013P", "高等无机化学", credits, null)
        val meetings = listOf(
            CourseMeeting(MeetingId("m1"), CourseId("c1"), 5, 3, 5, WeekPattern.range(2, 6), location, listOf("吴长征")),
            CourseMeeting(MeetingId("m2"), CourseId("c1"), 5, 3, 5, WeekPattern.range(7, 12), location, listOf("刘斯")),
        )
        return listOf(course) to meetings
    }

    @Test fun roundtrip_semester_courses_meetings_manual() = runBlocking {
        val (courses, meetings) = snapshot()
        db.applySchoolSnapshot(sem.id.value, courses, meetings, "fp1", Instant.ofEpochMilli(1000))
        assertEquals(1, db.courseDao().coursesForSemester(sem.id.value).size)
        assertEquals(2, db.courseDao().meetingsForSemester(sem.id.value).size)
    }

    @Test fun replaceSchoolData_swaps_school_rows_preserves_manual() = runBlocking {
        val (courses, meetings) = snapshot()
        db.applySchoolSnapshot(sem.id.value, courses, meetings, "fp1", Instant.ofEpochMilli(1000))
        db.manualItemDao().insert(Mappers.toEntity(ManualScheduleItem(
            ManualItemId("i1"), sem.id, "组会", 6, LocalTime.of(14, 20), LocalTime.of(16, 0),
            WeekPattern.range(3, 12), null, null, Instant.EPOCH, Instant.EPOCH)))
        val (courses2, meetings2) = snapshot(location = "TH-C204")
        db.applySchoolSnapshot(sem.id.value, courses2, meetings2, "fp2", Instant.ofEpochMilli(2000))
        assertEquals(2, db.courseDao().meetingsForSemester(sem.id.value).size)
        assertTrue(db.courseDao().meetingsForSemester(sem.id.value).all { it.location == "TH-C204" })
        assertEquals(1, db.manualItemDao().itemsForSemester(sem.id.value).size)
        val semAfter = db.semesterDao().byId(sem.id.value)!!
        assertEquals("fp2", semAfter.sourceFingerprint)
    }

    @Test fun applySchoolSnapshot_is_atomic_on_failure() = runBlocking {
        val (courses, meetings) = snapshot()
        val broken = meetings.map { it.copy(id = MeetingId("mx"), courseId = CourseId("missing")) }
        assertThrows(Exception::class.java) {
            db.applySchoolSnapshot(sem.id.value, courses, broken, "fpX", Instant.ofEpochMilli(3000))
        }
        assertEquals(0, db.courseDao().coursesForSemester(sem.id.value).size)
        assertEquals(null, db.semesterDao().byId(sem.id.value)!!.sourceFingerprint)
    }

    @Test fun setExclusiveAcademicCurrent_switches_flags() = runBlocking {
        db.semesterDao().insert(Mappers.toEntity(sem.copy(id = com.ustc.timetable.timetable.domain.SemesterId("s2"), isCurrentAcademicSemester = false)))
        db.semesterDao().setExclusiveAcademicCurrent("s2")
        assertEquals(false, db.semesterDao().byId("s1")!!.isCurrentAcademicSemester)
        assertEquals(true, db.semesterDao().byId("s2")!!.isCurrentAcademicSemester)
    }

    @Test fun converters_roundtrip() {
        val c = Converters()
        assertEquals(WeekPattern.parse("2-6,8"), c.fromWeekPatternMask(c.toWeekPatternMask(WeekPattern.parse("2-6,8"))))
        assertEquals(LocalTime.of(14, 20), c.fromMinutes(c.toMinutes(LocalTime.of(14, 20))))
    }
}
```
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.data.db.TimetableDatabaseTest"` → RED。
- [ ] 3. 最小实现。关键代码：

`Converters.kt`
```kotlin
package com.ustc.timetable.timetable.data.db

import androidx.room.TypeConverter
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun toWeekPatternMask(p: WeekPattern): Long = p.mask
    @TypeConverter fun fromWeekPatternMask(mask: Long): WeekPattern = WeekPattern(mask)
    @TypeConverter fun toMinutes(t: LocalTime): Int = t.hour * 60 + t.minute
    @TypeConverter fun fromMinutes(m: Int): LocalTime = LocalTime.of(m / 60, m % 60)
    @TypeConverter fun toEpochDay(d: LocalDate): Long = d.toEpochDay()
    @TypeConverter fun fromEpochDay(v: Long): LocalDate = LocalDate.ofEpochDay(v)
    @TypeConverter fun toEpochMilli(i: Instant): Long = i.toEpochMilli()
    @TypeConverter fun fromEpochMilli(v: Long): Instant = Instant.ofEpochMilli(v)
    @TypeConverter fun teachersToString(v: List<String>): String = v.joinToString("\u0001")
    @TypeConverter fun stringToTeachers(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split("\u0001")
}
```

`Entities.kt`（五张表；字段与 SPEC §9.1 一致，`semesters.profileId` 带 FK 到 `schedule_profiles`，`course_meetings.courseId` 级联删除；`source` 存 `ItemSource.name`）
```kotlin
package com.ustc.timetable.timetable.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_profiles")
data class ScheduleProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBundledOfficial: Boolean,
    val periodsJson: String,
)

@Entity(tableName = "semesters", foreignKeys = [ForeignKey(
    entity = ScheduleProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"])])
data class SemesterEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val academicYear: String,
    val term: String,
    val week1StartEpochDay: Long,
    val totalWeeks: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val importedAtEpochMilli: Long,
    val lastSyncedAtEpochMilli: Long?,
    val isCurrentAcademicSemester: Boolean,
    val portalLinked: Boolean,
    val profileId: String,
    val sourceFingerprint: String?,
)

@Entity(tableName = "courses", indices = [Index("semesterId")])
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val sourceCourseKey: String,
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val courseType: String?,
    val source: String,
)

@Entity(tableName = "course_meetings",
    indices = [Index("courseId")],
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)])
data class CourseMeetingEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPatternMask: Long,
    val location: String,
    val teacherNamesJoined: String,
    val source: String,
)

@Entity(tableName = "manual_items", indices = [Index("semesterId")])
data class ManualItemEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val title: String,
    val weekday: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val weekPatternMask: Long,
    val location: String?,
    val note: String?,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)
```

`Daos.kt`
```kotlin
package com.ustc.timetable.timetable.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao interface SemesterDao {
    @Insert suspend fun insert(s: SemesterEntity)
    @Query("SELECT * FROM semesters") fun observeAll(): Flow<List<SemesterEntity>>
    @Query("SELECT * FROM semesters WHERE id = :id") suspend fun byId(id: String): SemesterEntity?
    @Query("SELECT * FROM semesters ORDER BY startDateEpochDay DESC") suspend fun allByStartDateDesc(): List<SemesterEntity>
    @Query("SELECT * FROM semesters WHERE isCurrentAcademicSemester = 1 AND portalLinked = 1 LIMIT 1") suspend fun academicCurrentPortalLinked(): SemesterEntity?
    @Query("UPDATE semesters SET isCurrentAcademicSemester = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setExclusiveAcademicCurrent(id: String)
    @Query("UPDATE semesters SET sourceFingerprint = :fingerprint, lastSyncedAtEpochMilli = :syncedAtEpochMilli WHERE id = :id")
    suspend fun updateSyncMeta(id: String, fingerprint: String, syncedAtEpochMilli: Long)
}

@Dao interface CourseDao {
    @Insert suspend fun insertCourses(courses: List<CourseEntity>)
    @Insert suspend fun insertMeetings(meetings: List<CourseMeetingEntity>)
    @Query("DELETE FROM courses WHERE semesterId = :semesterId AND source = 'SCHOOL'")  // meetings 由 CASCADE 删除
    suspend fun deleteSchoolCourses(semesterId: String)
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId") suspend fun coursesForSemester(semesterId: String): List<CourseEntity>
    @Query("SELECT * FROM course_meetings WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    suspend fun meetingsForSemester(semesterId: String): List<CourseMeetingEntity>
    @Query("SELECT * FROM courses WHERE semesterId = :semesterId") fun observeCourses(semesterId: String): Flow<List<CourseEntity>>
    @Query("SELECT * FROM course_meetings WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    fun observeMeetings(semesterId: String): Flow<List<CourseMeetingEntity>>
}

@Dao interface ManualItemDao {
    @Insert suspend fun insert(item: ManualItemEntity)
    @Query("UPDATE manual_items SET title = :title, location = :location, note = :note, weekday = :weekday, startMinutes = :startMinutes, endMinutes = :endMinutes, weekPatternMask = :mask, updatedAtEpochMilli = :updatedAt WHERE id = :id")
    suspend fun update(id: String, title: String, location: String?, note: String?, weekday: Int, startMinutes: Int, endMinutes: Int, mask: Long, updatedAt: Long)
    @Query("DELETE FROM manual_items WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM manual_items WHERE semesterId = :semesterId") suspend fun itemsForSemester(semesterId: String): List<ManualItemEntity>
    @Query("SELECT * FROM manual_items WHERE semesterId = :semesterId") fun observe(semesterId: String): Flow<List<ManualItemEntity>>
}

@Dao interface ScheduleProfileDao {
    @Insert suspend fun insert(profile: com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity)
    @Query("SELECT * FROM schedule_profiles WHERE id = :id") suspend fun byId(id: String): com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity?
    @Query("SELECT * FROM schedule_profiles WHERE id = :id") fun observe(id: String): Flow<com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity?>
}
```
（`Mappers.kt` 提供 `Semester ↔ SemesterEntity`、`Course ↔ CourseEntity`、`CourseMeeting ↔ CourseMeetingEntity`、`ManualScheduleItem ↔ ManualItemEntity` 的纯函数映射；`term` 存枚举 name；teachers join 分隔符复用 `Converters` 的 `\u0001`。）

`ApplySchoolSnapshot.kt`（database-level 单事务，SPEC §8.2）
```kotlin
package com.ustc.timetable.timetable.data.db

import androidx.room.withTransaction
import com.ustc.timetable.timetable.data.db.dao.CourseDao
import com.ustc.timetable.timetable.data.db.dao.SemesterDao
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting
import java.time.Instant
import kotlinx.coroutines.flow.first

suspend fun TimetableDatabase.applySchoolSnapshot(
    semesterId: String,
    courses: List<Course>,
    meetings: List<CourseMeeting>,
    fingerprint: String,
    syncedAt: Instant,
) = withTransaction {
    courseDao().deleteSchoolCourses(semesterId)
    courseDao().insertCourses(courses.map { Mappers.toEntity(it, semesterId) })
    courseDao().insertMeetings(meetings.map { Mappers.toEntity(it) })
    semesterDao().updateSyncMeta(semesterId, fingerprint, syncedAt.toEpochMilli())
}
```
`TimetableDatabase.kt`
```kotlin
package com.ustc.timetable.timetable.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ustc.timetable.timetable.data.db.dao.CourseDao
import com.ustc.timetable.timetable.data.db.dao.ManualItemDao
import com.ustc.timetable.timetable.data.db.dao.SemesterDao
import com.ustc.timetable.timetable.data.db.dao.ScheduleProfileDao
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity

@Database(
    entities = [SemesterEntity::class, ScheduleProfileEntity::class, CourseEntity::class,
        CourseMeetingEntity::class, ManualItemEntity::class],
    version = 1, exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TimetableDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun manualItemDao(): ManualItemDao
    abstract fun scheduleProfileDao(): ScheduleProfileDao

    companion object { const val NAME = "ustc_timetable.db" }
}
```
`Mappers.kt`：`Semester ↔ SemesterEntity`、`Course ↔ CourseEntity`、`CourseMeeting ↔ CourseMeetingEntity`（teachers join "\u0001" 复用 Converters 逻辑）、`ManualScheduleItem ↔ ManualItemEntity` 的纯函数映射对（`term` 存枚举 name）。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN（当前全部测试）。
- [ ] 6. `git add app/src && git commit -m "phaseA5: room schema + applySchoolSnapshot database-level transaction, manual items preserved"`。

---

## Task A6 — 仓库层 + AppContainer

- SPEC §3.5.1、§5.3、§8.1、§9.2。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/data/SemesterRepository.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/TimetableRepository.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/ManualItemRepository.kt`、`app/src/main/java/com/ustc/timetable/scheduleprofile/ScheduleProfileRepository.kt`、`app/src/main/java/com/ustc/timetable/timetable/data/SettingsStore.kt`、`app/src/main/java/com/ustc/timetable/TimetableApp.kt`、`app/src/main/java/com/ustc/timetable/AppContainer.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/data/RepositoriesTest.kt`（Robolectric）。

接口（关键签名）：
```kotlin
class SemesterRepository(private val db: TimetableDatabase) {
    fun observeSemesters(): Flow<List<Semester>>
    suspend fun academicCurrentPortalLinked(): Semester?          // 同步目标；浏览路径不得调用它做写入
    suspend fun viewedOrDefault(viewedId: String?): Semester?      // viewed 回退 academic-current，再回退最新学期
    // 稍后手动创建入口；事务内顺序：克隆 profile 为学期私有行 → insert semester(isCurrentAcademicSemester=true) → setExclusiveAcademicCurrent
    // 学校导入路径不走这里，走 G2 的 db.importNewSemesterWithSnapshot（含学校快照与 boundProfile，SPEC §8.2）
    suspend fun createLocalSemester(base: Semester, sourceProfile: ScheduleProfile): Semester
}
class TimetableRepository(private val db: TimetableDatabase) {
    fun observeSchool(semesterId: String): Flow<Pair<List<Course>, List<CourseMeeting>>>
}
class ManualItemRepository(private val db: TimetableDatabase) {
    fun observe(semesterId: String): Flow<List<ManualScheduleItem>>
    suspend fun add(item: ManualScheduleItem); suspend fun update(item: ManualScheduleItem); suspend fun delete(id: ManualItemId)
}
class ScheduleProfileRepository(
    private val db: TimetableDatabase, private val settings: SettingsStore,
) {
    fun observeForSemester(semesterId: String): Flow<ScheduleProfile>       // 学期绑定 profile（克隆行或 bundled）
    fun observeWorking(): Flow<ScheduleProfile>                             // active_working_profile_id，缺省 bundled 克隆
    suspend fun saveWorkingEdited(base: ScheduleProfile, periods: List<PeriodTime>): ScheduleProfile  // clone-on-write：新行 + 指针更新
    suspend fun restoreWorkingToBundled()                                    // working 指回 bundled
    suspend fun rebindAcademicCurrentSemester(): Boolean                     // 仅 academic-current：克隆 working 并重绑；历史学期恒 false
    suspend fun bindProfileAtSemesterCreation(semesterId: String): String    // 克隆 working 为学期私有行，返回 profileId
}
class SettingsStore(private val context: Context) {
    val viewedSemesterId: Flow<String?>
    val showNonCurrentWeek: Flow<Boolean>            // 默认 false
    val weeklySyncEnabled: Flow<Boolean>             // 默认 true
    val activeWorkingProfileId: Flow<String?>        // 缺省 bundled
    val needReauth: Flow<Boolean>
    val lastSyncFinishedAt: Flow<Long?>
    val notificationRequestShown: Flow<Boolean>
    suspend fun setViewedSemesterId(id: String)      // 唯一写 viewed 的入口（学期切换 UI 专用）
    suspend fun setShowNonCurrentWeek(v: Boolean); suspend fun setWeeklySyncEnabled(v: Boolean)
    suspend fun setActiveWorkingProfileId(id: String); suspend fun setNeedReauth(v: Boolean)
    suspend fun setLastSyncFinishedAt(at: Long); suspend fun markNotificationRequestShown()
}
class AppContainer(context: Context) {
    val db: TimetableDatabase; val settings: SettingsStore
    val semesters: SemesterRepository; val timetable: TimetableRepository
    val manual: ManualItemRepository; val profiles: ScheduleProfileRepository
}
class TimetableApp : android.app.Application() { lateinit var container: AppContainer; override fun onCreate() { super.onCreate(); container = AppContainer(this) } }
```
- 步骤：
- [ ] 1. 写 failing test（`RepositoriesTest.kt`，关键用例）：
  - `viewed_fallback_to_academic_current`：viewedSemesterId=null → `viewedOrDefault(null)` 返回 academic-current 学期。
  - `working_profile_edit_creates_new_row_and_does_not_touch_semester_binding`：建学期（绑定 bundled 克隆）→ `saveWorkingEdited` 修改第 6 节 → `observeForSemester(semesterId)` 仍返回原时间；`observeWorking()` 返回新时间。
  - `rebind_only_academic_current`：历史学期（isCurrentAcademicSemester=false）调用 `rebindAcademicCurrentSemester` 后其绑定不变；academic-current 学期绑定更新为 working 克隆。
  - `manual_crud_updates_updatedAt`。
  - `settings_defaults_and_viewed_persist`。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.data.RepositoriesTest"` → RED。
- [ ] 3. 最小实现：按上面签名；`createLocalSemester(base, sourceProfile)` 内部 `db.withTransaction { val newProfileId = profiles.bindProfileAtSemesterCreation(sourceProfile); semesterDao.insert(Mappers.toEntity(base.copy(profileId = ProfileId(newProfileId)))); semesterDao.setExclusiveAcademicCurrent(base.id.value) }`。`ScheduleProfileRepository.bindProfileAtSemesterCreation(sourceProfile)` 生成新 `UUID` 作 profileId，插入内容为 `sourceProfile` 的克隆行并返回该 id。manifest `<application>` 加 `android:name=".TimetableApp"`。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN。
- [ ] 6. `git add -A && git commit -m "phaseA6: repositories with viewed/academic separation and clone-on-write profile binding"`。

---

## Task B1 — TimelineAxis + WeeklyTimetableLayout + snap5（纯函数）

- SPEC §4.1、§4.2。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/layout/TimelineAxis.kt`、`app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableLayout.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/layout/TimelineAxisTest.kt`、`app/src/test/java/com/ustc/timetable/timetable/layout/WeeklyTimetableLayoutTest.kt`。

接口：
```kotlin
data class TimelineAxis(val start: LocalTime, val endInclusive: LocalTime) {
    fun fractionOf(t: LocalTime): Float      // clamp 到 [0,1]
    fun timeAt(fraction: Float): LocalTime   // clamp 到窗口
}
interface TimedBlock {
    val colorKey: String             // 取色键（SPEC §4.7）：学校块 = "$semesterId:$sourceCourseKey"；手动块 = "manual:$manualItemId"
    val meetingId: MeetingId?        // 学校块非空
    val manualItemId: ManualItemId?  // 手动块非空
    val weekday: Int
    val start: LocalTime
    val endInclusive: LocalTime
    val weeks: WeekPattern
    val title: String
    val location: String
}
data class PlacedBlock(val block: TimedBlock, val column: Int, val columnsInGroup: Int, val topFraction: Float, val heightFraction: Float)

object WeeklyTimetableLayout {
    fun axisOf(profile: ScheduleProfile): TimelineAxis
    fun snapDownTo5Minutes(t: LocalTime): LocalTime          // floor 到 5 分钟
    fun place(blocks: List<TimedBlock>, axis: TimelineAxis): List<PlacedBlock>
}
```
- 步骤：
- [ ] 1. 写 failing test（SPEC §28 Layout 全项）：
  - `axis_fraction_boundaries_0750_2155`：`fractionOf(07:50)==0f`、`fractionOf(21:55)==1f`。
  - `gap5_vs_gap20_height_ratio`：块(09:25–09:30) 与 块(09:25–09:45) 的 heightFraction 比值 == 5.0/20.0。
  - `sevenWeekdays_fixedColumnIndex`：weekday 1..7 各自独立成组。
  - `overlap_twoBlocks_twoColumns_halfWidth`：A 09:45–10:30 与 B 10:00–10:45 → 各 `columnsInGroup==2`，column 0/1。
  - `overlap_threeBlocks_threeColumns`：三块互叠 → 三列。
  - `transitiveChain_grouped`：A 9:00–10:00、B 9:30–10:30、C 10:00–11:00 → 同组三列（A、C 不同列号 0/1、B 1？按贪心：A col0，B col1，C col0）→ 断言 `C.column==0 && B.column==1 && 全部 columnsInGroup==2`（B 与 C 重叠所以组未关闭）。
  - `touchingBlocks_notOverlapped_fullWidth`：A end==B start → 各自 `columnsInGroup==1`。
  - `sequentialBlocks_shareColumn`。
  - `clamp_outOfAxis`：07:30 起的块 topFraction==0；22:10 结束的块 heightFraction 到 1。
  - `snapDown_1423_to_1420`：`snapDownTo5Minutes(LocalTime.of(14,23))==LocalTime.of(14,20)`；`LocalTime.of(14,25)` 不变。
  - `emptyInput_emptyOutput`。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.TimelineAxisTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableLayoutTest"` → RED。
- [ ] 3. 最小实现（贪心列分配核心）：
```kotlin
fun place(blocks: List<TimedBlock>, axis: TimelineAxis): List<PlacedBlock> {
    val out = mutableListOf<PlacedBlock>()
    for (weekday in 1..7) {
        val day = blocks.filter { it.weekday == weekday }.sortedWith(compareBy({ it.start }, { it.endInclusive }))
        if (day.isEmpty()) continue
        data class Open(var lastEnd: LocalTime)
        val columns = mutableListOf<Open>()
        var groupStart = 0
        var groupColumnsStart = 0
        val placedIdx = mutableListOf<Pair<Int, Int>>() // blockIndex to column
        for (b in day) {
            var col = columns.indexOfFirst { it.lastEnd <= b.start }
            if (col == -1) { columns += Open(b.endInclusive); col = columns.size - 1 } else columns[col].lastEnd = b.endInclusive
            placedIdx += day.indexOf(b) to col
            // 组边界：当前块与"组内最早 lastEnd"不再重叠时关闭组
            val groupOver = columns.any { it.lastEnd > b.start }
            if (!groupOver) {
                val groupSize = placedIdx.size - groupStart
                val maxCol = placedIdx.subList(groupStart, placedIdx.size).maxOf { it.second } + 1
                for (k in groupStart until placedIdx.size) {
                    val (bi, c) = placedIdx[k]
                    out += fraction(day[bi], c, maxCol, axis)
                }
                groupStart = placedIdx.size
                columns.clear(); groupColumnsStart = 0
            }
        }
        if (groupStart < placedIdx.size) {
            val maxCol = placedIdx.subList(groupStart, placedIdx.size).maxOf { it.second } + 1
            for (k in groupStart until placedIdx.size) {
                val (bi, c) = placedIdx[k]
                out += fraction(day[bi], c, maxCol, axis)
            }
        }
    }
    return out
}

private fun fraction(b: TimedBlock, column: Int, columnsInGroup: Int, axis: TimelineAxis): PlacedBlock {
    val top = axis.fractionOf(b.start)
    val bottom = axis.fractionOf(b.endInclusive)
    return PlacedBlock(b, column, columnsInGroup, top, bottom - top)
}
```
（实现时以测试为准精化组关闭逻辑；`TimelineAxis.fractionOf` 用 `java.time.Duration.between` 分钟数计算并 clamp。）
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseB1: realtime-axis layout, greedy overlap grouping, 5-minute snap"`。

---

## 本子计划完成判定

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` 全绿。
- domain/data/layout 三个包均有测试且覆盖 SPEC §28 对应项（week pattern、周计算、period 换算、事务、布局、吸附）。
