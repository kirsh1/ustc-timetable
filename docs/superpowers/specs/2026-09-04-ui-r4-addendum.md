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

`SemesterConfirmEditorState` owns an explicit end-date mode and keeps the manually supplied date separate from the derived effective date:

- `AUTO`: when both `week1Start` and a valid `totalWeeks` are present, the displayed and validated effective end date is recomputed immediately;
- `MANUAL`: a recognized draft date or user-confirmed date is preserved when week 1 or total weeks later changes.

Initialization is deterministic:

- a draft with `endDate != null` starts in `MANUAL` and preserves that recognized value;
- a draft with no end date starts in `AUTO`.

Opening the end-date picker does not change the mode. Confirming a selected date changes the state to `MANUAL`; dismissing or cancelling the picker causes no state change. A visible “恢复自动计算” action returns to `AUTO`. In AUTO mode, changing `week1Start` or valid `totalWeeks` immediately recalculates the effective value. In MANUAL mode, neither change overwrites the manual date. The field labels the effective value as “自动计算” or “已手动修改”. Validation and the final `ConfirmedSemesterMeta` consume the effective date, so import transaction APIs remain unchanged.

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

Recognition uses the platform touch slop and the following exclusive state machine:

1. `IDLE -> PRESS_PENDING` on pointer down. Click and empty-area long press are candidates; neither has fired.
2. While still inside touch slop, pointer up resolves to `CLICK_OWNED` and dispatches the existing card/empty click behavior.
3. If the existing long-press timeout expires first on an eligible empty grid location, transition to `LONG_PRESS_OWNED`, dispatch manual creation once, consume the rest of that pointer sequence, and never open/close WeekOverview.
4. If movement crosses touch slop before the timeout, compare accumulated absolute displacement. Horizontal dominance transitions to `HORIZONTAL_OWNED` and yields the sequence to `HorizontalPager`. Vertical dominance transitions to `VERTICAL_OWNED`, cancels both click and long-press candidates, and consumes the sequence in the grid gesture handler.
5. `VERTICAL_OWNED` resolves downward displacement to expand and upward displacement to collapse. It performs at most one state change per pointer sequence.
6. Once any owner is selected, ownership cannot change until up/cancel returns the machine to `IDLE`. Pointer cancellation performs no action.

Equal or indeterminate displacement remains pending until a direction dominates or another pending rule resolves. The vector overview button remains available. Expansion state remains semester-scoped, in-memory, and non-persistent.

## 6. Presentation-only ghost aggregation

Aggregation is performed after raw school blocks exist but before the main grid is placed. It is strictly a SCHOOL-to-SCHOOL presentation rule. Room records, domain courses and meetings, synchronization, and WeekOverview active-only projections are unchanged.

When `showNonCurrentWeek == false`, behavior is unchanged.

When it is true:

1. Place current-week active blocks using the existing overlap algorithm. Active-versus-active conflicts remain side-by-side.
2. A ghost that overlaps no active block remains an ordinary gray ghost card and follows the existing ghost-only overlap behavior.
3. A ghost overlapping an active block does not consume an active layout column. It is attached to one active representative.
4. If a ghost overlaps several active blocks, attach it to the active block with the greatest overlap duration. Ties are resolved by the existing stable business key so input order cannot affect the result.

School-course identity uses the existing stable business identity (`semesterId + sourceCourseKey`, currently represented by the school block `colorKey`), never display name. Same-name courses with different source keys are different courses.

MANUAL active blocks, MANUAL ghosts, SCHOOL-to-MANUAL overlaps, MANUAL-to-MANUAL overlaps, click behavior, and editor behavior remain exactly as they are at the baseline. Manual blocks never enter school aggregation, never create school markers, and never become a school detail-carousel page.

Each representative card can expose two theme-colored vector markers:

- different-course marker: one or more attached ghosts have another stable course identity;
- same-course-variant marker: the same stable course identity has another non-week presentation signature.

