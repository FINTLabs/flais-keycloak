Import-Module Microsoft.Graph.Authentication -ErrorAction Stop
Import-Module Microsoft.Graph.Applications -ErrorAction Stop

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/helpers/GenericHelpers.ps1"
. "$PSScriptRoot/helpers/ConsoleHelpers.ps1"
. "$PSScriptRoot/helpers/GraphRetry.ps1"
. "$PSScriptRoot/helpers/RequiredScopes.ps1"
. "$PSScriptRoot/helpers/GraphContext.ps1"
. "$PSScriptRoot/helpers/EnterpriseApplicationHelpers.ps1"

$CreateEnterpriseAppScript = "$PSScriptRoot/modules/Create-EnterpriseApplication.ps1"
$CreateClaimsMappingPolicyScript = "$PSScriptRoot/modules/Configure-ClaimsMappingPolicy.ps1"
$ConfigureAppScript = "$PSScriptRoot/modules/Configure-Application.ps1"
$ConfigureScimScript = "$PSScriptRoot/modules/Configure-ScimProvisioning.ps1"
$ConfigureAppRolesScript = "$PSScriptRoot/modules/Configure-AppRoles.ps1"
$ConfigureOwnerScript = "$PSScriptRoot/modules/Configure-Owner.ps1"
$CreateClientSecretScript = "$PSScriptRoot/modules/Create-ClientSecret.ps1"

$script:LastEnterpriseApplicationResult = $null

function Invoke-CreateEnterpriseApplication {
    $displayName = Read-RequiredValue "Enterprise Application display name"

    $result = & $CreateEnterpriseAppScript -DisplayName $displayName

    Assert-NovariEnterpriseApplicationResult -ApplicationResult $result

    Write-EnterpriseApplicationResult -ApplicationResult $result

    return $result
}

function Get-ExistingEnterpriseApplication {
    $answer = Read-DefaultedValue `
        -Prompt "Do you want to connect an existing Application? y/n" `
        -DefaultValue "n"

    if (-not (Test-Yes $answer)) {
        return $null
    }

    $applicationAppId = Read-RequiredValue "Application (client) ID"

    $servicePrincipal = Get-SingleGraphObject `
        -Items @(Get-MgServicePrincipal -Filter "appId eq '$applicationAppId'" -ErrorAction Stop) `
        -NoMatchMessage "No Enterprise Application found with Application (client) ID '$applicationAppId'." `
        -MultipleMatchesMessage "Multiple service principals found for Application (client) ID '$applicationAppId'."

    $application = Get-SingleGraphObject `
        -Items @(Get-MgApplication -Filter "appId eq '$applicationAppId'" -ErrorAction Stop) `
        -NoMatchMessage "Found service principal '$($servicePrincipal.DisplayName)', but could not find matching application registration with Application (client) ID '$applicationAppId'." `
        -MultipleMatchesMessage "Multiple application registrations found for Application (client) ID '$applicationAppId'."

    Assert-NovariEnterpriseApplicationDisplayName `
        -DisplayName $servicePrincipal.DisplayName `
        -Identifier $servicePrincipal.Id

    $result = [pscustomobject]@{
        DisplayName              = $ServicePrincipal.DisplayName
        ApplicationObjectId      = $Application.Id
        ApplicationAppId         = $Application.AppId
        ServicePrincipalObjectId = $ServicePrincipal.Id
    }

    Write-SectionTitle "Existing Enterprise Application connected" -Color Green
    Write-EnterpriseApplicationResult -ApplicationResult $result

    return $result
}

