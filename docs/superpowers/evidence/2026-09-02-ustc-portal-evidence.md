# Audited sanitized USTC portal evidence

**Audit date:** 2026-09-02
**Binding requirements:** `docs/superpowers/specs/2026-08-30-ustc-timetable-design.md` §§6.1–7.4 and §13
**Status:** evidence-audited for the documented portal surfaces. This record releases the §13 evidence gate for the staged implementation work; it does **not** claim real-device, real-login, or production-runtime qualification.

## 2026-09-03 current-turn discovery supplement

The reviewed sanitized supplement establishes `/for-std/course-select` as the
stable authenticated discovery surface. The portal home loads that path in
iframe `e-home-iframe-1`; the loaded page has route shape
`/for-std/course-select/turns/<STUDENT_ID>` and exactly one observed current-turn
anchor with visible text `进入选课`, classes `btn btn-primary`, parent
`div.col-sm-3.text-center`, and path shape
`/for-std/course-select/<STUDENT_ID>/turn/<TURN_ID>/select`. This resolves the
runtime request-context gate: production may parse typed student/turn identifiers
from that unique path, but must reject missing, malformed, or ambiguous matches.

The supplement also corroborates `button.my-course-table` and the page-local
`.course-table-modal` containing `#time-table`/`.time-table`; opening it does not
navigate. It does not establish multiple-current-turn policy, session lifetime,
kick-out behavior, or a low-cost probe guarantee. No supplemental evidence file
is copied into the parser fixture set.

## Audit boundary and security result

The audit read every file in the sanitized evidence set, including all six supplemental static assets. The set contains sanitized page/response material only. A combined scan for private keys, common cloud keys, bearer/basic values, authorization headers, Cookie/Set-Cookie headers, unredacted ticket/token query values, and assigned credential values found no reusable secret. The two lexical matches were reviewed as third-party UI-library strings (`COOKIE` date-format label and a password-input template), not credentials or headers.

The starting repository revision was clean, had no stash entries, and matched the approved starting revision. The tracked-file audit found only implementation/test filenames containing the word “Cookie”; it found no tracked raw capture, `1.html`, CAS ticket, Cookie header material, or Authorization material.

## SPEC §13 evidence mapping

| §13 row | Audited evidence and ruling |
|---|---|
| 1. Logged-in selection/timetable URLs | `01-selection-url.txt` and `04-timetable-url.txt` preserve both sanitized routes. `13-current-turn-discovery.md` additionally establishes the stable discovery path and unique current-turn link shape from which typed student/turn identifiers can be parsed. Redacted identifiers are never restored. |
| 2. Sanitized page contents | `02-selection-dom.html`, `03-selection-source.html`, `05-timetable-dom.html`, and `06-timetable-source.html` are complete sanitized captures for the two observed surfaces and their source/runtime forms. |
| 3. CAS URLs, redirects, and success features | `07-login-url-and-redirects.md` documents the CAS service-ticket chain, the stable participating hosts, and the return to the portal host. `09-login-expired-dom.html` supplies the login-page structure, including allowed field names and captcha/password processing, with no values. |
| 4. Four XHR URLs/methods/responses | `10-network-data-notes.md` records four same-origin POSTs and their trigger. The four `xhr/response-*.json` files preserve the sanitized response shapes; the static scripts prove the request body builders. |
| 5. Source versus runtime DOM | The selection source and runtime DOM both contain populated selection data. The timetable source has only the Modal/container; its runtime DOM contains the constructed timetable. HTTP must therefore be attempted first, with a DOM fallback required when the timetable response is source-only. |
| 6. Week behavior | `11-week-switch-behavior.md` and the timetable runtime DOM show a full-term Modal with per-meeting week ranges, no observed independent week switch, no navigation on open/close, and no observed request-parameter variation. A rendered maximum range is not semester metadata. |
| 7. Expired URL/HTML | `08-login-expired-url.txt` records the sanitized final redirect target and `09-login-expired-dom.html` records the resulting portal login page. These form the `LoginPageDetector` evidence. |
| 8. Session TTL/kick-out | `12-session-behavior.md` explicitly marks persistence, TTL, logout, and same-account kick-out as unobserved. They remain unknown; no timeout, cross-device, or kick-out behavior may be inferred. |
| 9. Low-cost probe URL | The selection-result route is the Spec §7.1 default candidate and is authenticated/redirect-detectable in the captured evidence. No response-size, latency, or stability measurement proves it is low-cost, so retain it only as the documented initial candidate and qualify it during runtime work. |

