# J2 physical-device qualification — in progress

## Execution baseline

- Resumed 2026-09-05 after the user's accepted UI work; starting HEAD: `a1cdccac4e299378368d424fbc3feb22fc610fed`.
- The historical wave's `1b66e5f` is not the current app tree. Old automated totals are not claimed as fresh J2/J3 results.
- Physical device: Xiaomi 25019PNF3C, Android 16 / API 36, 1080×2400, density 450. ADB serial `6a5770ce`.
- Debug APK installed with `adb install -r`; no uninstall, database reset, backup export, credential input or session export.
- Required portal commits `f4ad8c7`, `4c4beec`, `c79fbf9`: ancestors of HEAD. Existing real runtime retained.
- Initial stash empty. Remaining untracked `icon/` source assets are user-owned and intentionally preserved.
- Original physical font scale: 0.9. Temporarily set to 1.0 for qualification; restore at the end.
- Original Wi-Fi off; both cellular subscription data switches on. Disabled for offline test, then restored with `svc data enable`.

## Evidence method

`J2ReadOnlyDeviceAuditTest.report_non_secret_persistence_state` is an explicitly selected androidTest-only diagnostic. It does not reset or seed data, send network requests, mutate settings, or export session contents. It reports counts, schedule masks, SHA-256 summaries and the boolean result of `hasSession()`.

No general emulator fixture suite is run on the user's physical database. A successful diagnostic invocation is not by itself a PASS for the checklist.

## Checklist

| Item | Status | Current evidence / remaining work |
|---|---|---|
| 01 Offline Room-first cold start | PASS | Connectivity reported `Active default network: none`; force-stop → launch COLD, TotalTime 443 ms; real timetable and manual item visible without WebView. |
| 02 Seven days | PASS | fontScale 1.0: Monday through Sunday visible simultaneously. |
| 03 Week 1 | PASS | Current semester week1Start 2026-08-31; UI Monday 8-31, Sunday 9-06. |
| 04 13-period profile | PASS | Actual profile editor viewed without saving: periods 1–13 and each requested sample match official times; final period 21:10–21:55. |
| 05 Saturday one-off manual | PASS | Blank Saturday long-press created `J2-qualification`, 14:55–15:40, TH-B301, week 1. Tap opened detail; vector pencil opened editor; saved custom-week edit returned to detail. Existing user-created J2 items preserved. |
| 06 Weeks 3–12 | PASS | Existing repeat item mask 4092; physical week 2/13 has no active repeat card, week 3/12 has active repeat card. Enabled ghost setting still shows non-current representations, per accepted UI behavior. |
| 07 Custom weeks | PASS | Actual editor selected 2–6,8,10–12; saved detail shows exact set. Physical pages 2,6,8,10,12 active; 7,9,13 ghost-only. |
| 08 School/manual coexistence | PASS | New-baseline week 2 Tuesday: SCHOOL 15:55–18:20 and `J2-overlap` 16:10–16:55 retain both entities and form the accepted partial-overlap puzzle. Manual sub-card opens its own page with pencil; horizontal swipe opens the school detail without an edit action. |
| 09 Real sync preserves manual | PASS | User reauthenticated 18:52. Explicit live probe returned `NoChange`; exact manual entities including IDs unchanged across both semesters. Initial four digests also unchanged after UI sync. |
| 10 Auth-expired preserves local data | PASS | Actual production Settings → immediate sync returned login-expired dialog. Both semesters' school/manual counts and SHA-256 summaries unchanged. No forged Cookie or injected expiry. |
| 11 History zero web | PASS | Test-only listener on existing runtime HTTP client: actual resumed MainActivity browsing non-current semester generated 0 HTTP calls. Observer restored in finally; no URLs/headers logged. |
| 12 Real NoChange is silent | PASS | Explicit production syncRecorder call returned real `NoChange`; active app notification keys/post times unchanged. No server mutation or fake result. |
| 13 Physical diff notification | PASS | TEST-ONLY CHANGE INJECTION: typed LocationChanged, week 10, TH-B301 → TH-C204 passed through production SyncNotification/ChangeFormatter. Actual Android notification extras asserted exact diff; visible notification verified in physical shade. Unrelated personal notifications prevent retaining raw shade screenshot in Git. |
| 14 Restart persistence | PASS | Both semester identities, profile bindings, viewed/current flags, school and manual summaries identical before/after offline force-stop/relaunch. |
| 15 Academic-current isolation | PASS | Physical switch/restart retained distinct viewed/current flags. Real BackgroundSyncRunner issued 4 HTTP calls, returned NoChange, kept viewed selection and all snapshot entities unchanged. |
| 16 Notification denial | PASS (existing permission state) | Temporarily revoked permission; settings displayed denial hint and retained weekly sync. Repeated cold launch/settings entry did not prompt again. At 20:45 explicit live sync returned NoChange and preserved both current manuals. Permission and USER_SET flag restored. A fresh first-request system-dialog rejection was not replayed on the user's already-initialized permission state. |

