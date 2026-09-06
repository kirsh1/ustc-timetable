# Exact portal time and login recovery qualification

Date: 2026-09-06

## Automated evidence

- Exact-time domain invariants, entity round-trip, and Room v1→v2 column migration: PASS.
- Standard-only fingerprint compatibility and exact-minute change detection: PASS.
- `16:10–17:50` parser fallback, canonical propagation, malformed/gap/overflow rejection: PASS.
- Effective timetable/detail projection and non-standard endpoint labels: PASS.
- Encrypted session plus WebView cookie clearing: PASS.
- Ephemeral mobile/desktop WebView settings and existing login navigation suite: PASS.
- Full `testDebugUnitTest lintDebug assembleDebug assembleRelease`: PASS (109 Gradle tasks; 2026-09-06).

## Boundary audit

- Room migration is additive and non-destructive; existing rows receive null exact-time overrides.
- Account reset clears only school session layers. Timetable, manual items, appearance, wallpaper, and sync preferences are not cleared.
- Portal URL recognition, DOM current-turn discovery, completion coordination, and HTTP source behavior are unchanged.
- Existing standard meetings retain period-only identity and legacy fingerprint bytes.

## Device evidence

The sanitized `J2ExactTimeQualificationTest` reports only database version and aggregate counts. Physical-device upgrade/import validation requires an attached authorized device; no device was visible to adb when this record was created.
