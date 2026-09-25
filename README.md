# Airline Exam Preparation Bot

One Java 21 / Spring Boot application for Telegram exam preparation and a
Thymeleaf admin website. [PROJECT_SPEC.md](PROJECT_SPEC.md) defines the product rules.

## Current scope: compressed Phase 5

- Private-chat registration: /start → English or Amharic → active exam type →
  share your own Telegram contact → registration and free entitlement.
- PostgreSQL registration state survives restarts. Repeated commands, callbacks,
  and same-user contacts do not recreate accounts or entitlements.
- Free snapshots initially grant 100 unique practice questions, 2 mock exams,
  and 50 questions per mock, without expiration. These are independent allowances.
- Admin login, dashboard, settings, exam types, and categories.
- Administrative changes retain actor, timestamp, and before/after values.
- Question bank, immutable published versions, lifecycle review, source rights,
  pool eligibility, and staged CSV/XLSX imports.
- Answering, quota consumption, mocks, payments, and deployment are not implemented.

## Requirements

JDK 21, Maven Wrapper (Maven 3.9.16), PostgreSQL 18. Spring Boot 3.5.16 manages
dependencies; Flyway core and the PostgreSQL module are aligned at 11.20.3.
H2 and Spring Security test support are test-only dependencies.

## Configuration

Supply environment variables to the process starting the application. Neither
Spring Boot nor the wrapper automatically reads .env. Real .env files are ignored;
.env.example contains placeholders only.

| Variable | Default / requirement |
| --- | --- |
| DB_HOST | 127.0.0.1 |
| DB_PORT | 5432; configurable |
| DB_NAME | airline_exam_bot |
| DB_USERNAME | airline_exam_user |
| DB_PASSWORD | Required for normal PostgreSQL startup |
| SERVER_PORT | 8080; use 8081 temporarily if occupied |
| TELEGRAM_BOT_ENABLED | false |
| TELEGRAM_BOT_TOKEN | Required only when Telegram is enabled |
| PHONE_IDENTITY_HMAC_KEY | Base64 encoding of at least 32 random bytes; required when Telegram is enabled |
| ADMIN_BOOTSTRAP_USERNAME | Optional initial admin username, 3–64 letters/numbers/underscore/dot/hyphen |
| ADMIN_BOOTSTRAP_PASSWORD | Optional initial strong password, at least 16 characters and at most 72 UTF-8 bytes |

Never put actual values into source, SQL, documentation, command arguments, logs,
or tracked files. Read passwords/tokens through secure input, then set process
environment variables:

```powershell
$dbPasswordInput = Read-Host 'Local PostgreSQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPasswordInput).Password
Remove-Variable dbPasswordInput
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:DB_NAME = 'airline_exam_bot'
$env:DB_USERNAME = 'airline_exam_user'
$env:SERVER_PORT = '8081' # Optional, when another application owns 8080
.\mvnw.cmd spring-boot:run
```

Do not stop or reconfigure another application to free its port.

## Phone identity and key management

Accepted Ethiopian mobile formats are 09XXXXXXXX, 07XXXXXXXX,
+2519XXXXXXXX, +2517XXXXXXXX, and the equivalent 251 forms without a plus.
Spaces, parentheses, and hyphens are accepted as separators. Other country
codes, unsupported prefixes, incorrect lengths, extensions, and letters are
rejected. Canonical identity uses +251 followed by nine digits starting in 7 or 9.

Only a contact whose user_id equals the Telegram sender is accepted. Registration
is restricted to a private chat whose ID matches that sender. Typed phone numbers
and forwarded contacts belonging to others cannot complete registration.
Only Telegram ID, chosen language/exam, registration timestamps, and HMAC identity
are persisted; raw phone, Telegram names, usernames, and contact payloads are not.

