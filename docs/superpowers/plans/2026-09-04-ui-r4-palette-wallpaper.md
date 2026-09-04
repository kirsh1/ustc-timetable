# UI-R4 Palette and Wallpaper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkboxes for tracking.

**Goal:** Add stable user-applied course palette seeds and bounded wallpaper visibility with explicit candidate, apply, finish, and cancel semantics.

**Architecture:** `SettingsStore` persists exactly two new primitive preferences. Pure editor states distinguish persisted/restoration values from transient candidates. `CoursePalette` uses a deterministic seed permutation over the existing audited 12-color light/dark pairs, preserving the exact default mapping. Wallpaper visibility affects only the image and safety-scrim layer.

**Tech Stack:** Kotlin, DataStore Preferences, Jetpack Compose Material 3, JUnit 4, Robolectric Compose tests, Gradle.

**Spec:** `X:\schedule\docs\superpowers\specs\2026-09-04-ui-r4-addendum.md` §§8–9, §11, §12.

## Frozen boundary

- Add only `course_palette_seed` and `wallpaper_visibility_percent` to `SettingsStore`; do not create a Room migration.
- Keep `appearance_mode` and `timetable_wallpaper_uri` keys, stored values, and APIs intact.
- Never generate a seed during startup, collection, recomposition, sync, or week navigation. Seed generation occurs only on an explicit “换一套” click.
- Wallpaper visibility never modifies card container alpha, content alpha, grid/header alpha, marker alpha, or `CoursePalette.NON_CURRENT_WEEK_ALPHA`.
- Clearing a wallpaper removes only the URI and retains the visibility preference.

---

### Task 1: Persist exactly two compatible appearance preferences

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\data\SettingsStore.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\data\SettingsStoreTest.kt`
- Test FQCN: `com.ustc.timetable.timetable.data.SettingsStoreTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.data

const val DEFAULT_COURSE_PALETTE_SEED: Long = 0L
const val DEFAULT_WALLPAPER_VISIBILITY_PERCENT: Int = 65

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val coursePaletteSeed: Flow<Long>
    val wallpaperVisibilityPercent: Flow<Int>
    suspend fun setCoursePaletteSeed(seed: Long)
    suspend fun setWallpaperVisibilityPercent(percent: Int)
}
```

Use `longPreferencesKey("course_palette_seed")` and `intPreferencesKey("wallpaper_visibility_percent")`. A missing seed emits `DEFAULT_COURSE_PALETTE_SEED`. A missing visibility emits 65. Visibility is `coerceIn(0, 100)` on both read and write; invalid stored data is not rewritten merely by collection. Existing appearance and URI declarations/methods remain byte-for-semantics unchanged.

- [ ] **1. Write failing test.** Extend `SettingsStoreTest` to assert missing defaults, Long round-trip, 0/65/100 round-trip, negative and over-100 read clamps using raw DataStore writes, write clamps, URI/appearance retention, and that clearing wallpaper URI leaves visibility unchanged.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.data.SettingsStoreTest" --rerun-tasks --max-workers=1`. Expected RED: the two keys, flows, constants, and setters do not exist.
- [ ] **3. Minimal production implementation.** Add the two Preferences keys and exact APIs above. Do not rename, remove, or reinterpret any existing key and do not touch Room files.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.data.SettingsStoreTest" --rerun-tasks --max-workers=1`. Expected GREEN: default and clamp behavior is deterministic and existing settings round trips still pass.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.appearance.WallpaperStateTest" --tests "com.ustc.timetable.DebugSeedTest" --rerun-tasks --max-workers=1`. Expected GREEN: existing settings state, wallpaper URI grants, and debug initialization remain unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/data/SettingsStore.kt app/src/test/java/com/ustc/timetable/timetable/data/SettingsStoreTest.kt` and `git commit -m "feat(ui-r4): persist palette and wallpaper preferences"`.

---

### Task 2: Make course palette derivation deterministic and previewable

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\CoursePalette.kt`
- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\PaletteCandidateEditor.kt`
- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\PaletteSeedGenerator.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\appearance\TimetableThemeTest.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\PaletteCandidateEditorTest.kt`
- Test FQCNs: `com.ustc.timetable.appearance.TimetableThemeTest`, `com.ustc.timetable.settings.PaletteCandidateEditorTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

