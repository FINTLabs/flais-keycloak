<#
.SYNOPSIS
  Entry menu for creating a FINT Entra Enterprise Application and configuring SCIM provisioning.

.DESCRIPTION
  Implements the Entra ID part of config/fint:
    - Bootstrap/startup context:
        - Creates non-gallery Enterprise Application
        - Optionally connects to an existing Enterprise Application
        - Creates Claims Mapping Policy as a separate operation
        - Assigns Claims Mapping Policy to an existing service principal

    - Configure context:
        - App Registration settings: Web redirect URI, acceptMappedClaims, Graph delegated User.Read/profile
        - Enterprise Application settings: enabled, assignment required, hidden from users

    - SCIM provisioning:
        - bearer auth
        - assigned users/groups scope
        - users on/groups off
        - FINT attributes/mappings
        - accidental delete threshold 500
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# -------------------------------------------------------------------------------------------------
# Script paths
# -------------------------------------------------------------------------------------------------

. "$PSScriptRoot/helpers/GenericHelpers.ps1"
. "$PSScriptRoot/helpers/GraphRetry.ps1"
. "$PSScriptRoot/helpers/RequiredScopes.ps1"
. "$PSScriptRoot/helpers/GraphContext.ps1"

$CreateEnterpriseAppScript = "$PSScriptRoot/modules/Create-EnterpriseApplication.ps1"
$CreateClaimsMappingPolicyScript = "$PSScriptRoot/modules/Create-ClaimsMappingPolicy.ps1"
$ConfigureEnterpriseAppScript = "$PSScriptRoot/modules/Configure-Application.ps1"
$ConfigureScimScript = "$PSScriptRoot/modules/Configure-ScimProvisioning.ps1"

$HeaderScript = "$PSScriptRoot/helpers/Header.ps1"
$MenuScript = "$PSScriptRoot/helpers/Menu.ps1"

$script:LastEnterpriseApplicationResult = $null

# -------------------------------------------------------------------------------------------------
# Safety guard
# -------------------------------------------------------------------------------------------------