Use a cryptographically random HMAC key and keep it in secure persistent storage
outside the repository. Supply the SAME key on every restart. A stored keyed
fingerprint rejects a changed key instead of silently permitting fresh claims.
Do not rotate or discard it without a designed identity migration and secure
backup. Account transfers and duplicate-phone recovery remain manual product
decisions; this phase never transfers access or grants a second free offer.

For this Windows development environment, generated local credentials can be
retained in a Windows DPAPI-encrypted file outside the repository:
%LOCALAPPDATA%\AirlineExamBot\development-secrets.clixml.
Only the Windows account/machine that encrypted it can normally decrypt it.
This is local development storage, not a production secret-management system.

If that local encrypted file has been provisioned, load it without printing values:

```powershell
$localSecrets = Import-Clixml (Join-Path $env:LOCALAPPDATA 'AirlineExamBot\development-secrets.clixml')
$env:PHONE_IDENTITY_HMAC_KEY = [System.Net.NetworkCredential]::new('', $localSecrets.PhoneHmacKey).Password
$env:ADMIN_BOOTSTRAP_USERNAME = $localSecrets.AdminUsername
$env:ADMIN_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $localSecrets.AdminPassword).Password
Remove-Variable localSecrets
```

## Admin bootstrap and usage

When the admin table is empty and both bootstrap values are supplied, startup
creates one BCrypt-hashed admin. Missing credentials create no default account.
When an admin already exists, startup never resets its password, even if the
bootstrap environment changes. Remove bootstrap credentials from the environment
after initial provisioning; retain them securely for login.

Open /admin/login. Authenticated administrators can manage:

- Overview: total users, completed registrations, active exams, categories, and current offer.
- Application settings: free limits, mock size, future lifetime price/currency,
  future payment flags, and support information.
- Exam types and categories: create, list, edit, activate, and deactivate.

Deactivation preserves foreign-key references and history. Inactive exam types
are not offered for registration; an unfinished selection is rechecked before
completion. There are no delete routes. Categories belong to existing exam types.
English names are required; optional Amharic names fall back to English.
Codes use lowercase letters, digits, and hyphens; category codes are unique within
their exam type.

Settings updates affect future registrations only. Existing grants are never
recalculated. Payment flags are configuration only and do not enable a payment
workflow in this phase.

Security uses sessions, BCrypt, CSRF-protected POST mutations/logout, generic
login failures, and admin authorization. GET /actuator/health remains public
without database details. Other actuator endpoints and unrelated routes remain
denied. Production deployment must use HTTPS and secure session cookies.

## Telegram