object CoursePalette {
    const val PALETTE_VARIANT_COUNT: Int = 24
    fun variantFor(seed: Long): Int
    fun colorIndexFor(colorKey: String, seed: Long): Int
    fun containerColor(index: Int, dark: Boolean = false): Color
    fun onContainerColor(index: Int, dark: Boolean = false): Color
    fun previewIndices(seed: Long): List<Int>
}

package com.ustc.timetable.settings

fun interface PaletteSeedSource {
    fun nextLong(): Long
}

object SecurePaletteSeedSource : PaletteSeedSource {
    override fun nextLong(): Long
}

object PaletteSeedGenerator {
    fun nextDistinct(currentSeed: Long, source: PaletteSeedSource): Long
}

data class PaletteCandidateState(
    val persistedSeed: Long,
    val candidateSeed: Long,
)

object PaletteCandidateEditor {
    fun open(persistedSeed: Long): PaletteCandidateState
    fun nextDistinctCandidate(
        state: PaletteCandidateState,
        source: PaletteSeedSource,
    ): PaletteCandidateState
    fun restoreDefault(state: PaletteCandidateState): PaletteCandidateState
    fun appliedSeed(state: PaletteCandidateState): Long
    fun cancel(state: PaletteCandidateState): PaletteCandidateState
}
```

For `DEFAULT_COURSE_PALETTE_SEED == 0L`, `colorIndexFor` executes the existing unsigned first MD5 byte modulo 12 exactly. `variantFor(seed)` is exactly `Math.floorMod(seed, PALETTE_VARIANT_COUNT.toLong()).toInt()`. Every seed selects one of exactly 24 unique, platform-stable permutations: variants `0..11` rotate the identity ordering by that value; variants `12..23` rotate the reversed ordering by `variant - 12`. Variant 0 is identity, so the missing/default seed preserves every UI-R3 course-to-color mapping. `previewIndices` returns the selected permutation; the existing audited color pairs retain their bounded hue/saturation/tone, light/dark contrast, and color-number identity.

`PaletteSeedSource` is injected at the Settings UI boundary. `SecurePaletteSeedSource.nextLong()` performs one `SecureRandom.nextLong()` call, but that method is invoked exactly once and only by an explicit `palette_new_candidate` click; opening, recomposition, navigation, Apply, cancel, and restore-default never invoke it. `PaletteSeedGenerator.nextDistinct` first samples the source. If the sampled seed's 12-index preview differs from `currentSeed`, it returns the sample unchanged. If the sample collides, it deterministically returns `(CoursePalette.variantFor(currentSeed) + 1).rem(CoursePalette.PALETTE_VARIANT_COUNT).toLong()`, the canonical seed for the next variant. Because all 24 permutations are unique, the returned preview is guaranteed to differ without retry loops, clocks, startup randomness, or arbitrary RGB. `PaletteCandidateEditor.nextDistinctCandidate` compares against the currently previewed `candidateSeed`, not only the persisted seed. `restoreDefault` changes only `candidateSeed`; `appliedSeed` returns the candidate for an explicit persistence callback; `cancel` restores candidate to persisted.

- [ ] **1. Write failing test.** Add exact named tests `default_seed_preserves_existing_palette_mapping`, `candidate_seed_is_not_persisted_before_apply`, `restore_default_requires_apply`, `applied_seed_persists_across_recreation`, `new_candidate_is_distinct_from_current_preview`, and `colliding_seed_source_uses_deterministic_fallback`. Assert all 24 previews are deterministic and unique, every preview contains 12 unique indices, repeated explicit clicks compare against the current candidate, stable same-key mapping, light/dark identity, and contrast ratio at least 4.5 for every pair.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.TimetableThemeTest" --tests "com.ustc.timetable.settings.PaletteCandidateEditorTest" --rerun-tasks --max-workers=1`. Expected RED: `CoursePalette` accepts no seed, `PaletteCandidateEditor` does not exist, and there is no injectable collision-safe seed generator.
- [ ] **3. Minimal production implementation.** Add the seed overload, exact 24-variant permutation, `PaletteSeedSource`, deterministic distinct fallback, and pure editor while keeping the current color pairs and alpha policy. Retain the one-argument color lookup only as a delegating overload to the default seed. Do not add DataStore, time, process, startup, or recomposition dependencies to these pure components.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.TimetableThemeTest" --tests "com.ustc.timetable.settings.PaletteCandidateEditorTest" --rerun-tasks --max-workers=1`. Expected GREEN: variant 0 preserves baseline indices, every explicit replacement has a distinct preview, collision fallback is deterministic, and all variants remain contrast-safe.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --tests "com.ustc.timetable.manual.ManualItemFlowTest" --rerun-tasks --max-workers=1`. Expected GREEN: default seed leaves all school/manual and mini-map colors unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/CoursePalette.kt app/src/main/java/com/ustc/timetable/settings/PaletteCandidateEditor.kt app/src/main/java/com/ustc/timetable/settings/PaletteSeedGenerator.kt app/src/test/java/com/ustc/timetable/appearance/TimetableThemeTest.kt app/src/test/java/com/ustc/timetable/settings/PaletteCandidateEditorTest.kt` and `git commit -m "feat(ui-r4): derive course palette from applied seed"`.

