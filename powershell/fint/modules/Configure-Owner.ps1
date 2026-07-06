param(
    [Parameter(Mandatory = $true)]
    [string]$ApplicationObjectId,

    [Parameter(Mandatory = $true)]
    [string]$ServicePrincipalObjectId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/../helpers/GraphRetry.ps1"
. "$PSScriptRoot/../helpers/RequiredScopes.ps1"

Assert-MgContextHasExactlyRequiredScopes -RequiredScopes @(
    "Application.ReadWrite.OwnedBy"
)

function Test-DirectoryObjectIsOwner {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("applications", "servicePrincipals")]
        [string]$ResourceType,

        [Parameter(Mandatory = $true)]
        [string]$ResourceObjectId,

        [Parameter(Mandatory = $true)]
        [string]$OwnerObjectId
    )

    $ownersResponse = Invoke-GraphWithRetry `
        -Method GET `
        -Uri "https://graph.microsoft.com/v1.0/$ResourceType/$ResourceObjectId/owners?`$select=id"

    $owners = @()
    if ($ownersResponse.value) {
        $owners = @($ownersResponse.value)
    }

    return $null -ne ($owners | Where-Object { $_.id -eq $OwnerObjectId } | Select-Object -First 1)
}

function Add-DirectoryObjectOwnerIfMissing {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("applications", "servicePrincipals")]
        [string]$ResourceType,

        [Parameter(Mandatory = $true)]
        [string]$ResourceObjectId,

        [Parameter(Mandatory = $true)]
        [string]$OwnerObjectId,

        [Parameter(Mandatory = $true)]
        [string]$ResourceDisplayName,

        [Parameter(Mandatory = $true)]
        [string]$OwnerDisplayName
    )

    if (Test-DirectoryObjectIsOwner `
            -ResourceType $ResourceType `
            -ResourceObjectId $ResourceObjectId `
            -OwnerObjectId $OwnerObjectId) {
        Write-Host "$OwnerDisplayName is already an owner of $ResourceDisplayName."
        return
    }

    Write-Host "Adding $OwnerDisplayName as owner of $ResourceDisplayName..."

    $body = @{
        "@odata.id" = "https://graph.microsoft.com/v1.0/directoryObjects/$OwnerObjectId"
    }

    Invoke-GraphWithRetry `
        -Method POST `
        -Uri "https://graph.microsoft.com/v1.0/$ResourceType/$ResourceObjectId/owners/`$ref" `
        -BodyJson ($body | ConvertTo-Json -Depth 5) | Out-Null

    Write-Host "Added $OwnerDisplayName as owner of $ResourceDisplayName." -ForegroundColor Green
}

Add-DirectoryObjectOwnerIfMissing `
    -ResourceType "applications" `
    -ResourceObjectId $ApplicationObjectId `
    -OwnerObjectId $ServicePrincipalObjectId `
    -ResourceDisplayName "Application Registration $ApplicationObjectId" `
    -OwnerDisplayName "Enterprise Application service principal $ServicePrincipalObjectId"

Add-DirectoryObjectOwnerIfMissing `
    -ResourceType "servicePrincipals" `
    -ResourceObjectId $ServicePrincipalObjectId `
    -OwnerObjectId $ServicePrincipalObjectId `
    -ResourceDisplayName "Enterprise Application service principal $ServicePrincipalObjectId" `
    -OwnerDisplayName "Enterprise Application service principal $ServicePrincipalObjectId"
