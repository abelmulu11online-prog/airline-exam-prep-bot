# Schema migrations

Phases 1–3 had no business migrations. Compressed Phase 4 introduces:

- V1: settings defaults, persisted admins, and administrative change records.
- V2: exam types and categories.
- V3: registration state, unique phone identity, and entitlement snapshots.

These additive migrations run through Flyway on startup. They seed safe business
defaults only, never passwords or secrets. Exam types/categories are admin-managed.

Add subsequent schema changes using the next available version.
Review SQL before execution. Never edit an applied migration; add a new one.
Never run Flyway clean or destructive operations on development/production.
Automatic baselining is disabled: investigate an unexpected existing schema.

JPA uses `ddl-auto=validate` and cannot create or update the schema.
Automated tests run these migrations in isolated H2 databases. PostgreSQL 18
migration compatibility is also verified separately against the authorized local
development database, without cleaning or resetting it.
