param(
    [Parameter(Mandatory)][string]$BackupFile,
    [Parameter(Mandatory)][ValidateSet('127.0.0.1','localhost','::1')][string]$DestinationHost,
    [Parameter(Mandatory)][ValidateRange(1,65535)][int]$DestinationPort,
    [Parameter(Mandatory)][ValidatePattern('^airline_restore_[a-z0-9_]+$')][string]$DestinationDatabase,
    [Parameter(Mandatory)][string]$DestinationUsername
)
. (Join-Path $PSScriptRoot 'postgres-common.ps1')
if ([string]::IsNullOrWhiteSpace($env:RESTORE_DB_PASSWORD)) { throw 'RESTORE_DB_PASSWORD is required; production credentials are never used for restore.' }
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) { throw 'Backup file does not exist.' }
$priorPassword = $env:DB_PASSWORD
try {
    $env:DB_PASSWORD = $env:RESTORE_DB_PASSWORD
    # Local disposable PostgreSQL may not have a TLS certificate. Never used for production.
    Invoke-WithDatabaseEnvironment -SslMode prefer -Operation {
        $target = @('--host',$DestinationHost,'--port',"$DestinationPort",'--username',$DestinationUsername,'--dbname',$DestinationDatabase,'--no-password')
        $count = Invoke-SafePostgresTool psql ($target + @('--no-psqlrc','--tuples-only','--no-align','--set','ON_ERROR_STOP=1','--command',
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp%' AND c.relkind IN ('r','p','v','m','S','f');"))
        if ("$count".Trim() -ne '0') { throw 'Destination is not empty. Restore refused.' }
        # A newly provisioned PostgreSQL database already contains public. Restore its
        # contents without trying to recreate that schema; never drop it to make room.
        $entries = @(Invoke-SafePostgresTool pg_restore @('--list',$BackupFile))
        $entries = @($entries | Where-Object { $_ -notmatch '^\d+;\s+\d+\s+\d+\s+SCHEMA\s+-\s+public\s' })
        $listFile = [System.IO.Path]::GetTempFileName()
        try {
            [System.IO.File]::WriteAllLines($listFile, [string[]]$entries, [System.Text.UTF8Encoding]::new($false))
            $null = Invoke-SafePostgresTool pg_restore ($target + @('--exit-on-error','--single-transaction','--no-owner','--no-acl','--use-list',$listFile,$BackupFile))
        } finally { Remove-Item -LiteralPath $listFile -Force }
    }
} finally { $env:DB_PASSWORD = $priorPassword }
Write-Output 'Isolated restore completed. Validate Flyway history, row counts and application startup before declaring recovery verified.'