## Authoritative payload rulings

There is no field-level “first non-empty wins” policy. Each canonical concern has one authority, and absence is represented as absence rather than filled from a different surface.

| Canonical concern | Authority | Non-authoritative material and rule |
|---|---|---|
| Selection course basics | `xhr/response-02-selected-lessons.json` | The selection source/runtime tables corroborate server-returned selection data and remain parser fixtures; do not merge their display cells over the XHR course fields. |
| Meetings | `xhr/response-03-datum.json`: `scheduleList`, joined to `lessonList` and `scheduleGroupList` by their provided linking fields | The timetable runtime DOM is a rendered regression/fallback fixture; it may validate visible rendering but is not a competing meeting authority. The timetable source is only the Modal shell. |
| Semester metadata | No authority exists in this evidence set | `displayName`, `academicYear`, `term`, `week1Start`, `totalWeeks`, `startDate`, and `endDate` are all unproven. Do not derive `totalWeeks` from a meeting range or infer dates from timetable rendering; return an unconfident partial and use the confirmation path. |

The static evidence fixes the four POST mappings and body construction:

| POST route | Body format and fields | Builder evidence | Response authority |
|---|---|---|---|
| `/ws/schedule-table/timetable-layout` | JSON object with `timeTableLayoutId` | `static/course-select-by-graduate.js` | `xhr/response-01-timetable-layout.json` |
| `/ws/for-std/course-select/selected-lessons` | Default jQuery form encoding with `studentId` and `turnId` | `static/course-select-by-graduate.js` | `xhr/response-02-selected-lessons.json` |
| `/ws/schedule-table/datum` | JSON object with `lessonIds` and `studentId` | `static/course-select-by-graduate.js` | `xhr/response-03-datum.json` |
| `/ws/schedule-table/week-indices-digest` | JSON array of objects containing `weekIndicesGroupId` and `weekIndices` | `static/schedule-table-cards.js`, `static/schedule-table-cards-service.js`, and `static/schedule-table-card-model.js` | `xhr/response-04-week-indices-digest.json` |

`static/schedule-table-main.js` establishes that the returned lessons, schedules, schedule groups, and layout flow into the timetable construction; `static/eams-ui.min.js` is a supplemental anonymously fetched UI dependency and establishes no payload authority. All static asset hashes match the set’s sanitized README.

## Phase 1 fixture intake — copy exactly this set

Phase 0 copies no fixtures. Before writing Phase 1 parser/source tests, copy exactly the following sanitized files into the repository fixture location and preserve their names. Do not copy any other evidence artifact unless a later evidence record amends this list.

1. `03-selection-source.html`
2. `05-timetable-dom.html`
3. `06-timetable-source.html`
4. `09-login-expired-dom.html`
5. `xhr/response-01-timetable-layout.json`
6. `xhr/response-02-selected-lessons.json`
7. `xhr/response-03-datum.json`
8. `xhr/response-04-week-indices-digest.json`
9. `static/course-select-by-graduate.js`
10. `static/schedule-table-main.js`
11. `static/schedule-table-cards.js`
12. `static/schedule-table-cards-service.js`
13. `static/schedule-table-card-model.js`

The selection runtime DOM is redundant with the source for populated selection rows; the login/URL/session/week notes and generic UI bundle remain audit evidence, not parser fixtures. The static scripts are retained only as body-builder provenance; tests must not execute them against live services.

## File-by-file evidence inventory

