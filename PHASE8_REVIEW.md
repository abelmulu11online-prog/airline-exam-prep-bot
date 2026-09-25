# Phase 8 security and recovery review

Baseline: `37892e6`. Scope: the existing V1 application, not production deployment.
This is an application review and adversarial regression record, not a claim of
exhaustive penetration testing or full WCAG compliance.

## Threat model and evidence

| Actor / boundary | Asset and control | Evidence |
| --- | --- | --- |
| Anonymous browser / ordinary identity | Admin resources and receipts require ADMIN; only login, CSS and basic health are public | HardeningWebTests covers sensitive reads, mutations and actuator endpoints |
| Admin login / stolen session | BCrypt cost 12, generic failures, session rotation, POST+CSRF logout, session invalidation | AdminWebTests, AdminBootstrapTests, HardeningWebTests |
| Repeated login attempts | Ten attempts per direct peer per minute, at most 4,096 peers; no username lock and no trust in forwarded headers | LoginThrottleTests; production proxy policy remains Phase 9 work |
| Browser forms | Explicit DTO mapping, CSRF, escaped Thymeleaf, CSP without scripts, no-referrer, frame and MIME protections | AdminJourneyTests, QuestionBankTests, PaymentWebTests, HardeningWebTests |
| Malicious Telegram sender | Private chat/sender agreement, contact ownership, rejection of forwarded contacts, server-side resource ownership | TelegramUpdateHandlerTests, RegistrationServiceTests, engine/payment tests |
| Replayed / stale buttons | Persistent creation keys, unique consumption, immutable evidence, state validation; invalid student actions receive safe menu guidance | FullJourneyTests, StudentTelegramEngineTests, PaymentConcurrencyTests |
| Duplicate account | Canonical Ethiopian phone HMAC, stable key guard, unique constraints and atomic entitlement | PhoneIdentityTests, RegistrationServiceTests, RegistrationRollbackTests |
| Malicious uploader | Admin-only input, 2 MiB file / 3 MiB multipart, exact headers, 500 rows, cell limits, 20 MiB expanded XLSX, 1,000 parts, no macros/embedded objects/external-link parts/formula evaluation | QuestionBankTests and ImportAttackTests |
| Receipt / external file path | Stored file ID selected only by authorized payment ID; fixed Telegram host, no redirects, bounded stream, path and signature checks | ReceiptTests, TelegramReceiptClientTests, PaymentWebTests |
| Payment fraud / admin double-click | Snapshots, reserved normalized references, terminal state machine, one grant provenance, atomic audit | PaymentServiceTests, PaymentConcurrencyTests, FullJourneyTests, AdminJourneyTests |
| Network failure / blocked user | Business commits precede network calls; safe API errors, bounded backoff and five automatic notification attempts | Telegram client/polling tests, FullJourneyTests, PaymentNotificationTests |
| Failed DB write | No half registration, answer, payment, grant or critical audit | RegistrationRollbackTests, EngineConcurrencyTests, PaymentConcurrencyTests, FailureRecoveryTests |
| Concurrent / crashed outbox worker | Two-minute lease plus unique claim token; stale completion cannot overwrite current worker; abandoned fifth attempt becomes manually retryable | PaymentNotificationTests; V13 adds claim_token only |
| Restart | Registration, usage, active mock/deadline, payment stages, access and queued outbox survive context close/reopen | RestartPersistenceTests uses two real application contexts and an isolated persistent-in-memory H2 database |
| Oversized result sets | Bounded question/import/payment/audit pages, SQL practice selection and bounded random mock selection | PerformanceSanityTests (300 questions / 50-item mock), existing pagination tests, 500-row parser checks |

## Review conclusions

- SQL values use JDBC parameters or JPA Criteria. Sorting and pool names are
  allowlisted. No client-supplied SQL identifiers are interpolated. Native dynamic
  Sort repository methods are absent.
- Question current versions are fetched by an entity graph; option collections
  use batch fetching. Mock preparation inserts its bounded frozen set; it does not
  load every pool question. Scoring and progress use grouped SQL. Taxonomy choices
  are intentionally small configuration lists, not user/history tables. No cache
  is introduced for mutable financial or access state.
- Existing foreign keys restrict deletion of referenced questions/versions,
  entitlements, practice, mocks and payments. No cascade-delete historical path or
  application delete route was added. V1–V12 remain unchanged.
- Money remains BigDecimal/NUMERIC; zero is allowed by the existing settings
  policy, negative values and excess precision are rejected. Telegram IDs use
  longs. Business timestamps remain Instant / time-zone-aware columns.
- Admin bootstrap holds the existing settings lock before checking/creating the
  initial admin. Restart never resets an existing password. Phone HMAC cannot be
  silently regenerated; missing required or changed keys fail safely.
- All essential business state is in PostgreSQL; receipt binaries remain in
  Telegram. Import bytes/workbooks and download streams close through
  try-with-resources. Application code never writes uploaded filenames to disk.
  Multipart temporary files are servlet-managed; there is no durable local upload
  directory. CSV download contains only fixed template headers, so export formula
  injection is not applicable. Imported formula-looking CSV values remain text.
