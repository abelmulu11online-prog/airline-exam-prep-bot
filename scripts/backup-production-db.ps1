. (Join-Path $PSScriptRoot 'postgres-common.ps1')
Assert-DatabaseEnvironment
$directory = Join-Path (Split-Path $PSScriptRoot -Parent) 'backups'
$null = New-Item -ItemType Directory -Force -Path $directory
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ')
$destination = Join-Path $directory ("airline-$stamp.dump")
Write-Output "Backup target: $($env:DB_HOST) / $($env:DB_NAME); UTC: $stamp"
Invoke-WithDatabaseEnvironment {
    $null = Invoke-SafePostgresTool pg_dump @('--host',$env:DB_HOST,'--port',$env:DB_PORT,'--username',$env:DB_USERNAME,
        '--dbname',$env:DB_NAME,'--no-password','--format=custom','--schema=public','--no-owner','--no-acl','--file',$destination)
    $null = Invoke-SafePostgresTool pg_restore @('--list',$destination)
}
if ((Get-Item -LiteralPath $destination).Length -eq 0) { throw 'Backup is empty.' }
Write-Output "Backup archive verified: $destination"
