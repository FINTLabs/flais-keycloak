param(
    [Parameter(Mandatory = $true)]
    [string]$ServicePrincipalObjectId,

    [Parameter(Mandatory = $true)]
    [string]$DisplayName,

    [Parameter(Mandatory = $true)]
    [string]$EmployeeIdSourceAttribute,

    [Parameter(Mandatory = $true)]
    [string]$StudentNumberSourceAttribute,

    [Parameter(Mandatory = $false)]
    [int]$MaxRetries = 10,

    [Parameter(Mandatory = $false)]
    [int]$RetryDelaySeconds = 5
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

function New-FintClaimsMappingDefinition {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EmployeeIdSource,

        [Parameter(Mandatory = $true)]
        [string]$StudentNumberSource
    )

    return @{
        ClaimsMappingPolicy = @{
            Version              = 1
            IncludeBasicClaimSet = "true"
            ClaimsSchema         = @(
                @{
                    Source       = "user"
                    ID           = $EmployeeIdSource
                    JwtClaimType = "employee_id"
                },
                @{
                    Source       = "user"
                    ID           = $StudentNumberSource
                    JwtClaimType = "student_number"
                }
            )
        }
    } | ConvertTo-Json -Depth 10 -Compress
}

function Get-FintServicePrincipalClaimsMappingPolicies {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServicePrincipalId,

        [Parameter(Mandatory = $true)]
        [int]$MaxAttempts,

        [Parameter(Mandatory = $true)]
        [int]$DelaySeconds
    )

    return Invoke-GraphWithRetry `
        -Method "GET" `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$ServicePrincipalId/claimsMappingPolicies" `
        -MaxAttempts $MaxAttempts `
        -InitialDelaySeconds $DelaySeconds
}

function New-FintClaimsMappingPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DefinitionJson,

        [Parameter(Mandatory = $true)]
        [string]$DisplayName,

        [Parameter(Mandatory = $true)]
        [int]$MaxAttempts,

        [Parameter(Mandatory = $true)]
        [int]$DelaySeconds
    )

    $bodyJson = @{
        definition  = @($DefinitionJson)
        displayName = $DisplayName
    } | ConvertTo-Json -Depth 20 -Compress

    return Invoke-GraphWithRetry `
        -Method "POST" `
        -Uri "https://graph.microsoft.com/v1.0/policies/claimsMappingPolicies" `
        -BodyJson $bodyJson `
        -MaxAttempts $MaxAttempts `
        -InitialDelaySeconds $DelaySeconds
}

function Update-FintClaimsMappingPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PolicyId,

        [Parameter(Mandatory = $true)]
        [string]$DefinitionJson,

        [Parameter(Mandatory = $true)]
        [string]$DisplayName,

        [Parameter(Mandatory = $true)]
        [int]$MaxAttempts,

        [Parameter(Mandatory = $true)]
        [int]$DelaySeconds
    )

    $bodyJson = @{
        definition  = @($DefinitionJson)
        displayName = $DisplayName
    } | ConvertTo-Json -Depth 20 -Compress

    Invoke-GraphWithRetry `
        -Method "PATCH" `
        -Uri "https://graph.microsoft.com/v1.0/policies/claimsMappingPolicies/$PolicyId" `
        -BodyJson $bodyJson `
        -MaxAttempts $MaxAttempts `
        -InitialDelaySeconds $DelaySeconds `
    | Out-Null

    return Invoke-GraphWithRetry `
        -Method "GET" `
        -Uri "https://graph.microsoft.com/v1.0/policies/claimsMappingPolicies/$PolicyId" `
        -MaxAttempts $MaxAttempts `
        -InitialDelaySeconds $DelaySeconds
}

function Add-FintClaimsMappingPolicyToServicePrincipal {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServicePrincipalId,

        [Parameter(Mandatory = $true)]
        [string]$PolicyId,

        [Parameter(Mandatory = $true)]
        [int]$MaxAttempts,

        [Parameter(Mandatory = $true)]
        [int]$DelaySeconds
    )

    $bodyJson = @{
        "@odata.id" = "https://graph.microsoft.com/v1.0/policies/claimsMappingPolicies/$PolicyId"
    } | ConvertTo-Json -Depth 10 -Compress

    Invoke-GraphWithRetry `
        -Method "POST" `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$ServicePrincipalId/claimsMappingPolicies/`$ref" `
        -BodyJson $bodyJson `
        -MaxAttempts $MaxAttempts `
        -InitialDelaySeconds $DelaySeconds `
    | Out-Null

    Write-Host "Assigned Claims Mapping Policy $PolicyId to service principal $ServicePrincipalId."
}

$claimsMapping = New-FintClaimsMappingDefinition `
    -EmployeeIdSource $EmployeeIdSourceAttribute `
    -StudentNumberSource $StudentNumberSourceAttribute

$assignedPoliciesResponse = Get-FintServicePrincipalClaimsMappingPolicies `
    -ServicePrincipalId $ServicePrincipalObjectId `
    -MaxAttempts $MaxRetries `
    -DelaySeconds $RetryDelaySeconds

$assignedClaimsMappingPolicies = @($assignedPoliciesResponse.value)

if ($assignedClaimsMappingPolicies.Count -gt 1) {
    throw "Service principal $ServicePrincipalObjectId already has multiple Claims Mapping Policies assigned. Refusing to choose one automatically."
}

if ($assignedClaimsMappingPolicies.Count -eq 1) {
    $existingPolicy = $assignedClaimsMappingPolicies[0]

    Write-Host "Service principal $ServicePrincipalObjectId already has Claims Mapping Policy $($existingPolicy.id). Updating existing policy instead of creating a new one."

    $policy = Update-FintClaimsMappingPolicy `
        -PolicyId $existingPolicy.id `
        -DefinitionJson $claimsMapping `
        -DisplayName $DisplayName `
        -MaxAttempts $MaxRetries `
        -DelaySeconds $RetryDelaySeconds

    Write-Host "Updated Claims Mapping Policy:"
    Write-Host "  Name: $($policy.displayName)"
    Write-Host "  Id:   $($policy.id)"
}
else {
    $policy = New-FintClaimsMappingPolicy `
        -DefinitionJson $claimsMapping `
        -DisplayName $DisplayName `
        -MaxAttempts $MaxRetries `
        -DelaySeconds $RetryDelaySeconds

    Write-Host "Created Claims Mapping Policy:"
    Write-Host "  Name: $($policy.displayName)"
    Write-Host "  Id:   $($policy.id)"

    Add-FintClaimsMappingPolicyToServicePrincipal `
        -ServicePrincipalId $ServicePrincipalObjectId `
        -PolicyId $policy.id `
        -MaxAttempts $MaxRetries `
        -DelaySeconds $RetryDelaySeconds
}
