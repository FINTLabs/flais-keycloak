param(
    [Parameter(Mandatory = $true)]
    [string]$ApplicationObjectId,

    [Parameter(Mandatory = $true)]
    [string]$ApplicationAppId,

    [Parameter(Mandatory = $true)]
    [string]$ServicePrincipalObjectId,

    [Parameter(Mandatory = $true)]
    [string]$RedirectUri,

    [Parameter(Mandatory = $false)]
    [bool]$AcceptMappedClaims = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/../helpers/GraphRetry.ps1"

Assert-MgContextHasExactlyRequiredScopes -RequiredScopes @(
    "Application.ReadWrite.All",
    "Policy.Read.All",
    "Policy.ReadWrite.ApplicationConfiguration",
    "Synchronization.ReadWrite.All"
)

function Set-ApplicationRegistrationConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApplicationObjectId
    )

    $graphAppId = "00000003-0000-0000-c000-000000000000"

    $app = Invoke-GraphWithRetry `
        -Method GET `
        -Uri "https://graph.microsoft.com/v1.0/applications/$ApplicationObjectId`?`$select=id,appRoles"

    $appRoles = @()
    if ($app.appRoles) {
        $appRoles = @($app.appRoles)
    }

    $userRole = $appRoles | Where-Object {
        $_.displayName -eq "User" -or $_.value -eq "User" -or $_.value -eq "user"
    } | Select-Object -First 1

    if ($userRole) {
        $userRole.value = "user"
    }
    else {
        throw "Could not find the default User app role on application $ApplicationObjectId."
    }

    $body = @{
        api                    = @{
            acceptMappedClaims = $AcceptMappedClaims
        }
        appRoles               = $appRoles
        requiredResourceAccess = @(
            @{
                resourceAppId  = $graphAppId
                resourceAccess = @(
                    @{
                        id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
                        type = "Scope"
                    },
                    @{
                        id   = "14dad69e-099b-42c9-810b-d002981feec1"
                        type = "Scope"
                    }
                )
            }
        )
        optionalClaims         = @{
            idToken     = @(
                @{
                    name                 = "upn"
                    source               = $null
                    essential            = $false
                    additionalProperties = @()
                }
            )
            accessToken = @()
            saml2Token  = @()
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($RedirectUri)) {
        $body.web = @{
            redirectUris = @($RedirectUri)
        }
    }

    Invoke-GraphWithRetry `
        -Method PATCH `
        -Uri "https://graph.microsoft.com/v1.0/applications/$ApplicationObjectId" `
        -BodyJson ($body | ConvertTo-Json -Depth 30) | Out-Null
}

function Set-EnterpriseApplicationConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServicePrincipalObjectId
    )

    $sp = Invoke-GraphWithRetry `
        -Method GET `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$ServicePrincipalObjectId`?`$select=id,tags,appRoles"

    $tags = @()
    if ($sp.tags) {
        $tags = @($sp.tags)
    }

    if ($tags -notcontains "HideApp") {
        $tags += "HideApp"
    }

    $appRoles = @()
    if ($sp.appRoles) {
        $appRoles = @($sp.appRoles)
    }

    $defaultAppRole = $appRoles | Where-Object {
        $_.value -eq "msiam_access" -or
        $_.displayName -eq "msiam_access" -or
        $_.description -eq "msiam_access"
    } | Select-Object -First 1

    if ($defaultAppRole) {
        Write-Host "Found default msiam_access app role on Enterprise Application. Disabling it..."

        $appRoles = @($appRoles | ForEach-Object {
                if ($_.id -eq $defaultAppRole.id) {
                    $_.isEnabled = $false
                }

                $_
            })
    }

    $body = @{
        accountEnabled            = $true
        appRoleAssignmentRequired = $true
        tags                      = $tags
        appRoles                  = $appRoles
    }

    Invoke-GraphWithRetry `
        -Method PATCH `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$ServicePrincipalObjectId" `
        -BodyJson ($body | ConvertTo-Json -Depth 20) | Out-Null
}

Set-ApplicationRegistrationConfig `
    -ApplicationObjectId $ApplicationObjectId

Set-EnterpriseApplicationConfig `
    -ServicePrincipalObjectId $ServicePrincipalObjectId