---

### Task 3: Define wallpaper visibility math and transient editor state

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\appearance\Appearance.kt`
- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\WallpaperVisibilityEditor.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\appearance\WallpaperStateTest.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\WallpaperVisibilityEditorTest.kt`
- Test FQCNs: `com.ustc.timetable.appearance.WallpaperStateTest`, `com.ustc.timetable.settings.WallpaperVisibilityEditorTest`

**Required production contract**

```kotlin
package com.ustc.timetable.appearance

fun wallpaperImageAlpha(visibilityPercent: Int): Float
fun wallpaperScrim(appearance: ResolvedAppearance, visibilityPercent: Int): Color

package com.ustc.timetable.settings

data class WallpaperVisibilityState(
    val persistedPercent: Int,
    val candidatePercent: Int,
)

object WallpaperVisibilityEditor {
    fun open(persistedPercent: Int): WallpaperVisibilityState
    fun preview(state: WallpaperVisibilityState, percent: Int): WallpaperVisibilityState
    fun finish(state: WallpaperVisibilityState): WallpaperVisibilityState
    fun cancel(state: WallpaperVisibilityState): WallpaperVisibilityState
}
```

All inputs clamp to `0..100`. `wallpaperImageAlpha` returns normalized visibility. Scrim alpha is zero at 0 and scales to the existing appearance-specific safety alpha at 100; the solid Material surface remains behind the image. `preview` changes only candidate. `finish` promotes candidate to the restoration/persisted field for the caller's persistence write. `cancel` resets candidate to the latest persisted/restoration field. No function accepts or returns course-card alpha.

- [ ] **1. Write failing test.** Add exact named tests `slider_preview_does_not_change_course_card_alpha`, `slider_finish_persists_candidate`, and `slider_cancel_before_finish_restores_persisted_value`; cover clamp behavior, 0/65/100 image and scrim results, cancel after finish retaining the finished value, and light/dark safety scrims.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.WallpaperStateTest" --tests "com.ustc.timetable.settings.WallpaperVisibilityEditorTest" --rerun-tasks --max-workers=1`. Expected RED: visibility-aware functions/editor types are absent and wallpaper scrim currently has no percentage input.
- [ ] **3. Minimal production implementation.** Implement bounded pure math and the editor state. Keep the URI grant/release functions in `WallpaperSelection` unchanged and leave `CoursePalette.alphaFor` untouched.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.WallpaperStateTest" --tests "com.ustc.timetable.settings.WallpaperVisibilityEditorTest" --rerun-tasks --max-workers=1`. Expected GREEN: candidate/finish/cancel transitions and background-layer math exactly match the spec.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.WallpaperImageLoaderTest" --tests "com.ustc.timetable.appearance.TimetableThemeTest" --rerun-tasks --max-workers=1`. Expected GREEN: URI decoding, unavailable reporting, theme resolution, and course contrast remain unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/appearance/Appearance.kt app/src/main/java/com/ustc/timetable/settings/WallpaperVisibilityEditor.kt app/src/test/java/com/ustc/timetable/appearance/WallpaperStateTest.kt app/src/test/java/com/ustc/timetable/settings/WallpaperVisibilityEditorTest.kt` and `git commit -m "feat(ui-r4): model wallpaper visibility editing"`.

