# Airline Exam Preparation Bot

A Telegram-based airline written-exam preparation platform with a future
Thymeleaf admin website in the same Spring Boot application.
[PROJECT_SPEC.md](PROJECT_SPEC.md) is the authoritative product specification.

## Current phase

Phase 2 provides the technical foundation: a Java entry point, dependencies,
environment-based PostgreSQL configuration, Flyway, safe JPA configuration,
restricted HTTP access, health monitoring, and isolated baseline tests.

Telegram integration, `/start`, registration, phone verification, entitlements,
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

Spring Boot manages dependency versions together. The 3.5 release line retains
the requested JUnit 5 stack. H2 is not packaged as an application database.

## Local PostgreSQL and environment

The expected development database already exists. This project does not create
or recreate it.

| Variable | Default / requirement |
| --- | --- |
| `DB_HOST` | `127.0.0.1` |
| `DB_PORT` | `1621` |
| `DB_NAME` | `airline_exam_bot` |
| `DB_USERNAME` | `airline_exam_user` |
| `DB_PASSWORD` | Required for application startup; no default |
| `SERVER_PORT` | Optional; defaults to `8080` |

`.env.example` documents safe placeholders. Spring Boot and the wrapper do not
automatically load `.env` files. Supply actual values through the current shell
or your IDE's private environment configuration. Never commit or log a password.
The Telegram entries in `.env.example` are reserved placeholders and are unused;
tests and application startup require no Telegram credentials.

In PowerShell, from the project root:

```powershell
java -version
.\mvnw.cmd --version
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '1621'
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
