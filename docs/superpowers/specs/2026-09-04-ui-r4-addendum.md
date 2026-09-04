# UI-R4 Addendum — timetable density, ghost aggregation, palette, and wallpaper controls

Date: 2026-09-04  
Baseline: `75b15822890e379b3c3382bdf95a76b539065f4b`

## 1. Scope and invariants

This addendum refines the existing UI-R4 implementation without changing portal parsing, normalization, Room data, sync fingerprints, manual-item persistence, or semester transaction behavior.

The existing vertical timetable geometry is frozen. This change must not redesign teaching-group gaps, time-boundary clustering, Y-axis mapping, long-press Y inversion, or the WeekOverview vertical axis. All existing vertical-axis round-trip and boundary tests remain regression gates.

The work covers:

- automatic semester end-date calculation with an explicit manual override;
- optical alignment of the week title and date range;
- course-card insets and text priority;
- vertical gestures for WeekOverview;
- presentation-only aggregation of non-current-week overlaps;
- a persisted, refreshable course-palette seed;
- a persisted wallpaper visibility control;
- measured horizontal time-rail width.

## 2. Semester end-date authority

The automatic inclusive semester end date is derived from the first teaching-week Monday, not the administrative semester start date:

```kotlin
autoEndDate = week1Start
    .plusWeeks(totalWeeks.toLong())
    .minusDays(1)
```

For `week1Start = 2026-08-31` and `totalWeeks = 20`, the result is Sunday `2027-01-17`.

`SemesterConfirmEditorState` owns an explicit end-date mode:

- `AUTO`: when both `week1Start` and a valid `totalWeeks` are present, the displayed and validated end date is recomputed immediately;
- `MANUAL`: a user-picked end date is preserved when week 1 or total weeks later changes.

The editor initially uses `AUTO`. Selecting the end-date field moves it to `MANUAL`. A visible “恢复自动计算” action returns to `AUTO`. The field labels the effective value as “自动计算” or “已手动修改”. Validation and the final `ConfirmedSemesterMeta` consume the effective date, so import transaction APIs remain unchanged.

## 3. Week-bar optical alignment

Week number and date range use the same fixed-height container, `Alignment.CenterVertically`, explicit font sizes and line heights, and disabled platform font padding. They do not use baseline alignment.

Their measured text-bounds `centerY` values must differ by no more than 1dp at font scales 1.0 and 1.3 and in compact width. Existing vector navigation, overview, and settings buttons remain.

## 4. Course-card visual and text hierarchy

Logical placement remains exactly `axis.position(start)` to `axis.position(end)`. The visible rounded card is inset inside that logical hit region by 1dp at the top and bottom and a uniform horizontal inset. Extremely short cards clamp to a non-negative visual height. Hit testing retains the full logical time region.

Text priority is:

```text
course name > location > teachers > time
```

Rules:

- Course names naturally wrap for one to three lines. A course such as “电化学研究方法” should remain complete when its card has the normal available height. Only insufficient height permits a final-line ellipsis.
- Location is a single line, uses `FontFamily.Monospace` and `FontWeight.SemiBold`, and precedes every optional field. Codes such as `TH-A301`, `TH-B301`, and `3C107` therefore have stable glyph widths. Chinese glyphs may use font fallback while keeping the same single-line, semibold treatment.
- Teacher names are joined with `、`. When remaining height permits, teachers wrap to at most two lines. A one-line budget uses one-line ellipsis. With no complete line available, teachers are omitted.
- In-card time is lowest priority and may be omitted because the fixed rail retains time semantics.
- Empty location or teacher values are omitted; no “未知” or “未填写” placeholders are introduced.
- Accessibility descriptions and detail sheets always retain the complete title, location, teacher list, weeks, and time.
- School and manual cards share the same renderer and priority policy.

Markers reserve their own bottom-right region and must not cover the mandatory location line.

## 5. WeekOverview vertical gesture

The seven-day grid body, excluding the fixed time rail, accepts a second WeekOverview control:

- downward vertical drag expands it;
- upward vertical drag collapses it.

Recognition uses the platform touch slop. Vertical displacement must dominate horizontal displacement before the gesture is claimed. Once claimed, the gesture cancels a pending card click. Movement below slop remains a click, horizontal movement remains the week pager gesture, and existing empty-area long press remains the manual-item gesture. The vector overview button remains available. Expansion state remains semester-scoped, in-memory, and non-persistent.

## 6. Presentation-only ghost aggregation

Aggregation is performed after raw school/manual blocks exist but before the main grid is placed. Room records, domain courses and meetings, synchronization, and WeekOverview active-only projections are unchanged.

When `showNonCurrentWeek == false`, behavior is unchanged.

When it is true:

1. Place current-week active blocks using the existing overlap algorithm. Active-versus-active conflicts remain side-by-side.
2. A ghost that overlaps no active block remains an ordinary gray ghost card and follows the existing ghost-only overlap behavior.
3. A ghost overlapping an active block does not consume an active layout column. It is attached to one active representative.
4. If a ghost overlaps several active blocks, attach it to the active block with the greatest overlap duration. Ties are resolved by the existing stable business key so input order cannot affect the result.

School-course identity uses the existing stable business identity (`semesterId + sourceCourseKey`, currently represented by the school block `colorKey`), never display name. Same-name courses with different source keys are different courses. Manual items retain their individual manual IDs.

Each representative card can expose two theme-colored vector markers:

- different-course marker: one or more attached ghosts have another stable course identity;
- same-course-variant marker: the same course has another teacher, location, time, or week arrangement.

