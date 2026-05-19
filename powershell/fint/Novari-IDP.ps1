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

. "$PSScriptRoot/helpers/GenericHelpers.ps1"
. "$PSScriptRoot/helpers/ConsoleHelpers.ps1"
. "$PSScriptRoot/helpers/GraphRetry.ps1"
. "$PSScriptRoot/helpers/RequiredScopes.ps1"
. "$PSScriptRoot/helpers/GraphContext.ps1"
. "$PSScriptRoot/helpers/EnterpriseApplicationHelpers.ps1"

$CreateEnterpriseAppScript = "$PSScriptRoot/modules/Create-EnterpriseApplication.ps1"
$CreateClaimsMappingPolicyScript = "$PSScriptRoot/modules/Create-ClaimsMappingPolicy.ps1"
$ConfigureEnterpriseAppScript = "$PSScriptRoot/modules/Configure-Application.ps1"
$ConfigureScimScript = "$PSScriptRoot/modules/Configure-ScimProvisioning.ps1"

$script:LastEnterpriseApplicationResult = $null


function Invoke-CreateEnterpriseApplication {
    $displayName = Read-RequiredValue "Enterprise Application display name"

    Write-SectionTitle "Creating Enterprise Application"

    $result = Invoke-ScriptAndConvertFromJson `
        -Path $CreateEnterpriseAppScript `
        -Parameters @{
            DisplayName = $displayName
        }

    Assert-NovariEnterpriseApplicationResult -ApplicationResult $result

    Write-SectionTitle "Enterprise Application created" -Color Green
    Write-EnterpriseApplicationResult -ApplicationResult $result

    return $result
}

function Get-ExistingEnterpriseApplication {
    Write-SectionTitle "Connect existing Enterprise Application"

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

    $servicePrincipal = Get-SingleGraphObject `
        -Items @(Get-MgServicePrincipal -Filter "appId eq '$applicationAppId'" -ErrorAction Stop) `
        -NoMatchMessage "No Enterprise Application found with Application AppId '$applicationAppId'." `
        -MultipleMatchesMessage "Multiple service principals found for Application AppId '$applicationAppId'."

    Assert-NovariEnterpriseApplicationDisplayName `
        -DisplayName $servicePrincipal.DisplayName `
        -Identifier $servicePrincipal.Id

    $application = Get-SingleGraphObject `
        -Items @(Get-MgApplication -Filter "appId eq '$applicationAppId'" -ErrorAction Stop) `
        -NoMatchMessage "Found service principal '$($servicePrincipal.DisplayName)', but could not find matching application registration with AppId '$applicationAppId'." `
        -MultipleMatchesMessage "Multiple application registrations found for Application AppId '$applicationAppId'."

    $result = New-EnterpriseApplicationResult `
        -Application $application `
        -ServicePrincipal $servicePrincipal

    Write-SectionTitle "Existing Enterprise Application connected" -Color Green
    Write-EnterpriseApplicationResult -ApplicationResult $result

    return $result
}

function Show-CurrentEnterpriseApplication {
    if (-not $script:LastEnterpriseApplicationResult) {
        Write-Host ""
        Write-Host "No Enterprise Application has been created or selected in this session." -ForegroundColor Yellow
        Write-Host "Run option 1 first, or connect existing on startup."
        return
    }

    Write-SectionTitle "Current Enterprise Application"
    Write-EnterpriseApplicationResult -ApplicationResult $script:LastEnterpriseApplicationResult
}

function Invoke-ConfigureEnterpriseApplication {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )

    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    $redirectUri = Read-RequiredValue "Keycloak redirect URI for IDP"
    Write-SectionTitle "Configuring FINT Enterprise Application"

    $result = Invoke-ScriptAndConvertFromJson `
        -Path $ConfigureEnterpriseAppScript `
        -Parameters @{
            ApplicationObjectId      = $ExistingApplicationResult.ApplicationObjectId
            ApplicationAppId         = $ExistingApplicationResult.ApplicationAppId
            ServicePrincipalObjectId = $ExistingApplicationResult.ServicePrincipalObjectId
            RedirectUri              = $redirectUri
            AcceptMappedClaims       = $true
        }

    Write-SectionTitle "Enterprise Application configured" -Color Green
    Write-ObjectProperties `
        -InputObject $result `
        -Labels ([ordered]@{
            "Application ObjectId"      = "ApplicationObjectId"
            "Application AppId"         = "ApplicationAppId"
            "ServicePrincipal ObjectId" = "ServicePrincipalObjectId"
            "Redirect URI"              = "RedirectUri"
        })

    return $result
}

function Invoke-CreateClaimsMappingPolicy {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )

    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    $displayName = Read-DefaultedValue `
        -Prompt "Claims Mapping Policy display name" `
        -DefaultValue "$($ExistingApplicationResult.DisplayName) - FINT Claims Mapping Policy"

    $employeeIdSource = Read-DefaultedValue `
        -Prompt "Employee ID source attribute" `
        -DefaultValue "extensionAttribute10"

    $studentNumberSource = Read-DefaultedValue `
        -Prompt "Student number source attribute" `
        -DefaultValue "extensionAttribute9"

    Write-SectionTitle "Creating and assigning Claims Mapping Policy"

    $result = Invoke-ScriptAndConvertFromJson `
        -Path $CreateClaimsMappingPolicyScript `
        -Parameters @{
            ServicePrincipalObjectId     = $ExistingApplicationResult.ServicePrincipalObjectId
            DisplayName                  = $displayName
            EmployeeIdSourceAttribute    = $employeeIdSource
            StudentNumberSourceAttribute = $studentNumberSource
        }

    Write-SectionTitle "Claims Mapping Policy created and assigned" -Color Green
    Write-ObjectProperties `
        -InputObject $result `
        -Labels ([ordered]@{
            "ServicePrincipal ObjectId"       = "ServicePrincipalObjectId"
            "ClaimsMappingPolicy ObjectId"    = "ClaimsMappingPolicyObjectId"
            "ClaimsMappingPolicy Name"        = "ClaimsMappingPolicyName"
            "Employee ID source attribute"    = "EmployeeIdSourceAttribute"
            "Student number source attribute" = "StudentNumberSourceAttribute"
        })

    return $result
}

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
    $employeeIdSource = Read-DefaultedValue `
        -Prompt "Employee ID source attribute" `
        -DefaultValue "extensionAttribute10"

    $studentNumberSource = Read-DefaultedValue `
        -Prompt "Student number source attribute" `
        -DefaultValue "extensionAttribute9"

    Write-SectionTitle "Configuring FINT SCIM provisioning"

    $result = Invoke-ScriptAndConvertFromJson `
        -Path $ConfigureScimScript `
        -Parameters @{
            ServicePrincipalObjectId     = $servicePrincipalObjectId
            TenantUrl                    = $tenantUrl
            SecretToken                  = ""
            ProvisionStatus              = "On"
            EmployeeIdSourceAttribute    = $employeeIdSource
            StudentNumberSourceAttribute = $studentNumberSource
        }

    Write-SectionTitle "SCIM provisioning configured" -Color Green
    Write-ObjectProperties `
        -InputObject $result `
        -Labels ([ordered]@{
            "ServicePrincipal ObjectId" = "ServicePrincipalObjectId"
            "Sync TemplateId"           = "SyncTemplateId"
            "Sync JobId"                = "SyncJobId"
            "Tenant URL"                = "TenantUrl"
            "Provision Status"          = "ProvisionStatus"
        })

    return $result
}

. "$PSScriptRoot/helpers/Header.ps1"
. "$PSScriptRoot/helpers/Menu.ps1"

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