## Keystore/session and data snapshots

Initial physical audit: two semesters; current portal-linked semester contains 9 courses / 15 meetings / 2 manual items. Other semester contains 2 courses / 4 meetings / 2 manual items.

Current-school summary: `32f64ab51382aafe4b9e875504be97edde627ceb0af59135d7e56dbd81398a54`.

Current-manual summary: `1dabcdf98a3147a0c90e5d482c0a9a3aa8aeb232b076604c24d45e21a47963ab`.

Other-school summary: `4940414a58c21a2ea6c158ab3dfcd3902b84fbf00029778fac08f5ec8c6cbc5c`.

Other-manual summary: `dc1e01e17328087b1c8dc09f97a9d1a339f9f35f58f04aa807b4961436581db3`.

These four summaries matched after offline restart and after the actual AuthenticationExpired result at 18:51. `sessionPresent=true` before/after restart proves encrypted storage is readable, not that the server still accepts the session. Clear-login smoke subsequently passed against the newer user-confirmed baseline below. No new Portal Evidence requested.

## Gate status

### Interrupted-run baseline change, 20:10

After the user's later continuation, a fresh audit found a different current-semester identity (hash `76aea1fc0633702a6e649ab95cca84626b6c850ed3fb2c675619a7c54385e010`), zero current manual items, and one remaining other-semester manual item. The current login UI timestamp had also changed to 19:35. The user explicitly answered yes to having reimported/deleted test items/changed the profile. This is a user-originated baseline change, not evidence of a production data-loss bug. Earlier PASS observations refer to their recorded earlier state.

The latest NoChange probe preserved its before/after entities but had no current manual item. It is not counted as current-target manual-survival evidence. The probe now explicitly requires a nonempty manual list in the actual current sync target to prevent that vacuous assertion.

Notification permission restored to originally granted; font scale restored to original 0.9. User's later Wi-Fi-on and ghost-off selections are preserved. No current-semester creation/deletion, profile rebind, or user-item deletion was performed by this qualification wave.

History probe harness note: ActivityScenario.launch stalled on this physical device before activity launch; explicitly stopped test process, then used a shell launch with actual RESUMED lifecycle assertion. Fresh run passed with launch calls 0 and explicit background HTTP calls 4. No production change.

### Current-target requalification and clear-login

Visibly created `J2-release-smoke` (Saturday 14:55–15:40, week 1) and `J2-overlap` (Tuesday 16:10–16:55, week 2). The live probe with the nonempty-target guard passed after creation and again after login restoration under denied notification permission.

Clear-login UI changed to 未登录; the read-only probe reported `sessionPresent=false`. Both semester identities and all four school/manual content digests matched the before-clear baseline:

- Current school: `d88250fec4dd033ce9f8214d8d8525b2a3d02d0d92783af800de134f00d96d5a` (9/15).
- Current manual: `1d09b41f01fd3e13816be08f5ecc7b98b2fa4079d673e347b3e3ea141d61cc93` (2).
- Other school: `4940414a58c21a2ea6c158ab3dfcd3902b84fbf00029778fac08f5ec8c6cbc5c` (2/4).
- Other manual: `427c26aba3a2e85a9a01a4884d42a73d30d5c2d45bc5c16cc9e92f117ec0c12d` (1).

The user then completed real authentication at 20:34, without reimport. Settings after cold restart showed logged in; subsequent real NoChange passed. No session secret was accessed or exported.

### Additional physical visual smoke

LIGHT and DARK system/navigation glyphs remained readable. fontScale 1.3 retained seven days and the full time rail; after configuration recreation, explicitly expanded WeekOverview and confirmed 21:55 remained visible. Collapsed view also retained 21:55. User's custom system font was retained. A temporary qualification screenshot selected through the system photo picker rendered as wallpaper under the dark scrim, with opaque course cards; the strength sheet showed 65%. Cleared the test wallpaper and restored LIGHT, fontScale 0.9, and granted notifications. User's newer Wi-Fi-on, ghost-off and palette choices retained.

J2 physical acceptance: PASS with the documented existing-permission-state deviation for item 16. J3 fresh automation and release smoke remain required. No production changes in this resumed qualification wave.
