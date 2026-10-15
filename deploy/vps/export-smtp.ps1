# Export only SMTP settings; do not copy the development database/encryption keys.
param(
    [string]$Source = (Join-Path $PSScriptRoot '../../.env'),
    [string]$Destination = (Join-Path $PSScriptRoot '.env.smtp')
)
$ErrorActionPreference = 'Stop'
$smtpValues = @{}
foreach ($smtpLine in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Source))) {
    if ($smtpLine -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $smtpValues[$Matches[1]] = $Matches[2]
    }
}
$smtpMapping = [ordered]@{
    SPRING_MAIL_HOST = 'SPRING_MAIL_HOST'
    SPRING_MAIL_PORT = 'SPRING_MAIL_PORT'
    SPRING_MAIL_USERNAME = 'SPRING_MAIL_USERNAME'
    SPRING_MAIL_PASSWORD = 'SPRING_MAIL_PASSWORD'
    MAIL_FROM = 'MAIL_FROM'
    DEMO_NOTIFICATION_EMAIL = 'DEMO_NOTIFICATION_EMAIL'
    SMTP_AUTH = 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH'
    SMTP_STARTTLS = 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE'
    SMTP_SSL = 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE'
}
foreach ($smtpRequired in @('SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD', 'MAIL_FROM')) {
    if (-not $smtpValues.ContainsKey($smtpRequired) -or [string]::IsNullOrWhiteSpace($smtpValues[$smtpRequired])) {
        throw "Missing local SMTP setting: $smtpRequired"
    }
}
$smtpOutput = @('# SMTP-only import. Contains credentials; never commit or paste into chat.')
foreach ($smtpEntry in $smtpMapping.GetEnumerator()) {
    $smtpValue = if ($smtpValues.ContainsKey($smtpEntry.Key)) { $smtpValues[$smtpEntry.Key] } elseif ($smtpValues.ContainsKey($smtpEntry.Value)) { $smtpValues[$smtpEntry.Value] } elseif ($smtpEntry.Key.StartsWith('SMTP_')) { 'false' } else { '' }
    if ($smtpValue -match '[\r\n\x00]') { throw "Invalid multiline SMTP value: $($smtpEntry.Key)" }
    $smtpOutput += $smtpEntry.Key + '=' + $smtpValue
}
$smtpDestinationPath = [System.IO.Path]::GetFullPath($Destination)
[System.IO.File]::WriteAllLines($smtpDestinationPath, $smtpOutput, [System.Text.UTF8Encoding]::new($false))
Write-Output 'SMTP import created in deploy/vps/.env.smtp. Upload only over SSH/SFTP; delete after importing.'
