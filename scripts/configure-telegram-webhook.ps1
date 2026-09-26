param([Parameter(Mandatory)][uri]$PublicUrl)
. (Join-Path $PSScriptRoot 'telegram-common.ps1')
if (-not $PublicUrl.IsAbsoluteUri -or $PublicUrl.Scheme -ne 'https' -or $PublicUrl.UserInfo -or $PublicUrl.Query -or $PublicUrl.Fragment `
        -or $PublicUrl.AbsolutePath -ne '/' -or -not $PublicUrl.IsDefaultPort) {
    throw 'PublicUrl must be an HTTPS origin without credentials, path, query, fragment or custom port.'
}
if ($env:TELEGRAM_WEBHOOK_SECRET -cnotmatch '^[A-Za-z0-9_-]{1,256}$') { throw 'A valid TELEGRAM_WEBHOOK_SECRET is required in the process environment.' }
$identity = Invoke-TelegramOperation getMe
if (-not $identity.is_bot) { throw 'getMe did not identify a bot.' }
Write-Output ('Verified bot: @' + (Protect-TelegramText $identity.username))
$null = Invoke-TelegramOperation setWebhook @{
    url = $PublicUrl.GetLeftPart([System.UriPartial]::Authority) + '/api/telegram/webhook'
    secret_token = $env:TELEGRAM_WEBHOOK_SECRET
    allowed_updates = @('message','callback_query')
    max_connections = 1
    drop_pending_updates = $false
}
Show-TelegramWebhookInfo