If both apply, the compact markers appear side by side. They are vector shapes, not Unicode characters.

If the time region has no active course, no representative aggregation occurs; existing gray ghost cards remain unchanged.

## 7. Course-detail paging

Opening a representative with different-course candidates shows a horizontally pageable detail sheet:

- page 1 is the current-week representative course;
- pages 2 through N are all attached non-current-week courses with different stable identities;
- stable sorting is used for candidates;
- vector previous/next controls and a page indicator expose navigation.

If there is no active course, gray ghost cards keep their existing independent click behavior; they are not converted into an aggregate or carousel.

Same-course teacher, location, time, or week variants never create extra pages or paging controls. The existing single course page remains and “完整安排” lists every meeting variant.

## 8. Persisted course-palette seed

`SettingsStore` owns a global `course_palette_seed`. It applies across all semesters and to both school and manual cards. The same seed and stable business key always produce the same palette index across restart, sync replacement, and week changes.

The settings palette editor contains a 12-color preview plus:

- “换一套”: create a temporary candidate seed and update only the preview;
- “应用”: persist the candidate seed;
- “恢复默认”: select the default seed;
- dismiss/back: discard the temporary candidate and retain the applied seed.

Palette generation is constrained rather than arbitrary RGB: hue distribution, saturation, and light/dark tones are bounded, and each container/on-container pair must preserve the existing minimum contrast requirement. Light and dark appearances share color identity while using appearance-appropriate tones. Wallpaper use must not lower course-card text contrast.

## 9. Wallpaper visibility

The setting is named “壁纸显示强度”, ranges from 0 to 100%, defaults to 65%, and is persisted as `wallpaper_visibility_percent`.

- 0% hides the wallpaper and produces the solid timetable background.
- 100% means the maximum permitted wallpaper visibility, while retaining the light/dark safety scrim.
- Dragging updates a preview immediately; persistence occurs when interaction completes.
- The slider is shown only when a wallpaper URI is selected.
- Clearing a wallpaper preserves the value for a later selection.
- The setting affects only the wallpaper image/scrim layer on the timetable page. It never changes course-card container or text alpha, the grid, headers, markers, or any primary content.

The wallpaper action sheet provides a bounded preview so the drag result is visible while Settings itself remains wallpaper-free.

## 10. Horizontal time-rail width only

The fixed 44dp rail is replaced by a measured width:

```text
railWidth = widest rendered time label
          + 4dp left padding
          + 4dp right padding
          + at most 0.5dp divider
```

There is no second spacer between the rail and Monday. Screen-edge-to-label and label-to-Monday whitespace should be approximately symmetric. Font scale 1.3 may increase the measured rail width but must not clip the widest label.

All released width enters the seven-day grid:

```text
gridWidth = viewportWidth - railWidth
dayWidth = gridWidth / 7
```

All seven columns remain equal and weekends remain visible. Long-press X inversion continues to receive coordinates relative only to the grid body.

This is the only permitted time-rail change. No vertical time labels, group gaps, axis positions, inverse mapping, period derivation, or WeekOverview Y geometry may change.

## 11. State and component boundaries

- `SemesterConfirmEditor` owns automatic/manual end-date state and pure calculation.
- `TimetableViewModel` (or a small UI projection helper beside it) owns ghost-to-active association and stable detail-page models.
- `WeeklyTimetableGrid` renders already-projected representatives, ghost-only cards, and markers; it does not query repositories.
- `CourseDetailSheet` gains an optional pager wrapper while existing single-page content remains reusable.
- `CoursePalette` derives colors from applied seed and stable key; Settings owns candidate editing.
- `SettingsStore` persists only applied palette seed and wallpaper visibility.
- `AppBackgroundLayer` applies wallpaper visibility below the grid and cards.
- `FixedTimeRail` measures horizontal width while retaining its existing vertical geometry.

## 12. Tests and qualification

Tests are written and observed failing before production changes. Required coverage includes:

- auto end date, live recalculation, manual override preservation, restore-auto behavior, inclusive Sunday result, and final metadata;
- top-bar text centerY within 1dp at font scales 1.0/1.3 and compact width;
- 1dp card visual inset without changing logical positions;
- monospace semibold location, multiline/ellipsis/omitted teacher policies, mandatory-field priority, and complete accessibility text;
- touch-slop and direction arbitration for vertical overview gestures, clicks, long presses, and horizontal paging;
- active-only placement, deterministic maximum-overlap attachment, different-course versus same-course classification, ghost-only preservation, and active-conflict preservation;
- detail first-page context, stable different-course paging, and absence of a pager for same-course variants;
- candidate/apply/cancel/default palette behavior, persistence, stable mapping, and light/dark contrast;
- wallpaper 0/65/100 behavior, persistence, preview, scrim safety, and course-card alpha isolation;
- measured rail width, symmetric 4dp padding, no extra spacer, equal seven-day redistribution, and fontScale 1.3 non-clipping;
- unchanged time-boundary positions, segmented-axis round trips, manual long-press mapping, and WeekOverview vertical projection.

Qualification runs targeted semester, timetable layout/UI, appearance, settings, and persistence tests; then the full unit suite, lint, connected tests, debug/release builds, and emulator screenshots in light/dark and expanded/collapsed states.

## 13. Explicit non-goals

- No portal URL, selector, parser, normalizer, session, or real-network change.
- No Room schema or sync fingerprint/diff change.
- No vertical time-axis redesign.
- No change to weekly worker behavior.
- No persistence of WeekOverview expansion.
- No random color changes on startup, recomposition, sync, or week navigation.