function Invoke-ConfigureApplication {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )
    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    $redirectUri = Read-RequiredValue "Keycloak redirect URI for IDP"

    & $ConfigureAppScript `
        -ApplicationObjectId $ExistingApplicationResult.ApplicationObjectId `
        -ApplicationAppId $ExistingApplicationResult.ApplicationAppId `
        -ServicePrincipalObjectId $ExistingApplicationResult.ServicePrincipalObjectId `
        -RedirectUri $redirectUri `
        -AcceptMappedClaims $true
}

function Invoke-CreateClaimsMappingPolicy {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )
    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    $displayName = Read-DefaultedValue `
        -Prompt "Claims Mapping Policy display name" `
        -DefaultValue "$($ExistingApplicationResult.DisplayName) - Claims Mapping Policy"

    $employeeIdSource = Read-DefaultedValue `
        -Prompt "Employee ID source attribute" `
        -DefaultValue "extensionAttribute10"

    $studentNumberSource = Read-DefaultedValue `
        -Prompt "Student number source attribute" `
        -DefaultValue "extensionAttribute9"

    & $CreateClaimsMappingPolicyScript `
        -ServicePrincipalObjectId $ExistingApplicationResult.ServicePrincipalObjectId `
        -DisplayName $displayName `
        -EmployeeIdSourceAttribute $employeeIdSource `
        -StudentNumberSourceAttribute $studentNumberSource
}

function Invoke-ConfigureApplicationRoles {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )
    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    & $ConfigureAppRolesScript `
        -ApplicationObjectId $ExistingApplicationResult.ApplicationObjectId
}


function Invoke-ConfigureOwner {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )
    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    & $ConfigureOwnerScript `
        -ApplicationObjectId $ExistingApplicationResult.ApplicationObjectId `
        -ServicePrincipalObjectId $ExistingApplicationResult.ServicePrincipalObjectId
}


function Invoke-CreateClientSecret {
    param(
        [Parameter(Mandatory = $false)]
        [object]$ExistingApplicationResult
    )
    Assert-NovariEnterpriseApplicationResult -ApplicationResult $ExistingApplicationResult

    $displayName = Read-DefaultedValue `
        -Prompt "Client secret display name" `
        -DefaultValue "Keycloak client secret"

    $validityDaysRaw = Read-DefaultedValue `
        -Prompt "Client secret validity in days" `
        -DefaultValue "180"

    $validityDays = 0
    if (-not [int]::TryParse($validityDaysRaw, [ref]$validityDays)) {
        throw "Client secret validity must be a whole number of days."
    }

    & $CreateClientSecretScript `
        -ApplicationObjectId $ExistingApplicationResult.ApplicationObjectId `
        -DisplayName $displayName `
        -ValidityDays $validityDays
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

    $identifierMappingMode = Read-ChoiceDefaultedValue `
        -Prompt "Identifier mapping mode. Use Direct or EmployeeType" `
        -DefaultValue "Direct" `
        -AllowedValues @("Direct", "EmployeeType")

    $configureScimParams = @{
        ServicePrincipalObjectId     = $servicePrincipalObjectId
        TenantUrl                    = $tenantUrl
        EmployeeIdSourceAttribute    = $employeeIdSource
        StudentNumberSourceAttribute = $studentNumberSource
        IdentifierMappingMode        = $identifierMappingMode
    }

    if ($identifierMappingMode -eq "EmployeeType") {
        $employeeTypeSource = Read-DefaultedValue `
            -Prompt "Source attribute to match employee type from" `
            -DefaultValue "employeeType"

        [string[]]$employeeTypeEmployeeValues = @(
            ConvertTo-NonEmptyStringArray -Value (
                Read-DefaultedValue `
                    -Prompt "employeeType values that should populate employeeId, comma separated" `
                    -DefaultValue "ansatt,tilsett"
            )
        )

        [string[]]$employeeTypeStudentValues = @(
            ConvertTo-NonEmptyStringArray -Value (
                Read-DefaultedValue `
                    -Prompt "employeeType values that should populate studentNumber, comma separated" `
                    -DefaultValue "elev"
            )
        )

        if (-not $employeeTypeEmployeeValues -or $employeeTypeEmployeeValues.Length -eq 0) {
            throw "At least one employeeType value is required for employeeId."
        }

        if (-not $employeeTypeStudentValues -or $employeeTypeStudentValues.Length -eq 0) {
            throw "At least one employeeType value is required for studentNumber."
        }

        $configureScimParams.EmployeeTypeSourceAttribute = $employeeTypeSource
        $configureScimParams.EmployeeTypeEmployeeValues = $employeeTypeEmployeeValues
        $configureScimParams.EmployeeTypeStudentValues = $employeeTypeStudentValues
    }

    & $ConfigureScimScript @configureScimParams
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
