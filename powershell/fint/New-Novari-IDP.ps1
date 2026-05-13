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

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$CreateEnterpriseAppScript = Join-Path $ScriptRoot "Create-EnterpriseApplication.ps1"
$CreateClaimsMappingPolicyScript = Join-Path $ScriptRoot "Create-ClaimsMappingPolicy.ps1"
$ConfigureEnterpriseAppScript = Join-Path $ScriptRoot "Configure-EnterpriseApplication.ps1"
$ConfigureScimScript = Join-Path $ScriptRoot "Configure-ScimProvisioning.ps1"

$HeaderScript = Join-Path $ScriptRoot "helpers/Header.ps1"
$MenuScript = Join-Path $ScriptRoot "helpers/Menu.ps1"

$script:LastEnterpriseApplicationResult = $null

# -------------------------------------------------------------------------------------------------
# Generic helpers
# -------------------------------------------------------------------------------------------------

function Test-ScriptExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        throw "Required script not found: $Path"
    }
}

function Read-RequiredValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    do {
        $value = Read-Host $Prompt

        if ([string]::IsNullOrWhiteSpace($value)) {
            Write-Host "Value is required." -ForegroundColor Yellow
        }
    } while ([string]::IsNullOrWhiteSpace($value))

    return $value
}

function Read-DefaultedValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt,

        [Parameter(Mandatory = $true)]
        [string]$DefaultValue
    )

    $value = Read-Host "$Prompt [$DefaultValue]"

    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }

    return $value
}

function Test-Yes {
    param(
        [Parameter(Mandatory = $false)]
        [string]$Value
    )

    return $Value -in @("y", "Y", "yes", "Yes", "YES")
}

# -------------------------------------------------------------------------------------------------
# Microsoft Graph helpers
# -------------------------------------------------------------------------------------------------

function Connect-ToGraph {
    Write-Host ""
    Write-Host "Microsoft Graph app-only login" -ForegroundColor Cyan
    Write-Host "------------------------------"

    Import-Module Microsoft.Graph.Authentication -ErrorAction Stop

    Disconnect-MgGraph -ErrorAction SilentlyContinue | Out-Null

    $tenantId = Read-RequiredValue "Tenant ID"
    $clientId = Read-RequiredValue "Client ID"
    $clientSecretSecure = Read-Host "Client Secret" -AsSecureString

    if (-not $clientSecretSecure -or $clientSecretSecure.Length -eq 0) {
        throw "Client Secret is required."
    }

    $cred = New-Object System.Management.Automation.PSCredential($clientId, $clientSecretSecure)

    Connect-MgGraph `
        -TenantId $tenantId `
        -ClientSecretCredential $cred `
        -NoWelcome

    $ctx = Get-MgContext

    if (-not $ctx) {
        throw "Graph login failed. No Microsoft Graph context was created."
    }

    Write-Host ""
    Write-Host "Connected to Microsoft Graph." -ForegroundColor Green
    Write-Host "Tenant:    $($ctx.TenantId)"
    Write-Host "Client ID: $($ctx.ClientId)"
    Write-Host "Auth type: $($ctx.AuthType)"
}

function Show-GraphContext {
    $ctx = Get-MgContext

    if (-not $ctx) {
        Write-Host "Not connected to Microsoft Graph." -ForegroundColor Yellow
        return
    }

    Write-Host ""
    Write-Host "Current Graph Context" -ForegroundColor Cyan
    Write-Host "---------------------"
    Write-Host "Tenant ID: $($ctx.TenantId)"
    Write-Host "Client ID: $($ctx.ClientId)"
    Write-Host "Auth Type: $($ctx.AuthType)"
    Write-Host "Scopes:   $($ctx.Scopes | Sort-Object -Unique)"
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
        Write-Host "Run option 1 first, or enter the values manually when using the other options."
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

    Test-ScriptExists $ConfigureEnterpriseAppScript

    if ($ExistingApplicationResult) {
        $applicationObjectId = $ExistingApplicationResult.ApplicationObjectId
        $applicationAppId = $ExistingApplicationResult.ApplicationAppId
        $servicePrincipalObjectId = $ExistingApplicationResult.ServicePrincipalObjectId
    }
    else {
        $applicationObjectId = Read-RequiredValue "Application ObjectId"
        $applicationAppId = Read-RequiredValue "Application AppId"
        $servicePrincipalObjectId = Read-RequiredValue "ServicePrincipal ObjectId"
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

    Test-ScriptExists $CreateClaimsMappingPolicyScript

    if ($ExistingApplicationResult) {
        $servicePrincipalObjectId = $ExistingApplicationResult.ServicePrincipalObjectId
        $defaultPolicyDisplayName = "$($ExistingApplicationResult.DisplayName) - FINT Claims Mapping Policy"
    }
    else {
        $servicePrincipalObjectId = Read-RequiredValue "ServicePrincipal ObjectId"
        $defaultPolicyDisplayName = "FINT Claims Mapping Policy"
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

    Test-ScriptExists $ConfigureScimScript

    if ([string]::IsNullOrWhiteSpace($ExistingServicePrincipalObjectId)) {
        $servicePrincipalObjectId = Read-RequiredValue "ServicePrincipal ObjectId"
    }
    else {
        $servicePrincipalObjectId = $ExistingServicePrincipalObjectId
    }

    $tenantUrl = Read-RequiredValue "SCIM Tenant URL / BaseAddress. Example: https://keycloak.prod.infra.flais.no/realms/fint/scim/v2/<org-id>/"
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

Test-ScriptExists $HeaderScript
Test-ScriptExists $MenuScript

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
