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

function Get-SingleGraphObject {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Items,

        [Parameter(Mandatory = $true)]
        [string]$NoMatchMessage,

        [Parameter(Mandatory = $true)]
        [string]$MultipleMatchesMessage
    )

    if (-not $Items -or $Items.Count -eq 0) {
        throw $NoMatchMessage
    }

    if ($Items.Count -gt 1) {
        throw $MultipleMatchesMessage
    }

    return $Items[0]
}

function New-EnterpriseApplicationResult {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Application,

        [Parameter(Mandatory = $true)]
        [object]$ServicePrincipal
    )

    return [pscustomobject]@{
        DisplayName              = $ServicePrincipal.DisplayName
        ApplicationObjectId      = $Application.Id
        ApplicationAppId         = $Application.AppId
        ServicePrincipalObjectId = $ServicePrincipal.Id
    }
}

function Write-EnterpriseApplicationResult {
    param(
        [Parameter(Mandatory = $true)]
        [object]$ApplicationResult
    )

    Write-ObjectProperties `
        -InputObject $ApplicationResult `
        -Labels ([ordered]@{
            "Display Name"              = "DisplayName"
            "Application ObjectId"      = "ApplicationObjectId"
            "Application AppId"         = "ApplicationAppId"
            "ServicePrincipal ObjectId" = "ServicePrincipalObjectId"
        })
}
