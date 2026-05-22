param(
    [Parameter(Mandatory = $true)]
    [string]$DisplayName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/../helpers/GraphRetry.ps1"
. "$PSScriptRoot/../helpers/RequiredScopes.ps1"

Assert-MgContextHasExactlyRequiredScopes -RequiredScopes @(
    "Application.ReadWrite.All",
    "Policy.Read.All",
    "Policy.ReadWrite.ApplicationConfiguration",
    "Synchronization.ReadWrite.All"
)

$templateIdNonGallery = "8adf8e6e-67b2-4cf2-a259-e3dc5476c621"

$inst = Invoke-GraphWithRetry `
    -Method POST `
    -Uri "https://graph.microsoft.com/v1.0/applicationTemplates/$templateIdNonGallery/instantiate" `
    -BodyJson (@{ displayName = $DisplayName } | ConvertTo-Json)

$app = $inst.application
$sp = $inst.servicePrincipal

Write-Host "Created Enterprise Application:"
Write-Host "  Application ObjectId:    $($app.id)"
Write-Host "  Application AppId:       $($app.appId)"
Write-Host "  ServicePrincipal Id:     $($sp.id)"

[pscustomobject]@{
    DisplayName              = $DisplayName
    ApplicationObjectId      = $app.id
    ApplicationAppId         = $app.appId
    ServicePrincipalObjectId = $sp.id
}
