# Airline Exam Preparation Bot

One Java 21 / Spring Boot application for Telegram exam preparation and a
Thymeleaf admin website. [PROJECT_SPEC.md](PROJECT_SPEC.md) defines the product rules.

## Current scope: compressed Phase 4

- Private-chat registration: /start → English or Amharic → active exam type →
  share your own Telegram contact → registration and free entitlement.
- PostgreSQL registration state survives restarts. Repeated commands, callbacks,
  and same-user contacts do not recreate accounts or entitlements.
- Free snapshots initially grant 100 unique practice questions, 2 mock exams,
  and 50 questions per mock, without expiration. These are independent allowances.
- Admin login, dashboard, settings, exam types, and categories.
- Administrative changes retain actor, timestamp, and before/after values.
- Questions, answering, quota consumption, mocks, payments, imports, and deployment
  are not implemented. Compressed Phase 5 is Question Bank & Content Management.

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
