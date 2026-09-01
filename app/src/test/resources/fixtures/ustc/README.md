# USTC parser fixtures

This directory is the sanitized parser-evidence boundary. The checked-in
`sample_minimal.html` file is synthetic and tests only resource loading; it is
not portal evidence and contains no real selector assumptions.

The following full, post-login HTML captures are required later and are
currently **NOT PRESENT / PENDING USER EVIDENCE**:

- `course_selection.html`
- `timetable.html`
- `login_page.html`
- `auth_expired.html`

Before adding a captured fixture:

- Preserve the complete DOM structure, element IDs/classes, field labels,
  course/period/week structure, and other selector evidence.
- Replace names with stable placeholders such as `学生A` and `教师A`.
- Replace student numbers with `PB00000000`; replace phone numbers, email
  addresses, identity numbers, and all other personal data with stable `X_*`
  placeholders.
- Never commit usernames, passwords, raw `Cookie` or `SessionBlob` data,
  `session.bin`, authorization/bearer values, session IDs, account-specific
  CSRF values, or secret query values.
- Preserve URL scheme, host, port, path, query parameter names, and non-secret
  functional values. Replace only a secret query value with
  `<REDACTED_SECRET>` and note what kind of evidence was redacted.
- Do not alter DOM IDs, classes, or structural selector evidence merely to
  sanitize content.
- Human-inspect every real fixture and inspect its staged git diff before
  committing it.
