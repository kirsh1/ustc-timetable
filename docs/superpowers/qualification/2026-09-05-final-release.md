# J2/J3 final release qualification

## Baselines and boundaries

- Resumed execution starting HEAD: `a1cdccac4e299378368d424fbc3feb22fc610fed`.
- The original wave starting revision `1b66e5f` predates separately accepted UI/icon work; its old totals are not reused as fresh qualification.
- J2 opt-in diagnostic commit: `309b8e6`.
- J2 physical acceptance record commit: `ac6edec`.
- Physical device: Xiaomi 25019PNF3C, Android 16/API 36, serial `6a5770ce`.
- Full connected suite: disposable `dsh_android16`, API 36, serial `emulator-5554` only. AGP 9.3.0 bytecode was inspected: DeviceProviderInstrumentTestTask reads ANDROID_SERIAL and ConnectedDeviceProvider filters exact serials before execution. Every full run explicitly sets ANDROID_SERIAL=emulator-5554; output names only that AVD.
- No production, Room schema, portal, parser, sync, session storage, worker, or manual persistence changes in this qualification wave.
- User-owned untracked icon source assets retained, not added to qualification commits.

## J2 physical acceptance

See `../acceptance/2026-09-05-device-qualification.md` for all 16 checks, clear-login preservation, Keystore restart, and additional LIGHT/DARK/overview/wallpaper/fontScale smoke.

The user confirmed an intervening reimport, manual-test deletion and profile changes. Baselines were separated; no old snapshot was restored. New visible manual fixtures were then created and required by the live-sync test. NoChange under denied notification permission preserved both current manual records. Notification state, LIGHT, no wallpaper and fontScale 0.9 restored; newer user Wi-Fi-on, ghost-off and palette choices preserved.

Permission qualification uses revocation of the already-initialized grant and repeated entry, not a replayed first-request dialog. Typed notification diff verification is PHYSICAL DEVICE / TEST-ONLY CHANGE INJECTION, not a real server course mutation.

## Commands

PowerShell build environment: TEMP and TMP `C:\jtmp`, GRADLE_OPTS `-Djava.net.preferIPv4Stack=true`. Gradle commands run serially with `--max-workers=1`.

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --max-workers=1
.\gradlew.bat :app:lintDebug --rerun-tasks --max-workers=1
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --max-workers=1
.\gradlew.bat :app:assembleDebug :app:assembleRelease --rerun-tasks --max-workers=1
```

## Regression evidence and harness correction

First fresh JVM run: 1178 tests, zero failures/errors/skips. Fresh lint: zero errors, 11 warnings; no new suppression or baseline.

Initial full connected run: 35 tests, one failure in `com.ustc.timetable.ui.TimetableSmokeTest.empty_database_manual_fallback_enters_blank_seven_day_timetable`. The assertion ran immediately after Compose idle, while the production first-launch gate starts Loading and waits for Room's asynchronous first emission. Other seeded startup cases already wait for their destination. The only correction adds a bounded destination-node wait before the existing visible-screen and persistence assertions; no fixed sleep, removed assertion, or production change. Targeted full TimetableSmokeTest then passed 5/5. Fresh full rerun passed 35/35 with zero failures/errors/skips (XML time 208.025 seconds). Harness correction commit: `0daf4b5`.

The three opt-in J2 live-action methods return without network/injection in ordinary connected execution; these are not counted as fresh live-server coverage. Their selected physical executions are recorded separately in J2. The read-only audit case reports no secret values and never resets or seeds data.

Final fresh build command reran unit, lint, debug and release tasks in one serial invocation:

- Unit: 1178 tests, failures/errors/skips 0/0/0.
- Lint debug: 0 errors, 11 warnings.
- Connected: 35 tests, failures/errors/skips 0/0/0, disposable emulator only.
- Debug and release assemble: GREEN.
- Release APK: `app/build/outputs/apk/release/app-release.apk`, 14,997,560 bytes, SHA-256 `CE99C7CAABD2D8D52F173F2D4A93D28A1A85D1AECF13A871C68949316E4ADBF7`.
- Release configuration: project-defined debug signing and `isMinifyEnabled=false`; this is release sanity, not store signing qualification.
- Actual APK Dex scan: zero matches for J1/UI-R4/J2 Android test classes, runners/harnesses, `DebugSeed`, or `DebugTimetableApp`.

## Physical release smoke

The release APK installed over debug using `adb install -r` with Success; no uninstall or app-data reset.

1. Offline Room-first: with `Active default network: none`, force-stop/cold launch completed in 167 ms and rendered the local week-one timetable with `J2-release-smoke` visible and no WebView. Wi-Fi and cellular service were restored afterward.
2. Real sync/manual survival: the first release live attempt at 21:50 returned real `AuthenticationExpired`; the immediate read-only audit proved every school/manual digest unchanged and the encrypted local session blob still present. This is additional auth-expired preservation evidence, not a PASS for NoChange. The user reauthenticated through the real UI. The next explicit release live attempt returned `NoChange` and asserted every manual entity/local ID plus Android notification keys/post times unchanged.
3. Restart persistence: before/after force-stop and cold launch, both semesters, current/viewed/portal flags, session presence, school counts/digests and manual counts/digests were identical. Current school 9/15 digest `d88250...96d5a`; current manual 2 digest `1d09b4...cc93`; other school 2/4 digest `494041...bc5c`; other manual 1 digest `427c26...c12d`. The release UI again showed `J2-release-smoke` after restart.

The target's working profile identifier changed during the user's real reauthentication flow, but the paired release restart audits use the same new identifier and all schedule content digests are invariant. No profile or database write was performed by the audit.

## Status

J2 physical acceptance and J3 release sanity are complete. Final audit confirmed the documentation commit changes no `app`/`gradle` paths; the whole qualification wave changes no production/debug/build configuration; stash is empty. Git status contains only the user's pre-existing untracked `icon/` design-source directory, intentionally preserved. Physical fontScale is 0.9, Wi-Fi and both cellular subscriptions are enabled, notification permission is granted with USER_SET, and only the physical handset remains connected after closing the qualification emulator.
