# Sanitized USTC portal fixtures

These fixtures are the reviewed, sanitized artifacts authorized by
`docs/superpowers/evidence/2026-09-02-ustc-portal-evidence.md` for parser and
later portal-boundary tests. Identifiers and personal data are synthetic or
redacted. The files contain no reusable authentication material.

The SHA-256 values below are the original values recorded during the evidence
audit and are preserved here so fixture provenance can be verified without
retaining any raw capture location.

| Fixture | Original SHA-256 |
|---|---|
| `03-selection-source.html` | `717A264AC489301874349AB13281D2E70BA4F0FE816D2DA4F91105F244D9879D` |
| `05-timetable-dom.html` | `05C32355CC46430BED1232D10EE542DCDFAA54BEAC7C8DA625F64501F61E351D` |
| `06-timetable-source.html` | `ADCA73BF0E46AF807EF92E053C725DFBC9029E6300DABB1A034BB7FA0664DFB3` |
| `09-login-expired-dom.html` | `C6E69B5EB1143E8D3FE67B33ACA4E2A56C15DB62768A5D0314796A1A623CA67C` |
| `xhr/response-01-timetable-layout.json` | `32FDD40785054A0A5851D1F9C0D21AB153F4479818683DD41A1F2078FA4A6B44` |
| `xhr/response-02-selected-lessons.json` | `DF3CE391205CD4898AE2FCAA0C7B30CA98FC326CBE3BA4E1973C4E724E231E30` |
| `xhr/response-03-datum.json` | `EA205C7C7215F9B29BC7D3ADF483B859FFEA766D183D70228B423260620BF3C2` |
| `xhr/response-04-week-indices-digest.json` | `40532B9C7E5062C34FB2BD32A91AAC10D8350C2ED2058DB4AE29665E593CF760` |
| `static/course-select-by-graduate.js` | `256B14565B7B183155F6E51CA9C525CF476AA551D9D5F2A4D86BFEFA0E886088` |
| `static/schedule-table-main.js` | `6AF54BF48F3DEEDAE6B265896F2E4C5FD1CD2C4FD84613798E56D00FC82ACFBC` |
| `static/schedule-table-cards.js` | `8D3FA8F7B2646470C9995DF59744F550C16F10B05DA840B304C3AE14ED72E1E5` |
| `static/schedule-table-cards-service.js` | `98DE3E6FA0288BCC268188B44933D46BD6856D4E20247A621C362BF7B17ADF46` |
| `static/schedule-table-card-model.js` | `8FCDE8B35004FEA5FCDD9AF9EBFAC6406169150CFC40957E3950AAB610B6D25D` |

Authority remains deliberately disjoint: selected-lessons JSON supplies course
basics, datum JSON plus its evidenced links supplies meetings, and this fixture
set supplies no semantic semester metadata.

## Current-turn discovery provenance

The 2026-09-03 sanitized evidence supplement is retained outside the fixture
tree and is not copied into tests. Its reviewed provenance is recorded here so
synthetic discovery HTML can be traced without persisting any observed
identifier or response body:

| Evidence | SHA-256 |
|---|---|
| `README.md` | `339BC1AA78BAD833A814936FFBB0B31455BA33B344233EDD79B31D2BC569713E` |
| `13-current-turn-discovery.md` | `104D30407D684D034E35E6085381A4B50D8BC7CCDA26499A66DC2284DD578BE3` |

Supported structural facts are limited to stable authenticated path
`/for-std/course-select`, iframe `e-home-iframe-1`, one observed `进入选课`
primary anchor under `div.col-sm-3.text-center`, its redacted student/turn path
shape, and the existing `button.my-course-table`/`.course-table-modal` trigger.
Tests use synthetic positive identifiers and the implementation fails closed
for missing, malformed, or ambiguous current-turn links.
