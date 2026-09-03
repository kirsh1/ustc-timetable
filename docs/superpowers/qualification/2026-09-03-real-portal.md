# Real USTC portal qualification — 2026-09-03

**Starting revision:** `b20772dd2842bed2d3333089e3fbf5705599319f`  
**Environment:** Windows 11, JDK 21.0.12.1, Gradle 9.5.0, AGP 9.3.0, Kotlin 2.4.10, compileSdk 37, targetSdk 36, minSdk 26  
**Device:** `dsh_android16`, `emulator-5554`, API 36, x86_64  
**Scope:** sanitized-evidence parsers, CAS/WebView completion, four-request portal source, runtime composition, real import/manual sync/reauth, and regression qualification. J2/J3 remain separate gates.

## Evidence and security boundary

- The sanitized evidence directory contained 24 files. Six lexical secret-scan hits were confined to the reviewed login-page/UI-library fixtures and represented field names, redacted/documentation text, or third-party UI strings rather than reusable credentials.
- Sixteen selected files were copied into `app/src/test/resources/fixtures/ustc`; the complete raw capture directory was never copied or committed.
- The tracked-tree scan found no raw evidence file, CAS ticket, Cookie/Set-Cookie/Authorization header material, password handling, production response-body logging, or fake portal implementation in `main`/`debug`.
- The user entered credentials directly in the WebView. No credential, Cookie value, ticket value, request header dump, HAR, or cURL was captured by the qualification process.
- Session TTL, cross-device kick-out, and probe-cost claims remain explicitly unknown. Runtime authentication decisions use observed redirect/page detection rather than a guessed lifetime.

## Implemented portal path

- The selection-course basics are parsed from the evidenced selected-lessons response.
- Meetings are parsed from the evidenced timetable datum response and its lesson/schedule/group relations. Week-digest data remains presentation support rather than semester-total authority.
- Semester metadata remains incomplete in the observed portal payload and therefore correctly enters the existing confirmation sheet instead of inferring dates or total weeks from meeting ranges.
- The WebView bootstraps the stable student-selection module, discovers exactly one current-turn link by its evidenced route/structure, and rejects absent, malformed, ambiguous, or stale navigation-derived candidates.
- After verified navigation, the runtime captures endpoint-scoped Cookie request headers into the encrypted session store without persisting CAS ticket URLs or WebView history.
- The HTTP source issues the four evidenced requests with their evidenced methods/body shapes and feeds the existing normalizer and `SyncEngine`; no duplicate synchronization pipeline was introduced.
- `AppContainer` owns one real portal runtime. `TimetableApp` supplies the real worker factory and asynchronously reconciles the seven-day, initially delayed, unique periodic work.
- `SyncExecutionRecorder` records `lastSyncFinishedAt` only when an eligible portal target actually attempted the portal, shared by manual and background execution.

## Real-device evidence

On a true empty database, the release build opened the login/import path. After the user completed CAS authentication, the app automatically traversed the stable portal module/current-turn path, fetched the four real endpoints, and returned to the semester confirmation sheet. The confirmed semester was imported atomically and rendered a portal-linked timetable.

The non-sensitive database audit immediately after import found one semester, nine school courses, fifteen meetings, zero orphan meetings, complete sync metadata, and no manual item. Weekday and period bounds were valid. A release cold start reopened directly into the persisted timetable without displaying the WebView.

A manual item was then created through the timetable editor. Real manual refresh completed silently on unchanged school data and preserved both the school snapshot and the manual item; the post-refresh audit remained nine courses, fifteen meetings, one manual item, and zero orphan meetings.

The reauthentication path was qualified by clearing only the app's encrypted local session while retaining the already authenticated WebView cookie state. Refresh kept the existing grid visible and opened the authentication-expired dialog; successful WebView return closed the dialog and retried exactly once. This is a local-session-missing orchestration qualification, not evidence of a server-expired CAS cookie.

Opening Settings after timetable-owned reauthentication exposed one real lifecycle defect: the activity-scoped Settings view model retained its earlier `NotLoggedIn` snapshot. A regression test first reproduced the stale state, then `onSyncSectionEntered()` was minimally changed to refresh the session before permission-policy evaluation. The Settings package passed 38/38, and the updated release build displayed the restored logged-in state on entry.

## Automated qualification

| Gate | Result |
|---|---|
| Portal boundary (`school.ustc.*`) | 241 tests, 0 failures/errors/skips |
| Sync + semester | 161 tests, 0 failures/errors/skips |
| Full unit, serial | 991 tests, 0 failures/errors/skips |
| Full unit, default workers | 991 tests, 0 failures/errors/skips |
| Lint debug | 0 errors, 12 warnings |
| Connected debug Android tests | 14 tests, 0 failures, 0 skips |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`, 14,764,397 bytes |
| Release APK | `app/build/outputs/apk/release/app-release.apk`, 10,632,555 bytes |

Lint warning IDs were: `AndroidGradlePluginVersion` (2), `ComposableNaming` (2), `DataExtractionRules` (1), `GradleDependency` (1), `InlinedApi` (1), `MissingApplicationIcon` (1), `OldTargetApi` (1), `SetJavaScriptEnabled` (1), and `UseKtx` (2). None was an error or introduced a baseline/suppression.

The connected test runner uninstalls the target APK after execution. A private-cache-only pre-test snapshot was therefore removed with the package and could not restore the just-imported emulator state. This affected only disposable emulator state, not repository code or qualification results; the release APK was reinstalled and final real-state restoration required repeating the import after the automated gate. Future runs that must preserve state must export an encrypted/local-only snapshot outside the package before invoking `connectedDebugAndroidTest`.

The repeated import completed successfully. The user intentionally used the short display name `q` for this disposable test semester. The final non-sensitive audit found one portal-linked academic-current semester, nine courses, fifteen meetings, zero manual items, zero orphan meetings, and a present encrypted session file. A subsequent release cold start entered `MainActivity` directly, and Settings showed the refreshed logged-in state. The Android notification permission dialog was left for the user to decide.

## Deferred UX follow-up

The confirmation UI currently asks for both the first-week/start date and end date. A later UX task should derive the inclusive semester end automatically as:

```text
endInclusive = week1Start + totalWeeks weeks - 1 day
```

For a Monday first-week start and 20 teaching weeks, this yields the Sunday ending the twentieth week. The field may remain editable if the product needs calendar exceptions. This qualification wave records the request but does not change confirmation semantics or UI.

## Gate status

- Sanitized portal evidence: audited; all nine rows are mapped, with the documented unknowns preserved.
- Real parser/source/runtime composition: qualified.
- Real login/import, cold-start persistence, unchanged manual sync, and local-session reauthentication orchestration: qualified on API 36.
- Server-expired-session behavior, full J2 16-item device acceptance, and J3 release sanity: not claimed here.