All paths below are sanitized-relative paths. SHA-256 values identify the reviewed file; purpose/support never permits reconstruction of redacted values.

| Sanitized relative path | SHA-256 | Purpose and supported fields/selectors/endpoints | Limitations |
|---|---|---|---|
| `README.md` | `339BC1AA78BAD833A814936FFBB0B31455BA33B344233EDD79B31D2BC569713E` | Updated collection manifest, sanitization policy, static-asset provenance, and current-turn discovery summary | Narrative only; no runtime qualification or request payload capture |
| `01-selection-url.txt` | `57E08629C2B44465215F2E1108F1112C6FEB7EAD289593FC309EB6C2AEC516B0` | Sanitized logged-in selection-result route | Redacted identifier; no reachability/performance result |
| `02-selection-dom.html` | `9EDD7C4F5289A3CC00DDFAB540225C84B26BEB366FBC3978DB885BBB54342E43` | Selection runtime DOM; populated result-table content and source/runtime comparison | Runtime snapshot only; redundant for Phase 1 fixture intake |
| `03-selection-source.html` | `717A264AC489301874349AB13281D2E70BA4F0FE816D2DA4F91105F244D9879D` | Selection source HTML; server-returned selection surface, Modal bootstrap configuration, selected-lessons route binding | No independent semester metadata authority |
| `04-timetable-url.txt` | `F3E486EF0621E03AA751D0E6C5989CAF415DCC962E978EEFCFDFD54C7B4092A9` | Sanitized logged-in course-selection/timetable-opening route | Redacted identifier; Modal opening is not a navigated timetable URL |
| `05-timetable-dom.html` | `05C32355CC46430BED1232D10EE542DCDFAA54BEAC7C8DA625F64501F61E351D` | Runtime Modal selectors `.course-table-modal`, `.course-table-block`, `.time-table`, `#time-table`, rendered weekday/period/card/week-range structure | Post-JS render; not a source-HTML meeting authority or semester-metadata source |
| `06-timetable-source.html` | `ADCA73BF0E46AF807EF92E053C725DFBC9029E6300DABB1A034BB7FA0664DFB3` | Source Modal shell/selectors and client bootstrap configuration | Does not contain constructed `.time-table` data; proves DOM fallback need when HTTP sees this shape |
| `07-login-url-and-redirects.md` | `37863D1F8FA81C6374E610869CB4120F5DEFDDC1789495C39FEA798D6F9A8DA1` | CAS service-ticket redirect sequence, participating hosts, portal-host success feature | No raw ticket, cookies, request bodies, or session lifetime result |
| `08-login-expired-url.txt` | `94501D9550CF6EDA7F6ABEF2CCB8FFBDAF9F88A65118171CD8B9A2711ADF4D2A` | Sanitized unauthenticated final login redirect shape | No response headers or redirect timings |
| `09-login-expired-dom.html` | `C6E69B5EB1143E8D3FE67B33ACA4E2A56C15DB62768A5D0314796A1A623CA67C` | Login detector fixture; portal login selectors, unified-login link, captcha/password-processing field names | No credential values; not proof of successful login or CAS session persistence |
| `10-network-data-notes.md` | `7F8EFEA20FB52630845ABA044A14108BEC1349935156ABE49C92249E549BBA0D` | Trigger, POST methods, routes, status/content type, and response-fixture mapping for four XHRs | No request headers, bodies, HAR, or cURL; static builders supply body evidence |
| `11-week-switch-behavior.md` | `D6EE54B2B7DCADE229460A345C52E344A266BFC4E656858CD9BE4AD9C312C31D` | Modal/no-navigation behavior and observed week-range presentation | Independent week control, changed parameters, default week, and calendar metadata are unobserved/not applicable |
| `12-session-behavior.md` | `06F6C44D1440BFB700DEF47DB1B69FA111F74D61825638FC694543B62D8C9687` | Explicitly records session-behavior unknowns | Supplies no TTL, persistence, logout, kick-out, or low-cost-probe proof |
| `13-current-turn-discovery.md` | `104D30407D684D034E35E6085381A4B50D8BC7CCDA26499A66DC2284DD578BE3` | Stable authenticated discovery path, iframe name, unique current-turn link structure/path, and timetable Modal trigger/selectors | Current visible turn only; no ambiguity policy beyond fail-closed, session lifetime, kick-out, or low-cost-probe proof |
| `xhr/response-01-timetable-layout.json` | `32FDD40785054A0A5851D1F9C0D21AB153F4479818683DD41A1F2078FA4A6B44` | Layout response: course-unit labels/order/time bounds used to construct period layout | Layout response only; no meetings or semester dates |
| `xhr/response-02-selected-lessons.json` | `DF3CE391205CD4898AE2FCAA0C7B30CA98FC326CBE3BA4E1973C4E724E231E30` | Authoritative selection basics: lesson/course codes/names, credits, department/type, teacher summary, textual time/week/place | Does not authoritatively supply structured meeting assignments or semester metadata |
| `xhr/response-03-datum.json` | `EA205C7C7215F9B29BC7D3ADF483B859FFEA766D183D70228B423260620BF3C2` | Authoritative structured meetings: lesson, schedule, group links; weekday, period/unit, time, room, teacher, week-index fields | No semester dates/display metadata; redacted/synthetic identifiers must not be treated as real values |
| `xhr/response-04-week-indices-digest.json` | `40532B9C7E5062C34FB2BD32A91AAC10D8350C2ED2058DB4AE29665E593CF760` | Server digest of grouped week-index arrays for rendered-card text | Presentation digest only; no independent total-week authority |
| `static/course-select-by-graduate.js` | `256B14565B7B183155F6E51CA9C525CF476AA551D9D5F2A4D86BFEFA0E886088` | Modal trigger; `timetable-layout`, selected-lessons, and datum POST builders; response-to-timetable data flow | Static provenance, not a fixture of user data or live request success |
| `static/eams-ui.min.js` | `63554C7CA4BE94E1A909B41DAEE5665CBC732094DC0C414E727EEA05FF3D04FE` | Supplemental anonymous UI dependency referenced by runtime page | Generic/minified dependency; no portal payload, selector, or credential authority |
| `static/schedule-table-main.js` | `6AF54BF48F3DEEDAE6B265896F2E4C5FD1CD2C4FD84613798E56D00FC82ACFBC` | Timetable construction from layout, lessons, schedules, groups, and weekday list | Does not itself issue the four captured requests |
| `static/schedule-table-cards.js` | `8D3FA8F7B2646470C9995DF59744F550C16F10B05DA840B304C3AE14ED72E1E5` | Builds `weekIndices-digest` array elements from card week indexes | No standalone route or response authority |
| `static/schedule-table-cards-service.js` | `98DE3E6FA0288BCC268188B44933D46BD6856D4E20247A621C362BF7B17ADF46` | POSTs JSON week-index digest and returns its result mapping | Static provenance only; no network-header evidence |
| `static/schedule-table-card-model.js` | `8FCDE8B35004FEA5FCDD9AF9EBFAC6406169150CFC40957E3950AAB610B6D25D` | Collects per-card week indexes and constructs digest request elements | Model-level provenance only; no semester total or source-of-truth override |

## Implementation constraints carried forward

- Retain the Spec §7.1 probe candidate until measured; do not label it low-cost based solely on this audit.
- Keep the documented session unknowns explicit in UX and sync policy.
- Discover typed student/turn identifiers only from the unique evidenced current-turn link; reject absent, malformed, or ambiguous matches.
- Keep the three authoritative payload domains disjoint; absence remains absence, and metadata must reach the existing confirmation path.
- Treat source-only timetable HTML as the trigger for the existing `WebViewDomSource` fallback decision, not as an empty timetable.
- Phase 1 must copy only the listed sanitized fixtures before parser/source tests; this Phase 0 commit deliberately contains documentation only.