---

### Task 4: Add palette candidate/apply/cancel/default settings UI

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsModels.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsViewModel.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsScreen.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\SettingsViewModelTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\SettingsScreenTest.kt`
- Test FQCNs: `com.ustc.timetable.settings.SettingsViewModelTest`, `com.ustc.timetable.settings.SettingsScreenTest`

**Required production contract**

```kotlin
data class SettingsUiState(
    val coursePaletteSeed: Long = DEFAULT_COURSE_PALETTE_SEED,
    val wallpaperVisibilityPercent: Int = DEFAULT_WALLPAPER_VISIBILITY_PERCENT,
    val appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    val timetableWallpaperUri: String? = null,
)

data class SettingsCallbacks(
    val onApplyCoursePaletteSeed: (Long) -> Unit = {},
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    paletteSeedSource: PaletteSeedSource = SecurePaletteSeedSource,
)

class SettingsViewModel {
    fun onCoursePaletteSeedApplied(seed: Long)
}
```

The real data classes and composable retain all existing fields/parameters; the excerpt fixes only new signatures/defaults. The production call site supplies a process-local `PaletteSeedSource` implementation, while tests inject a counting/fixed source. `SettingsViewModel.Persisted` and its combines include both new flows without replacing existing ones. The “课程色系” row opens a sheet with a 12-color candidate preview, `palette_new_candidate`, `palette_apply`, and `palette_restore_default`. Only the explicit new-candidate click calls `PaletteCandidateEditor.nextDistinctCandidate(editorState, paletteSeedSource)`. Opening initializes `PaletteCandidateEditor.open(state.coursePaletteSeed)`. Recomposition, dismiss/back, Apply, and restore-default do not call the source. Dismiss/back calls cancel and writes nothing. Apply invokes the callback once and closes only after adopting the candidate. Restore-default changes preview only.

- [ ] **1. Write failing test.** In `SettingsViewModelTest`, assert applied seed writes once and is emitted after recreation. In `SettingsScreenTest`, assert preview count 12, add exact named test `seed_source_is_called_only_by_explicit_new_candidate`, verify one explicit click samples exactly once and changes preview without persistence, restore-default/Apply/dismiss/recomposition do not sample, Apply writes the current candidate, dismiss writes nothing, and reopening starts from the persisted value.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --rerun-tasks --max-workers=1`. Expected RED: settings state/callbacks contain no palette seed and the palette editor row/sheet is absent.
- [ ] **3. Minimal production implementation.** Extend the existing settings combine with both preference values, add only the palette persistence method in this task, inject `PaletteSeedSource` at the Settings screen boundary, and render candidate controls using `PaletteCandidateEditor`. Invoke the source only inside the explicit new-candidate click handler. Do not change theme or wallpaper URI rows.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.PaletteCandidateEditorTest" --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: only explicit Apply persists and all candidate/default/cancel flows are deterministic.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.sync.ManualSyncFlowTest" --rerun-tasks --max-workers=1`. Expected GREEN: theme, wallpaper URI, sync, relogin, profile, and notification settings remain intact.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/settings/SettingsModels.kt app/src/main/java/com/ustc/timetable/settings/SettingsViewModel.kt app/src/main/java/com/ustc/timetable/settings/SettingsScreen.kt app/src/test/java/com/ustc/timetable/settings/SettingsViewModelTest.kt app/src/test/java/com/ustc/timetable/settings/SettingsScreenTest.kt` and `git commit -m "feat(ui-r4): add course palette settings workflow"`.

---

### Task 5: Wire wallpaper preview/finish/cancel and runtime layers

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsModels.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsViewModel.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsScreen.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\ui\theme\TimetableTheme.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\MainActivity.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\SettingsViewModelTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\settings\SettingsScreenTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\appearance\WallpaperImageLoaderTest.kt`
- Test FQCNs: `com.ustc.timetable.settings.SettingsViewModelTest`, `com.ustc.timetable.settings.SettingsScreenTest`, `com.ustc.timetable.appearance.WallpaperImageLoaderTest`

**Required production contract**

```kotlin
data class SettingsCallbacks(
    val onWallpaperVisibilityFinished: (Int) -> Unit = {},
)

