# Airline Exam Preparation Bot

A Telegram-based airline written-exam preparation platform with a future
Thymeleaf admin website in the same Spring Boot application.
[PROJECT_SPEC.md](PROJECT_SPEC.md) is the authoritative product specification.

## Current phase

Phase 3 adds optional Telegram long polling and a welcome reply to `/start`.
The Phase 2 foundation remains: a Java entry point, dependencies,
environment-based PostgreSQL configuration, Flyway, safe JPA configuration,
restricted HTTP access, health monitoring, and isolated baseline tests.

Registration, phone verification, entitlements,
questions, categories, exams, practice, progress, payments, receipts, lifetime
access, admin accounts/pages, imports, audit logging, and deployment are not
implemented. No business tables or SQL migrations exist yet.

## Stack and requirements

- JDK 21 (a JDK, not only a JRE), available on `PATH` or through `JAVA_HOME`.
- Spring Boot 3.5.16, a stable release compatible with Java 21 and JUnit 5.
  See the [official requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- Maven 3.9.16 through Maven Wrapper 3.3.4; no global Maven install is needed.
  Initial wrapper/dependency downloads require internet access.
- PostgreSQL running locally for normal application startup.
- Spring MVC, Data JPA, Validation, Security, Thymeleaf, Actuator, Flyway core
  and its PostgreSQL module, and the PostgreSQL JDBC driver.
- Spring Boot test support/JUnit 5, with H2 exclusively on the test classpath.

Spring Boot manages dependency versions together, with Flyway core and its
PostgreSQL module pinned together to 11.20.3 for PostgreSQL 18 support.
The 3.5 release line retains
the requested JUnit 5 stack. H2 is not packaged as an application database.

## Local PostgreSQL and environment

The expected development database already exists. This project does not create
or recreate it.

| Variable | Default / requirement |
| --- | --- |
| `DB_HOST` | `127.0.0.1` |
| `DB_PORT` | `5432` (configurable) |
| `DB_NAME` | `airline_exam_bot` |
| `DB_USERNAME` | `airline_exam_user` |
| `DB_PASSWORD` | Required for application startup; no default |
| `SERVER_PORT` | Optional; defaults to `8080` |

The local PostgreSQL service uses port 5432. `DB_PORT` overrides the application
default, so update any existing shell or IDE configuration that uses another port.
Custom PostgreSQL ports remain supported by setting `DB_PORT` explicitly.

`.env.example` documents safe placeholders. Spring Boot and the wrapper do not
automatically load `.env` files. Supply actual values through the current shell
or your IDE's private environment configuration. Never commit or log a password.
Telegram is disabled by default. Tests and disabled-mode startup require no
Telegram credentials. `TELEGRAM_ADMIN_ID` remains unused until a later phase.

In PowerShell, from the project root:

```powershell
java -version
.\mvnw.cmd --version
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:DB_NAME = 'airline_exam_bot'
$env:DB_USERNAME = 'airline_exam_user'
```

Set the password without putting it in terminal history or a file:

```powershell
$dbPasswordInput = Read-Host 'Local PostgreSQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPasswordInput).Password
Remove-Variable dbPasswordInput
```

Environment variables are inherited when processes start. Set them in the same
PowerShell session used to launch Maven. Setting a variable in another terminal
does not update an already-running application or agent shell. Do not paste the
password into chat. If `JAVA_HOME` is set, it must point to the JDK 21 directory,
not its `bin` subdirectory.

## Run tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The tests load the application context, JPA, Flyway, and the real security chain.
Test-only dynamic properties force unique in-memory H2 connections for both JPA
and Flyway, overriding ordinary datasource environment settings. No development
database or `DB_PASSWORD` is required. JPA validation and Flyway remain enabled;
production configuration is not weakened for tests.

Tests check public health with no database detail leakage, denial of other
routes without login redirects, persistence initialization, and absence of a
generated login user. MockMvc exercises the health endpoint inside the test
context; it does not establish that local PostgreSQL or a listening HTTP server
has been verified. H2 is not a PostgreSQL migration compatibility test. Add an
isolated PostgreSQL integration strategy when business migrations need it.
Telegram tests use mocked HTTP/API clients only. The full application test
explicitly disables Telegram and clears its token in test properties, even if
the developer's environment enables a real bot. Additional tests cover command
parsing, malformed responses/updates, duplicate offsets, backoff, interruption,
and redaction of exception details. No real Telegram API call occurs in tests.

## Telegram local development (Phase 3)

| Variable | Behavior |
| --- | --- |
| `TELEGRAM_BOT_ENABLED` | Defaults to `false`; set to `true` to start polling |
| `TELEGRAM_BOT_TOKEN` | Required when enabled; supplied through the environment only |

The implementation calls the [Telegram Bot API](https://core.telegram.org/bots/api)
directly with Java 21's HTTP client and the existing Jackson dependency; no
Telegram SDK or additional Maven dependency is needed. Typed configuration
validates token syntax when enabled; `getMe` checks the actual bot identity
before polling. API authentication failure is logged as a numeric code with
backoff, not a token-bearing exception.

In the same PowerShell session where you configured the local database:

```powershell
$env:TELEGRAM_BOT_ENABLED = 'true'
$telegramTokenInput = Read-Host 'Telegram bot token' -AsSecureString
$env:TELEGRAM_BOT_TOKEN = [System.Net.NetworkCredential]::new('', $telegramTokenInput).Password
Remove-Variable telegramTokenInput
.\mvnw.cmd spring-boot:run
```

Keep the real token out of `.env.example`, command-line arguments, chat, source
files, and logs. The placeholder notation is
`TELEGRAM_BOT_TOKEN=<your_bot_token>`; it is not a usable credential.

1. Check the console for successful PostgreSQL/Flyway startup, `Telegram polling
   started`, and `Telegram bot identity verified`.
2. In a second terminal, verify `/actuator/health` as described below. Health
   reports application/database health; it does not prove Telegram connectivity.
3. In a private chat with your bot, send `/start`. Expect the welcome message
   beginning “✈️ Welcome to Airline Exam Prep!” No account is created.
4. `/start@YourBotUsername` is also recognized; commands addressed to another
   bot are ignored. `/start` arguments are ignored for now. Other commands,
   non-text messages, and unsupported updates are ignored.
5. Use Ctrl+C in the application terminal. Expect `Telegram polling stopped`;
   shutdown interrupts pending requests and closes the managed HTTP client.
6. Remove the token and disable flag when finished:

```powershell
Remove-Item Env:TELEGRAM_BOT_TOKEN
Remove-Item Env:TELEGRAM_BOT_ENABLED
```

Only run one polling process per token. An existing webhook or another poller
can cause API code 409; this implementation neither installs nor deletes a
webhook. Production webhook support is deferred. Code 401 indicates rejected
authentication; code 429 triggers the provided retry delay. Do not enable JDK
HTTP wire/debug logging: it can expose URLs containing the token. The enabled
configuration rejects the JDK HTTP diagnostic system properties, and application
logging disables the HTTP client categories. Never override these safeguards
while using real credentials.

### Polling behavior and delivery limits

For local transport verification, set
`LOGGING_LEVEL_COM_AIRLINEPREP_BOT_TELEGRAM=DEBUG`. Diagnostics report successful
polls, welcome replies accepted by Telegram, and ignored text, without logging
tokens, chat IDs, or message contents. Remove the override after verification.

One lifecycle-managed worker polls with a 25-second server timeout, 40-second
request timeout, and a 250-millisecond minimum pause between successful batches.
Failures back off from 2 to 60 seconds; a larger Telegram `retry_after` is honored.
Only ordinary message updates are requested. No schema changes are made.

The in-memory offset advances past each handled/ignored update. Duplicate IDs
are skipped during that process. A failed or uncertain welcome send is not
automatically resent, to avoid repeated replies; the user can send `/start`
again. Later updates remain pending while polling backs off. This development
transport does not promise exactly-once delivery: a crash/restart before Telegram
acknowledges the next offset can replay the last update. Durable deduplication
belongs with later persisted workflows, not a new Phase 3 business table.

If credentials are unavailable to an agent process, perform the live checks in
your own locally configured terminal. Never paste credentials into the agent.

## Start and verify locally

With the environment set:

```powershell
.\mvnw.cmd spring-boot:run
```

In another PowerShell window:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health'
```

Expect `status` equal to `UP` (JSON: `{"status":"UP"}`). The normal health check
includes the configured datasource. Use Ctrl+C in the application terminal to
stop it gracefully. Clear the password when finished:

```powershell
Remove-Item Env:DB_PASSWORD
```

If port 8080 is occupied, identify it before starting:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

Do not kill an unrelated process. If needed, set `$env:SERVER_PORT = '8081'`
for that run and use port 8081 in the health URL. Clear the override afterwards
with `Remove-Item Env:SERVER_PORT`.

## Schema management and security

Flyway is enabled for PostgreSQL. Its initial startup may create only its
`flyway_schema_history` metadata table; no Phase 2 business SQL is present.
An empty migration warning is therefore expected. Database objects are created
only when a later phase actually needs them. Before starting against an existing
database, review its state and any pending migrations. Automatic baselining and
Flyway cleaning are disabled. Never clean, drop, truncate, or bulk-delete data
in development or production.

Use `src/main/resources/db/migration/V1__description.sql`, then
`V2__description.sql`, `V3__description.sql`, etc. Never edit an applied migration.
JPA uses `ddl-auto=validate`, SQL logging is off, and open-in-view is disabled.

Only `GET /actuator/health` is publicly permitted. Other requests, including
`/admin` and `/login`, are denied with HTTP 403. Only the health actuator endpoint
is exposed over HTTP; JMX actuator exposure and health details are disabled.
CSRF protection remains enabled. There are no default/generated users, form
login, or HTTP Basic credentials. Phase 6 must introduce proper admin
authentication and adjust the security chain deliberately.

Thymeleaf is present for that future phase; template-location checking is disabled
while there are no templates. No empty controllers, services, or business modules
have been added.
