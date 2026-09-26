Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-DatabaseEnvironment {
    foreach ($name in @('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','DB_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "$name is required in the process environment." }
    }
    if ($env:DB_HOST -notmatch '^[A-Za-z0-9.-]+$' -or $env:DB_PORT -notmatch '^[0-9]{1,5}$' `
        -or $env:DB_NAME -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid database target fields.' }
}

function Invoke-WithDatabaseEnvironment {
    param([Parameter(Mandatory)][scriptblock]$Operation, [string]$SslMode = 'require')
    $names = @('PGPASSWORD','PGSSLMODE','PGCONNECT_TIMEOUT')
    $previous = @{}
    foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
    try {
        $env:PGPASSWORD = $env:DB_PASSWORD
        $env:PGSSLMODE = $SslMode
        $env:PGCONNECT_TIMEOUT = '15'
        & $Operation
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    }
}

function Invoke-SafePostgresTool {
    param([Parameter(Mandatory)][string]$Tool, [Parameter(Mandatory)][string[]]$Arguments)
    $command = Get-Command $Tool -ErrorAction SilentlyContinue
    if (-not $command) { throw "$Tool must be installed and available on PATH." }
    # Native stderr can include connection details. Never relay it or exception records.
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $command.Source @Arguments 2>$null
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    if ($code -ne 0) { throw "$Tool failed (exit $code); private diagnostic details withheld." }
    return $result
}