class SettingsViewModel {
    fun onWallpaperVisibilityFinished(percent: Int)
}

@Composable
fun AppBackgroundLayer(
    wallpaperUri: String? = null,
    wallpaperVisibilityPercent: Int = DEFAULT_WALLPAPER_VISIBILITY_PERCENT,
    onWallpaperUnavailable: () -> Unit = {},
    onWallpaperAvailable: () -> Unit = {},
    content: @Composable () -> Unit,
)

@Composable
internal fun WallpaperPreview(
    wallpaperUri: String,
    visibilityPercent: Int,
    modifier: Modifier = Modifier,
)
```

The wallpaper sheet initializes `WallpaperVisibilityEditor.open(state.wallpaperVisibilityPercent)`. Slider `onValueChange` previews candidate only. `onValueChangeFinished` calls the persistence callback with `finish(state).persistedPercent` and makes it the new cancel restoration point. Dismiss before finish cancels; dismiss after finish keeps the finished value. Slider and bounded preview exist only for nonnull URI. `MainActivity` collects the stored visibility alongside existing appearance/URI flows and passes it to `AppBackgroundLayer`. The layer draws solid surface, wallpaper image at `wallpaperImageAlpha`, appearance safety scrim, then fully opaque content.

- [ ] **1. Write failing test.** Add UI tests for visible-only-with-URI slider, live bounded preview at 0/65/100, cancel before finish, finish persistence, cancel after finish, URI clearing retaining visibility, and unchanged course-card/content alpha. Extend loader test to assert runtime layer receives the clamped persisted value.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.appearance.WallpaperImageLoaderTest" --rerun-tasks --max-workers=1`. Expected RED: the callback and slider/preview are absent, and `AppBackgroundLayer` always renders full image plus fixed scrim.
- [ ] **3. Minimal production implementation.** Add the persistence method, transient sheet state, bounded preview, and runtime parameter. Reuse the existing image loader and URI availability callbacks. Keep Settings itself on a solid background and never apply visibility to the content Box.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.WallpaperVisibilityEditorTest" --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.appearance.WallpaperImageLoaderTest" --rerun-tasks --max-workers=1`. Expected GREEN: preview, finish, cancellation, persisted restoration, and runtime layer all share the bounded percentage.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.appearance.TimetableThemeTest" --tests "com.ustc.timetable.appearance.WallpaperStateTest" --tests "com.ustc.timetable.MainActivityLifecycleTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: theme mode, URI grants, Activity lifecycle, and timetable content opacity remain unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/settings/SettingsModels.kt app/src/main/java/com/ustc/timetable/settings/SettingsViewModel.kt app/src/main/java/com/ustc/timetable/settings/SettingsScreen.kt app/src/main/java/com/ustc/timetable/ui/theme/TimetableTheme.kt app/src/main/java/com/ustc/timetable/MainActivity.kt app/src/test/java/com/ustc/timetable/settings/SettingsViewModelTest.kt app/src/test/java/com/ustc/timetable/settings/SettingsScreenTest.kt app/src/test/java/com/ustc/timetable/appearance/WallpaperImageLoaderTest.kt` and `git commit -m "feat(ui-r4): control wallpaper display strength"`.

## Subplan completion command

Run:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.data.SettingsStoreTest" --tests "com.ustc.timetable.appearance.TimetableThemeTest" --tests "com.ustc.timetable.appearance.WallpaperStateTest" --tests "com.ustc.timetable.appearance.WallpaperImageLoaderTest" --tests "com.ustc.timetable.settings.PaletteCandidateEditorTest" --tests "com.ustc.timetable.settings.WallpaperVisibilityEditorTest" --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.MainActivityLifecycleTest" --rerun-tasks --max-workers=1
```

Expected result: all named classes pass with zero failures, errors, and skips; default palette mapping matches UI-R3 and no wallpaper test observes a course-card/content alpha change.