The presentation signature consists of a canonical teacher-name set, trimmed location, weekday, start time, and end time. The teacher set trims names, removes empty/duplicate entries, and sorts them so source ordering alone cannot create a marker. `WeekPattern` is deliberately excluded: meetings that differ only in their week pattern do not produce a variant marker. Their weeks still appear in the existing complete-arrangement detail.

If both apply, the compact markers appear side by side. They are vector shapes, not Unicode characters.

If the time region has no active course, no representative aggregation occurs; existing gray ghost cards remain unchanged.

## 7. Course-detail paging

Opening a representative with different-course candidates shows a horizontally pageable detail sheet with exactly one page per stable school-course identity:

- page 1 is the current-week representative course;
- pages 2 through N are the distinct attached non-current-week stable course identities;
- vector previous/next controls and a page indicator expose navigation.

The representative active meeting is the page-1 anchor. For each alternative identity, its anchor is the minimum canonical meeting tuple: earliest week in `WeekPattern`, weekday, start time, end time, trimmed location, canonical teacher-name set, then canonical `WeekPattern` text. Alternative pages sort by that anchor tuple and finally by stable course identity. No locally generated `MeetingId` participates in ordering. Exact duplicate tuples collapse for anchor selection. Multiple attached meetings with one stable course identity therefore create one page, whose complete-arrangement section still contains all meetings for that course.

If there is no active course, gray ghost cards keep their existing independent click behavior; they are not converted into an aggregate or carousel.

Same-course teacher, location, weekday, or time variants never create extra pages or paging controls. The existing single course page remains and “完整安排” lists every meeting variant. A WeekPattern-only difference creates neither a marker nor a page.

## 8. Persisted course-palette seed

`SettingsStore` owns a global `course_palette_seed: Long`. It applies across all semesters and to both school and manual cards. The same seed and stable business key always produce the same palette index across restart, sync replacement, and week changes.

The default seed is a fixed constant whose mapping exactly preserves the baseline `MD5(colorKey)[0] % 12` palette-index behavior. Introducing the key therefore causes no recoloring until the user applies another candidate.

The settings palette editor contains a 12-color preview plus:

- “换一套”: create a temporary candidate seed and update only the preview;
- “应用”: persist the candidate seed;
- “恢复默认”: change only the temporary candidate to the fixed default seed;
- dismiss/back: discard the temporary candidate and retain the applied seed.

Neither “换一套” nor “恢复默认” writes DataStore. Only “应用” persists the current candidate, so restoring the default also requires Apply.

Palette generation is constrained rather than arbitrary RGB: hue distribution, saturation, and light/dark tones are bounded, and each container/on-container pair must preserve the existing minimum contrast requirement. Light and dark appearances share color identity while using appearance-appropriate tones. Wallpaper use must not lower course-card text contrast.

## 9. Wallpaper visibility

The setting is named “壁纸显示强度”, ranges from 0 to 100%, defaults to 65%, and is persisted as `wallpaper_visibility_percent`.

- 0% hides the wallpaper and produces the solid timetable background.
- 100% means the maximum permitted wallpaper visibility, while retaining the light/dark safety scrim.
- Opening the wallpaper editor copies the persisted value into a transient slider candidate.
- `onValueChange` changes only that candidate and updates the bounded wallpaper preview immediately.
- `onValueChangeFinished` persists the candidate and makes it the new restoration point.
- Dismissal or cancellation before `onValueChangeFinished` discards the transient candidate and restores the persisted value in both preview and runtime state.
- Dismissal after a finished interaction keeps the newly persisted value.
- The slider is shown only when a wallpaper URI is selected.
- Clearing a wallpaper preserves the value for a later selection.
- The setting affects only the wallpaper image/scrim layer on the timetable page. It never changes course-card container or text alpha, the grid, headers, markers, or any primary content.

The wallpaper action sheet provides a bounded preview so the drag result is visible while Settings itself remains wallpaper-free.

This addendum adds exactly two preference keys: `course_palette_seed` (`Long`) and `wallpaper_visibility_percent` (bounded integer). It does not replace or reinterpret the existing appearance-mode or wallpaper-URI keys.

## 10. Horizontal time-rail width only

