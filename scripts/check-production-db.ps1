. (Join-Path $PSScriptRoot 'postgres-common.ps1')
Assert-DatabaseEnvironment
Invoke-WithDatabaseEnvironment {
    $arguments = @('--host',$env:DB_HOST,'--port',$env:DB_PORT,'--username',$env:DB_USERNAME,'--dbname',$env:DB_NAME,
        '--no-password','--no-psqlrc','--tuples-only','--no-align','--set','ON_ERROR_STOP=1')
    # Successful libpq connection with PGSSLMODE=require proves client-to-pooler TLS.
    # pg_stat_ssl describes the pooler's backend connection, not this client connection.
    $connected = Invoke-SafePostgresTool psql ($arguments + @('--command','SELECT 1;'))
    if ("$connected".Trim() -ne '1') { throw 'Database connectivity was not confirmed.' }
    $tables = @(Invoke-SafePostgresTool psql ($arguments + @('--command',"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;")))
    if ($tables.Count -eq 0) { Write-Output 'SSL verified. Public schema has no tables; ready for initial Flyway migration.'; return }
    if ($tables -notcontains 'flyway_schema_history') { throw 'Existing public tables without Flyway history. STOP and inspect the schema privately before deployment.' }
    $history = Invoke-SafePostgresTool psql ($arguments + @('--command',"SELECT version || ':' || success FROM public.flyway_schema_history ORDER BY installed_rank;"))
    Write-Output 'SSL verified. Existing Flyway versions/status (review before deploying):'
    $history
}