- Plain-text Telegram messages are chunked below transport limits; no HTML parse
  mode is used. English/Amharic keys and placeholder indexes are compared by test.
  Safe error pages, form labels, visible focus, responsive containers and empty
  states are reviewed. This does not certify every assistive technology/browser.
- The global settings lock is deliberately conservative and serializes mutations.
  This is suitable for initial V1 traffic, not a high-throughput benchmark claim.
- Telegram delivery cannot be exactly once after a lost acknowledgement. Restart
  can repeat an outgoing message, but cannot duplicate consumption or a grant.
  Claim ownership fences database completion, not an already-started HTTP send.
- Login throttling is a bounded local defense, not distributed abuse prevention.
  It resets on restart and clients behind the same direct proxy share its budget.
  Do not enable trust in arbitrary forwarded headers to bypass this limitation.

## Dependency review (2026-09-25)

Maven dependency tree reviewed. No new libraries. Flyway core/PostgreSQL remain
aligned at 11.20.3; CSV 1.14.1, POI 5.5.1 and pgJDBC 42.7.11 remain unchanged.
Tomcat is patched from 10.1.55 to 10.1.60, using Boot's version override to keep
all embedded Tomcat modules aligned. Apache documents request-handling fixes,
including CVE-2026-86350 and CVE-2026-77756, in that release:
[Tomcat security advisories](https://tomcat.apache.org/security-10.html).

Spring Boot 3.5.16 is the final OSS release of its generation. Before production,
choose a supported framework upgrade or an appropriate support arrangement:
[Spring's release notice](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).
No speculative major-version migration was performed in this phase.

The reviewed August Spring advisories have preconditions absent here: no WebAuthn,
distributed sessions, DPoP or AesBytesEncryptor, and no untrusted native Sort.
This is code-based applicability analysis, not a clean bill for every dependency
or a replacement for ongoing vulnerability scanning:
[WebAuthn](https://spring.io/security/cve-2026-47841/),
[DPoP](https://spring.io/security/cve-2026-41707/),
[AES](https://spring.io/security/cve-2026-47842/),
[JPA sorting](https://spring.io/security/cve-2026-47834/).
POI remains restricted to authenticated administrators and bounded inputs; parser
isolation would be needed for a future public upload service. See
[Apache's processing guidance](https://poi.apache.org/security.html).

## Phase 9 handoff

Before serving production traffic: resolve framework support, configure HTTPS and
secure cookies, choose one polling instance or design authenticated webhook
delivery, restrict database/network access, provision external secrets, and test
PostgreSQL backup/restore together with the stable HMAC key. Receipt availability
also depends on Telegram. No DNS, certificates, production database, deployment,
production secrets or webhook were provisioned in Phase 8.

## Final verification (2026-09-25)

- Automatic suite: 376 tests, zero failures/errors/skips in each of the full test,
  package, and repeated randomized test runs (seed 8252026). The baseline was 294.
  No observed flaky tests remain. The final executable artifact builds successfully.
- Real PostgreSQL 18.6: PaymentPostgresIT and PostgresExamEngineIT both pass, with
  scenario writes rolled back. V13 applies and all 13 Flyway migrations validate;
  V1-V12 are unchanged. Constraints validate, consistency queries return zero
  violations, and there are no abandoned notification claims. Existing totals stay
  at two users, two payment requests, one lifetime grant and four sent notifications.
- Final packaged application starts on available port 8080. Public health returns
  only UP; internal actuator endpoints and direct /error access return 403. Admin
  and receipt access redirect anonymous visitors to login. Browser security headers
  are present, and authenticated POST without CSRF returns 403.
- Real headless Edge: successful admin login/logout, HttpOnly/SameSite=Lax cookies,
  nine admin pages returning 200 at both 1440px and 390px, associated form labels,
  no document-width overflow, and friendly 404 recovery. Desktop/mobile screenshots
  were visually inspected, then removed along with the isolated browser profile.
- Telegram getMe and repeated long polling succeed without 401/409. The explicitly
  authorized admin smoke-test notification is accepted. Live /start/menu confirmation
  was requested but not received; handler and complete student journeys pass in the
  automated suite. No inbound Telegram update or human receipt was fabricated.
- Optional real human receipt upload/retrieval: NOT RUN. The existing development
  account already has lifetime access; it was not downgraded. No published eligible
  development questions exist, so real practice/mock interactions were not run.
  Automated PostgreSQL verification covers 100 unique practice answers and two
  complete 50-item mock attempts, in addition to the larger-pool isolated test.
- Source and ignored-artifact scans find none of the real development credentials,
  stable HMAC key or Telegram admin ID. No raw-phone logging is present. .env remains
  ignored. No production infrastructure or Phase 9 deployment files are introduced.
- Graceful shutdown is verified against the packaged classes using a temporary
  local lifecycle adapter: polling stops, Tomcat completes graceful shutdown, JPA
  closes and Hikari shuts down; process exit is zero. The adapter is removed and no
  diagnostic/shutdown endpoint or test-only production path is added. The development
  application is stopped after verification.
