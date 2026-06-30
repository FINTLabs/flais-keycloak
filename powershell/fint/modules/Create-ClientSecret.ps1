param(
    [Parameter(Mandatory = $true)]
    [string]$ApplicationObjectId,

    [Parameter(Mandatory = $true)]
    [string]$DisplayName,

    [Parameter(Mandatory = $true)]
    [int]$ValidityDays
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/../helpers/GraphRetry.ps1"
. "$PSScriptRoot/../helpers/RequiredScopes.ps1"
. "$PSScriptRoot/../helpers/ConsoleHelpers.ps1"

Assert-MgContextHasExactlyRequiredScopes -RequiredScopes @(
    "Application.ReadWrite.OwnedBy",
    "Synchronization.ReadWrite.All"
)

if ([string]::IsNullOrWhiteSpace($DisplayName)) {
    throw "Client secret display name is required."
}

if ($ValidityDays -lt 1) {
    throw "Client secret validity must be at least 1 day."
}

$endDateTime = [DateTimeOffset]::UtcNow.AddDays($ValidityDays).ToString("yyyy-MM-ddTHH:mm:ssZ")

$body = @{
    passwordCredential = @{
        displayName = $DisplayName
        endDateTime = $endDateTime
    }
}

Write-SectionTitle "Creating client secret"
Write-Host "Application ObjectId: $ApplicationObjectId"
Write-Host "Display name:         $DisplayName"
Write-Host "Valid until UTC:      $endDateTime"
Write-Host ""
Write-Host "Important: the secret value is only returned once. Copy it before closing this window." -ForegroundColor Yellow

$result = Invoke-GraphWithRetry `
    -Method POST `
    -Uri "https://graph.microsoft.com/v1.0/applications/$ApplicationObjectId/addPassword" `
    -BodyJson ($body | ConvertTo-Json -Depth 10) `
    -MaxAttempts 1

Write-SectionTitle "Client secret created" -Color Green
Write-Host "Key ID:          $($result.keyId)"
Write-Host "Display name:    $DisplayName"
Write-Host "Valid until UTC: $($result.endDateTime)"
Write-Host "Secret value:    $($result.secretText)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Store the secret value securely now. It cannot be retrieved later from Microsoft Graph." -ForegroundColor Yellow

[pscustomobject]@{
    KeyId          = $result.keyId
    DisplayName    = $DisplayName
    EndDateTime    = $result.endDateTime
    SecretText     = $result.secretText
}