function Assert-NovariEnterpriseApplicationDisplayName {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$DisplayName,

        [Parameter(Mandatory = $false)]
        [string]$Identifier = "unknown"
    )

    if ([string]::IsNullOrWhiteSpace($DisplayName)) {
        throw "Refusing to continue: connected Enterprise Application '$Identifier' has no display name. Expected a name containing 'novari'."
    }

    if ($DisplayName.IndexOf("novari", [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Refusing to continue: connected Enterprise Application '$DisplayName' ('$Identifier') does not contain 'novari' in its name."
    }
}

function Assert-NovariEnterpriseApplicationResult {
    param(
        [Parameter(Mandatory = $true)]
        [object]$ApplicationResult
    )

    if (-not $ApplicationResult) {
        throw "No Enterprise Application is connected in this session. Create or connect an Enterprise Application before running this operation."
    }

    Assert-NovariEnterpriseApplicationDisplayName `
        -DisplayName $ApplicationResult.DisplayName `
        -Identifier $ApplicationResult.ServicePrincipalObjectId
}

function Assert-NovariServicePrincipalByObjectId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServicePrincipalObjectId
    )

    Import-Module Microsoft.Graph.Applications -ErrorAction Stop

    $servicePrincipal = Get-MgServicePrincipal `
        -ServicePrincipalId $ServicePrincipalObjectId `
        -Property "id,displayName" `
        -ErrorAction Stop

    Assert-NovariEnterpriseApplicationDisplayName `
        -DisplayName $servicePrincipal.DisplayName `
        -Identifier $servicePrincipal.Id

    return $servicePrincipal
}

# -------------------------------------------------------------------------------------------------
# Enterprise Application operations
# -------------------------------------------------------------------------------------------------

function Get-ExistingEnterpriseApplication {
    Write-Host ""
    Write-Host "Connect existing Enterprise Application" -ForegroundColor Cyan
    Write-Host "---------------------------------------"

    $answer = Read-DefaultedValue `
        -Prompt "Do you want to connect an existing Application? y/n" `
        -DefaultValue "n"

    if (-not (Test-Yes $answer)) {
        return $null
    }

    Import-Module Microsoft.Graph.Applications -ErrorAction Stop

    $applicationAppId = Read-RequiredValue "Application AppId"

    if ($applicationAppId -notmatch '^[0-9a-fA-F-]{36}$') {
        throw "Invalid Application AppId '$applicationAppId'. Expected a GUID."
    }

    $servicePrincipalMatches = @(Get-MgServicePrincipal `
            -Filter "appId eq '$applicationAppId'" `
            -ErrorAction Stop)

    if (-not $servicePrincipalMatches -or $servicePrincipalMatches.Count -eq 0) {
        throw "No Enterprise Application found with Application AppId '$applicationAppId'."
    }

    if ($servicePrincipalMatches.Count -gt 1) {
        throw "Multiple service principals found for Application AppId '$applicationAppId'."
    }

    $servicePrincipal = $servicePrincipalMatches[0]

    Assert-NovariEnterpriseApplicationDisplayName `
        -DisplayName $servicePrincipal.DisplayName `
        -Identifier $servicePrincipal.Id

    $applicationMatches = @(Get-MgApplication `
            -Filter "appId eq '$applicationAppId'" `
            -ErrorAction Stop)

    if (-not $applicationMatches -or $applicationMatches.Count -eq 0) {
        throw "Found service principal '$($servicePrincipal.DisplayName)', but could not find matching application registration with AppId '$applicationAppId'."
    }

    if ($applicationMatches.Count -gt 1) {
        throw "Multiple application registrations found for Application AppId '$applicationAppId'."
    }

    $application = $applicationMatches[0]

    $result = [pscustomobject]@{
        DisplayName              = $servicePrincipal.DisplayName
        ApplicationObjectId      = $application.Id
        ApplicationAppId         = $application.AppId
        ServicePrincipalObjectId = $servicePrincipal.Id
    }

    Write-Host ""
    Write-Host "Existing Enterprise Application connected." -ForegroundColor Green
    Write-Host "Display Name:              $($result.DisplayName)"
    Write-Host "Application ObjectId:      $($result.ApplicationObjectId)"
    Write-Host "Application AppId:         $($result.ApplicationAppId)"
    Write-Host "ServicePrincipal ObjectId: $($result.ServicePrincipalObjectId)"

    return $result
}

function Show-CurrentEnterpriseApplication {
    if (-not $script:LastEnterpriseApplicationResult) {
        Write-Host ""
        Write-Host "No Enterprise Application has been created or selected in this session." -ForegroundColor Yellow
        Write-Host "Run option 1 first, or connect existing on startup."
        return
    }

    Write-Host ""
    Write-Host "Current Enterprise Application" -ForegroundColor Cyan
    Write-Host "------------------------------"
    Write-Host "Display Name:              $($script:LastEnterpriseApplicationResult.DisplayName)"
    Write-Host "Application ObjectId:      $($script:LastEnterpriseApplicationResult.ApplicationObjectId)"
    Write-Host "Application AppId:         $($script:LastEnterpriseApplicationResult.ApplicationAppId)"
    Write-Host "ServicePrincipal ObjectId: $($script:LastEnterpriseApplicationResult.ServicePrincipalObjectId)"
}

function Invoke-ConfigureEnterpriseApplication {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )

    if ($ExistingApplicationResult) {
        Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

        $applicationObjectId = $ExistingApplicationResult.ApplicationObjectId
        $applicationAppId = $ExistingApplicationResult.ApplicationAppId
        $servicePrincipalObjectId = $ExistingApplicationResult.ServicePrincipalObjectId
    }
    else {
        throw "No existing application result provided. This function should only be called with an existing application."
    }

    $redirectUri = Read-RequiredValue "Keycloak redirect URI for IDP"

    Write-Host ""
    Write-Host "Configuring FINT Enterprise Application..." -ForegroundColor Cyan

    $configureParams = @{
        ApplicationObjectId      = $applicationObjectId
        ApplicationAppId         = $applicationAppId
        ServicePrincipalObjectId = $servicePrincipalObjectId
        AcceptMappedClaims       = $true
    }

    if ($redirectUri) {
        $configureParams.RedirectUri = $redirectUri
    }

    $resultJson = & $ConfigureEnterpriseAppScript @configureParams
    $result = $resultJson | ConvertFrom-Json

    Write-Host ""
    Write-Host "Enterprise Application configured." -ForegroundColor Green
    Write-Host "Application ObjectId:      $($result.ApplicationObjectId)"
    Write-Host "Application AppId:         $($result.ApplicationAppId)"
    Write-Host "ServicePrincipal ObjectId: $($result.ServicePrincipalObjectId)"
    Write-Host "Redirect URI:              $($result.RedirectUri)"

    return $result
}

# -------------------------------------------------------------------------------------------------
# Claims Mapping Policy operations
# -------------------------------------------------------------------------------------------------

function Invoke-CreateClaimsMappingPolicy {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )

    if ($ExistingApplicationResult) {
        Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

        $servicePrincipalObjectId = $ExistingApplicationResult.ServicePrincipalObjectId
        $defaultPolicyDisplayName = "$($ExistingApplicationResult.DisplayName) - FINT Claims Mapping Policy"
    }
    else {
        throw "No existing application result provided. This function should only be called with an existing application."
    }

    $displayName = Read-DefaultedValue `
        -Prompt "Claims Mapping Policy display name" `
        -DefaultValue $defaultPolicyDisplayName

    $employeeIdSource = Read-DefaultedValue `
        -Prompt "Employee ID source attribute" `
        -DefaultValue "extensionAttribute10"

    $studentNumberSource = Read-DefaultedValue `
        -Prompt "Student number source attribute" `
        -DefaultValue "extensionAttribute9"

    Write-Host ""
    Write-Host "Creating and assigning Claims Mapping Policy..." -ForegroundColor Cyan

    $resultJson = & $CreateClaimsMappingPolicyScript `
        -ServicePrincipalObjectId $servicePrincipalObjectId `
        -DisplayName $displayName `
        -EmployeeIdSourceAttribute $employeeIdSource `
        -StudentNumberSourceAttribute $studentNumberSource

    $result = $resultJson | ConvertFrom-Json

    Write-Host ""
    Write-Host "Claims Mapping Policy created and assigned." -ForegroundColor Green
    Write-Host "ServicePrincipal ObjectId:       $($result.ServicePrincipalObjectId)"
    Write-Host "ClaimsMappingPolicy ObjectId:    $($result.ClaimsMappingPolicyObjectId)"
    Write-Host "ClaimsMappingPolicy Name:        $($result.ClaimsMappingPolicyName)"
    Write-Host "Employee ID source attribute:    $($result.EmployeeIdSourceAttribute)"
    Write-Host "Student number source attribute: $($result.StudentNumberSourceAttribute)"

    return $result
}

# -------------------------------------------------------------------------------------------------
# SCIM provisioning operations
# -------------------------------------------------------------------------------------------------

function Invoke-ConfigureScimProvisioning {
    param(
        [Parameter(Mandatory = $false)]
        [string]$ExistingServicePrincipalObjectId
    )

    if ([string]::IsNullOrWhiteSpace($ExistingServicePrincipalObjectId)) {
        $servicePrincipalObjectId = Read-RequiredValue "ServicePrincipal ObjectId"
    }
    else {
        $servicePrincipalObjectId = $ExistingServicePrincipalObjectId
    }

    $servicePrincipal = Assert-NovariServicePrincipalByObjectId -ServicePrincipalObjectId $servicePrincipalObjectId
    Write-Host "Using Enterprise Application: $($servicePrincipal.DisplayName)" -ForegroundColor Green

    $tenantUrl = Read-RequiredValue "SCIM Tenant URL / BaseAddress"
    $secretToken = ""

    $employeeIdSource = Read-DefaultedValue `
        -Prompt "Employee ID source attribute" `
        -DefaultValue "extensionAttribute10"

    $studentNumberSource = Read-DefaultedValue `
        -Prompt "Student number source attribute" `
        -DefaultValue "extensionAttribute9"

    $provisionStatus = "On"

    Write-Host ""
    Write-Host "Configuring FINT SCIM provisioning..." -ForegroundColor Cyan

    $resultJson = & $ConfigureScimScript `
        -ServicePrincipalObjectId $servicePrincipalObjectId `
        -TenantUrl $tenantUrl `
        -SecretToken $secretToken `
        -ProvisionStatus $provisionStatus `
        -EmployeeIdSourceAttribute $employeeIdSource `
        -StudentNumberSourceAttribute $studentNumberSource

    $result = $resultJson | ConvertFrom-Json

    Write-Host ""
    Write-Host "SCIM provisioning configured." -ForegroundColor Green
    Write-Host "ServicePrincipal ObjectId: $($result.ServicePrincipalObjectId)"
    Write-Host "Sync TemplateId:           $($result.SyncTemplateId)"
    Write-Host "Sync JobId:                $($result.SyncJobId)"
    Write-Host "Tenant URL:                $($result.TenantUrl)"
    Write-Host "Provision Status:          $($result.ProvisionStatus)"

    return $result
}

# -------------------------------------------------------------------------------------------------
# Load UI modules
# -------------------------------------------------------------------------------------------------

. $HeaderScript
. $MenuScript

# -------------------------------------------------------------------------------------------------
# Entrypoint
# -------------------------------------------------------------------------------------------------

try {
    Start-Menu
}
catch {
    Write-Host ""
    Write-Host "ERROR:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
finally {
    Write-Host ""
}