Set TELEGRAM_BOT_ENABLED=true, the token, and the stable phone HMAC key, then
restart. The [Telegram Bot API](https://core.telegram.org/bots/api) client checks
getMe and polls message and callback_query updates. Callback queries are
acknowledged. All transport calls occur after database transactions complete.

Send /start (or /start@YourBotUsername). It resumes saved progress. Pick a
language and active exam type, then press the contact-sharing button. A completed
user sees their saved access summary; no new grant is created.

If no active exam types exist, create one through admin and send /start again.
No exam names are hard-coded into Telegram code.

One short database lock on the settings singleton serializes registration writes,
admin taxonomy changes, and offer updates. Unique constraints provide a second
line of protection. This deliberately simple approach suits the initial workload;
it performs no Telegram network calls while holding the lock.

Polling keeps an in-memory offset, skips duplicates in that process, backs off
on failures, honors retry_after, and stops through the application lifecycle.
Persistent registration makes retries/restarts safe for business state.
Exactly-once outgoing messages are not promised: after a failed/uncertain send,
send /start to resume. No fake incoming-update endpoint is exposed.

Run only one poller for a token. API 401 means authentication failed; 409 means
another poller or webhook conflicts. The application never deletes a webhook.
Never enable JDK HTTP wire logging, which could expose token-bearing URLs.
Optional LOGGING_LEVEL_COM_AIRLINEPREP_BOT_TELEGRAM=DEBUG logs safe transport
events without message contents, phone identities, or chat IDs.

## Tests and database safety

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Automated tests override database, token, HMAC, and bootstrap configuration with
isolated H2 databases, fake keys, and mocked Telegram transport. They never require
or modify the development database. Coverage includes migration/JPA initialization,
normalization, ownership, duplicate/concurrent registration, rollback, snapshots,
bilingual keyboards, authentication, CSRF, validation, and admin management.
The separate live smoke test verifies the actual PostgreSQL migrations and HTTP/
Telegram transport. H2 alone is not evidence of PostgreSQL compatibility.

Flyway owns schema changes; Hibernate remains ddl-auto=validate. Versioned migrations:

1. V1__settings_and_admin.sql — initial configurable defaults, admins, change history.
2. V2__exam_types_and_categories.sql — managed exam taxonomy.
3. V3__registration_and_entitlements.sql — users, unique phone identity, saved grants.

Never edit applied migrations, clean Flyway, drop schemas/databases, truncate, or
reset development data. Use additive migrations for later changes.
Automatic baselining and Flyway clean are disabled.

Verify health on the chosen port:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8081/actuator/health'
```

Stop the application cleanly using Ctrl+C. Clear process credentials afterward:

```powershell
Remove-Item Env:DB_PASSWORD, Env:TELEGRAM_BOT_TOKEN, Env:PHONE_IDENTITY_HMAC_KEY,
    Env:ADMIN_BOOTSTRAP_PASSWORD, Env:ADMIN_BOOTSTRAP_USERNAME -ErrorAction SilentlyContinue
```

## Question bank and content management

Open **/admin/questions** to create drafts, search, filter, and inspect version
history. Lists use database pagination (20 questions/imports/rows per page,
10 historical versions per page). Filters cover text, exam, category, status,
pool, and rights. Sort by creation or update time. Archived questions are
excluded unless explicitly selected.

The lifecycle is DRAFT → REVIEWED → PUBLISHED → ARCHIVED. Reviewed content can
return to draft. Saving reviewed content also clears review. Incomplete drafts
are allowed; review and publication require an active matching exam/category,
question, explanation, EASY/MEDIUM/HARD difficulty, 2–8 distinct nonblank options,
exactly one correct answer, source type/title, resolved rights, and at least one
pool. Blank option slots are omitted and remaining options preserve order.

Published edits create a new numbered draft version. The previous text,
answer key, explanation, source metadata, eligibility, exam/category names and
IDs, and publication/review timestamps remain preserved. A draft revision removes
the logical question from new selection until published again. Archiving applies
to the logical question and preserves all versions; no delete or restore endpoint
exists. Concurrent stale edits are rejected. Future attempts must reference
question_versions.id; the logical questions.id is the stable unique-practice
quota identity across routine revisions. No serving or quota engine exists yet.

Free practice, premium practice, and mock flags may be combined without duplicating
questions. Flags do not imply publication or user authorization. Future delivery
must require PUBLISHED and recheck active taxonomy, the requested pool, and the
user's entitlement. QuestionService.search is an admin query, not a student access
boundary.

Source metadata includes type, title, reference, optional year, notes, and use
status: ORIGINAL, LICENSED, PERMITTED, PUBLIC_DOMAIN, UNKNOWN_REVIEW_REQUIRED, or
BLOCKED. Unknown/blocked rights are visible and prevent review/publication.
Only import content you have permission to use commercially. Public visibility
does not establish permission. No exam papers are scraped or seeded.

### CSV and XLSX imports

Use **/admin/questions/import** and download the CSV template. The same headers
work in a single-sheet XLSX saved from a spreadsheet editor. Apache Commons CSV
1.14.1 handles quoted CSV; Apache POI 5.5.1 handles XLSX. Both are Apache-licensed.

Every header below is required exactly once (order may vary); optional cells may
be blank. Unknown, duplicate, or missing headers are rejected.

```text
exam_type,category,question,option_a,option_b,option_c,option_d,option_e,option_f,option_g,option_h,correct_answer,explanation,difficulty,source_type,source,source_reference,source_year,source_notes,copyright_status,free_available,premium_available,mock_available
```

- exam_type and category: existing catalog **codes**, with category scoped to exam.
- question and explanation: nonblank, up to 12,000 characters each.
- option_a through option_h: 2–8 nonblank choices, up to 2,000 characters each.
  correct_answer is exactly one A–H letter identifying a populated slot.
- difficulty: EASY, MEDIUM, HARD.
- source_type and source: nonblank type/title (100/300 characters).
- source_reference, source_year, source_notes: optional (500 characters,
  four-digit year 1000–9999, 2,000 characters).
- copyright_status: one of the use statuses above.
- free_available, premium_available, mock_available: true or false, with at least
  one true. Blank booleans are invalid.

Limits: 2 MiB per file, 3 MiB multipart request, 500 data rows, 12,000 characters
per cell; XLSX ZIP expansion is limited to 20 MiB and 1,000 parts. Only UTF-8 CSV
(with optional BOM) and XLSX are accepted. CSV supports quoted commas, multiline
fields, and Unicode. Blank rows are ignored. XLSX must have exactly one sheet;
formula/error cells are invalid, and macros, external links, embedded objects,
legacy XLS, and corrupt workbooks are rejected. Formulas are never evaluated.
Filenames cannot contain path separators or control characters. Raw uploads
are not retained as files.

Upload parses and persists a row-level preview without creating questions.
Preview reports source status, row number, reference codes, errors, and duplicate
reasons. The original submitted cells are retained as staging JSON for review and
revalidation; question content itself uses normalized relational tables.

Duplicate detection is scoped to exam type and checks all retained versions,
including archived history. NFKC normalization, Unicode whitespace folding, and
case folding identify exact question/options/answer-key matches. Same normalized
question text with differing options or key is a likely duplicate. Punctuation
is preserved to avoid merging mathematically distinct questions. Both classes
are skipped by confirmation and remain available for human review in batch
history; no automatic merge or override exists. Inspect existing content before
manually creating a distinct question.

Confirmation is an authenticated CSRF-protected POST. It revalidates references
and duplicates and creates only valid unique drafts. Unknown/blocked rights may
be imported as drafts for source review. Row errors do not prevent other valid
rows from being imported. Cancel creates no questions. Batch reports retain
uploaded actor/time/name, valid/invalid/duplicate/created/failed counts, and links
to created questions.

Confirmation is one atomic transaction. An unexpected persistence failure rolls
back all its questions, options, links, and counts, leaving the staged batch
retryable; it never claims partial success. Invalid/duplicate rows are expected
outcomes, not failed writes. A successful confirmation has zero failed writes.
The existing settings-row lock serializes confirmation with manual question
creation, registration, and taxonomy edits; parsing happens outside that lock.
The 500-row limit bounds lock duration. Persistent batch state makes refreshes
and simultaneous confirmations idempotent. Audit events retain acting admin and
question/version/batch identifiers without copying full content into audit logs.

### Phase 5 schema and checks

4. V4__question_bank.sql — logical questions, content versions, ordered options,
   historical taxonomy/source context, fingerprints, and lifecycle indexes.
5. V5__question_imports.sql — persistent preview rows, batch counts, question links.
6. V6__question_edit_revision.sql — deterministic optimistic revision advancement
   for draft edits, including saves within the same clock tick/transaction.

Indexes support lifecycle/current-version lookups, taxonomy, source rights,
fingerprints, and import history. Low-selectivity boolean flags are filtered
alongside indexed taxonomy/current-version joins rather than individually indexed.
Search uses bound criteria parameters and database pagination. Content escapes
through Thymeleaf; admin forms bind DTOs, not entities. CSRF and existing admin
role requirements remain in force. No registration phone data appears in content
pages. All automated tests use isolated databases.

```powershell
.\mvnw.cmd '-Dtest=QuestionBankTests,QuestionTransactionTests' test
.\mvnw.cmd test
.\mvnw.cmd package
```
