# Schema migrations

Phase 2 has no business entities or tables, so it has no SQL migration.
Flyway is enabled and uses the application datasource. On an empty database,
its startup migration step can create `flyway_schema_history` metadata even
when no business migrations exist. It does not create application tables.

Add the first required schema change in its implementation phase using
`V1__description.sql`, then `V2__description.sql`, `V3__description.sql`, etc.
Review SQL before execution. Never edit an applied migration; add a new one.
Never run Flyway clean or destructive operations on development/production.
Automatic baselining is disabled: investigate an unexpected existing schema.

JPA uses `ddl-auto=validate` and cannot create or update the schema.
The foundation tests exercise Flyway initialization and validation on isolated
in-memory H2, not PostgreSQL SQL compatibility. Verify PostgreSQL connectivity
and migration execution locally when the required environment is available.