The fixed 44dp rail is replaced by a measured width. Measurement uses the real rendered time-label style under the current density and font scale, selects the widest measured label, and rounds the pixel result upward before converting it to the layout width:

```text
railWidth = widest rendered time label
          + 4dp left padding
          + 4dp right padding
          + at most 0.5dp divider
```

Left and right text padding are each exactly 4dp; the divider may consume at most 0.5dp outside that symmetric pair. There is no second spacer between the rail and Monday. The measured screen-edge-to-label versus label-to-Monday padding difference must be no more than 1dp. Font scale 1.3 may increase the measured rail width but must not clip the widest label.

All released width enters the seven-day grid:

```text
gridWidth = viewportWidth - railWidth
dayWidth = gridWidth / 7
```

All seven columns remain equal and weekends remain visible. Long-press X inversion continues to receive coordinates relative only to the grid body.

This is the only permitted time-rail change. No vertical time labels, group gaps, axis positions, inverse mapping, period derivation, or WeekOverview Y geometry may change.

## 11. State and component boundaries

- `SemesterConfirmEditor` owns automatic/manual end-date state and pure calculation.
- `TimetableViewModel` (or a small UI projection helper beside it) owns SCHOOL-only ghost-to-active association and stable detail-page models.
- `WeeklyTimetableGrid` renders already-projected representatives, ghost-only cards, and markers; it does not query repositories.
- `CourseDetailSheet` gains an optional pager wrapper while existing single-page content remains reusable.
- `CoursePalette` derives colors from applied seed and stable key; Settings owns candidate editing.
- `SettingsStore` adds only the applied `course_palette_seed: Long` and bounded `wallpaper_visibility_percent`; existing appearance and URI ownership is unchanged.
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

The regression suite includes, at minimum, the following named cases:

```text
recognized_end_date_starts_manual
missing_end_date_starts_auto
auto_end_date_uses_week1_monday_and_inclusive_last_sunday
opening_and_canceling_end_picker_preserves_mode
confirming_end_picker_selection_enters_manual
manual_end_date_survives_week_or_week_count_changes
restore_auto_recomputes_end_date

week_pattern_only_difference_has_no_variant_marker
presentation_signature_difference_has_variant_marker
manual_blocks_never_enter_school_aggregation
active_school_conflicts_remain_side_by_side
unattached_school_ghost_remains_gray_card
ghost_uses_maximum_overlap_then_stable_key
one_detail_page_per_stable_course_identity
representative_active_course_is_first_detail_page
same_course_variants_use_single_detail_page

default_seed_preserves_existing_palette_mapping
candidate_seed_is_not_persisted_before_apply
restore_default_requires_apply
applied_seed_persists_across_recreation

slider_preview_does_not_change_course_card_alpha
slider_finish_persists_candidate
slider_cancel_before_finish_restores_persisted_value

click_wins_when_pointer_releases_within_slop
long_press_wins_after_timeout
horizontal_drag_before_timeout_yields_to_pager
vertical_drag_before_timeout_cancels_click_and_longpress
one_vertical_drag_changes_overview_at_most_once

time_rail_uses_measured_label_width_not_fixed_44dp
time_rail_left_and_right_text_padding_are_symmetric
actual_rail_padding_difference_within_1dp
no_extra_spacer_exists_between_time_rail_and_monday
released_width_is_distributed_equally_to_seven_days
longest_time_label_is_not_clipped_at_font_scale_1_3
existing_time_boundary_positions_remain_unchanged
existing_segmented_axis_roundtrip_remains_unchanged
```

Qualification runs targeted semester, timetable layout/UI, appearance, settings, and persistence tests; then the full unit suite, lint, connected tests, debug/release builds, and emulator screenshots in light/dark and expanded/collapsed states.

## 13. Explicit non-goals

- No portal URL, selector, parser, normalizer, session, or real-network change.
- No Room schema or sync fingerprint/diff change.
- No vertical time-axis redesign.
- No change to weekly worker behavior.
- No persistence of WeekOverview expansion.
- No random color changes on startup, recomposition, sync, or week navigation.
