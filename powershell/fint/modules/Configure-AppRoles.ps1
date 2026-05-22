param(
    [Parameter(Mandatory = $true)]
    [string]$ApplicationObjectId,

    [Parameter(Mandatory = $true)]
    [string]$Organization
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

$RolesJsonPath = Join-Path -Path $PSScriptRoot -ChildPath "../app-roles.json"

function Import-NovariAppRolesJson {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Roles JSON file '$Path' was not found."
    }

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $content = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8

    if ([string]::IsNullOrWhiteSpace($content)) {
        throw "Roles JSON file '$resolvedPath' is empty."
    }

    try {
        $parsed = $content | ConvertFrom-Json
    }
    catch {
        throw "Roles JSON file '$resolvedPath' could not be parsed. $($_.Exception.Message)"
    }

    if ($parsed -is [System.Collections.IDictionary] -and $parsed.Contains('roles')) {
        return @($parsed['roles'])
    }

    if ($parsed -and ($parsed.PSObject.Properties.Name -contains 'roles')) {
        return @($parsed.roles)
    }

    return @($parsed)
}

function Get-NovariRolePropertyValue {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Role,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if ($Role -is [System.Collections.IDictionary]) {
        if ($Role.Contains($Name)) {
            return $Role[$Name]
        }

        return $null
    }

    $property = $Role.PSObject.Properties[$Name]
    if ($property) {
        return $property.Value
    }

    return $null
}

function ConvertTo-NovariRoleIdString {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Value,

        [Parameter(Mandatory = $true)]
        [string]$Context
    )

    if ([string]::IsNullOrWhiteSpace([string]$Value)) {
        return $null
    }

    try {
        $guid = [guid]([string]$Value)
    }
    catch {
        throw "$Context must be a valid GUID. Value was '$Value'."
    }

    if ($guid -eq [guid]::Empty) {
        throw "$Context must not be the empty GUID."
    }

    return $guid.ToString()
}

function Get-NovariJsonRoleId {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Role
    )

    $id = Get-NovariRolePropertyValue -Role $Role -Name 'id'
    if ([string]::IsNullOrWhiteSpace([string]$id)) {
        return $null
    }

    $displayName = Get-NovariRolePropertyValue -Role $Role -Name 'displayName'
    $context = "Role '$displayName' id"
    if ([string]::IsNullOrWhiteSpace([string]$displayName)) {
        $context = 'Role id'
    }

    return ConvertTo-NovariRoleIdString -Value $id -Context $context
}

function Find-NovariExistingRoleById {
    param(
        [Parameter(Mandatory = $false)]
        [object[]]$ExistingRoles = @(),

        [Parameter(Mandatory = $true)]
        [string]$Id
    )

    $matches = @($ExistingRoles | Where-Object {
        -not [string]::IsNullOrWhiteSpace([string]$_.id) -and [string]$_.id -eq $Id
    })

    if ($matches.Count -gt 1) {
        throw "Multiple existing app roles use id '$Id'. Refusing to continue because the update target is ambiguous."
    }

    if ($matches.Count -eq 1) {
        return $matches[0]
    }

    return $null
}

function Convert-NovariRoleCatalogOrganizationUrl {
    param(
        [Parameter(Mandatory = $false)]
        [AllowNull()]
        [object]$Value,

        [Parameter(Mandatory = $true)]
        [string]$Organization
    )

    if ([string]::IsNullOrWhiteSpace([string]$Value)) {
        return $Value
    }

    $organizationValue = ([string]$Organization).Trim()
    if ([string]::IsNullOrWhiteSpace($organizationValue)) {
        throw "Organization must not be empty."
    }

    if ($organizationValue -match '[/?#]') {
        throw "Organization must be a path-safe value and cannot contain '/', '?' or '#'. Value was '$Organization'."
    }

    $newValue = [regex]::Replace(
        [string]$Value,
        '(https://role-catalog\.vigoiks\.no)/<organization>/',
        "`$1/$organizationValue/"
    )

    $newValue = [regex]::Replace(
        $newValue,
        '(https://role-catalog\.vigoiks\.no)/organization/',
        "`$1/$organizationValue/"
    )

    return $newValue
}

function New-NovariAppRoleFromJsonRole {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Role,

        [Parameter(Mandatory = $true)]
        [string]$AppRoleId,

        [Parameter(Mandatory = $true)]
        [string]$Organization,

        [Parameter(Mandatory = $false)]
        [object]$ExistingRole
    )

    $displayName = Get-NovariRolePropertyValue -Role $Role -Name 'displayName'
    $value = Get-NovariRolePropertyValue -Role $Role -Name 'value'
    $description = Get-NovariRolePropertyValue -Role $Role -Name 'description'
    $allowedMemberTypes = Get-NovariRolePropertyValue -Role $Role -Name 'allowedMemberTypes'
    $isEnabled = Get-NovariRolePropertyValue -Role $Role -Name 'isEnabled'

    if ([string]::IsNullOrWhiteSpace([string]$displayName)) {
        throw "Every role must include displayName."
    }

    if ([string]::IsNullOrWhiteSpace([string]$value)) {
        $value = ([string]$displayName).Trim()
    }

    $value = Convert-NovariRoleCatalogOrganizationUrl `
        -Value $value `
        -Organization $Organization

    if ([string]::IsNullOrWhiteSpace([string]$description)) {
        $description = [string]$displayName
    }

    if ($null -eq $allowedMemberTypes -or @($allowedMemberTypes).Count -eq 0) {
        $allowedMemberTypes = @('User')
    }
    else {
        $allowedMemberTypes = @($allowedMemberTypes)
    }

    foreach ($memberType in $allowedMemberTypes) {
        if ($memberType -notin @('User', 'Application')) {
            throw "Role '$displayName' has invalid allowedMemberTypes value '$memberType'. Supported values are 'User' and 'Application'."
        }
    }

    if ($null -eq $isEnabled) {
        $isEnabled = $true
    }

    $appRoleId = ConvertTo-NovariRoleIdString -Value $AppRoleId -Context "Role '$displayName' app role id"

    return [pscustomobject]@{
        allowedMemberTypes = @($allowedMemberTypes)
        description        = [string]$description
        displayName        = [string]$displayName
        id                 = [string]$appRoleId
        isEnabled          = [bool]$isEnabled
        value              = [string]$value
    }
}

function Merge-NovariAppRoles {
    param(
        [Parameter(Mandatory = $false)]
        [object[]]$ExistingRoles = @(),

        [Parameter(Mandatory = $true)]
        [object[]]$JsonRoles,

        [Parameter(Mandatory = $true)]
        [string]$Organization
    )

    if (-not $JsonRoles -or $JsonRoles.Count -eq 0) {
        throw "The roles JSON file did not contain any roles."
    }

    $mergedRoles = @()
    $handledExistingRoleIds = @{}
    $usedValues = @{}
    $usedRoleIds = @{}

    foreach ($jsonRole in $JsonRoles) {
        $appRoleId = Get-NovariJsonRoleId -Role $jsonRole
        $jsonDisplayName = Get-NovariRolePropertyValue -Role $jsonRole -Name 'displayName'

        if ([string]::IsNullOrWhiteSpace([string]$appRoleId)) {
            Write-Warning "Skipping JSON role '$jsonDisplayName' because it does not have id."
            continue
        }

        if ($usedRoleIds.ContainsKey($appRoleId)) {
            throw "Duplicate id '$appRoleId' found in roles JSON. Each app role id must be unique."
        }
        $usedRoleIds[$appRoleId] = $true

        $existing = Find-NovariExistingRoleById -ExistingRoles $ExistingRoles -Id $appRoleId

        $newRole = New-NovariAppRoleFromJsonRole `
            -Role $jsonRole `
            -AppRoleId $appRoleId `
            -Organization $Organization `
            -ExistingRole $existing

        if ($usedValues.ContainsKey($newRole.value)) {
            throw "Duplicate role value '$($newRole.value)' in JSON or existing app roles. Role values must be unique."
        }
        $usedValues[$newRole.value] = $true

        $mergedRoles += $newRole

        if ($existing) {
            $handledExistingRoleIds[[string]$existing.id] = $true
        }
    }

    foreach ($existingRole in $ExistingRoles) {
        if ($handledExistingRoleIds.ContainsKey([string]$existingRole.id)) {
            continue
        }

        if ($existingRole.value -and $usedValues.ContainsKey([string]$existingRole.value)) {
            throw "Duplicate existing role value '$($existingRole.value)' found on the application. Refusing to continue."
        }

        if ($existingRole.value) {
            $usedValues[[string]$existingRole.value] = $true
        }

        $mergedRoles += $existingRole
    }

    return @($mergedRoles)
}

function Get-NovariAppRoleDiffKey {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Role
    )

    if (-not [string]::IsNullOrWhiteSpace([string]$Role.id)) {
        return "id:$($Role.id)"
    }

    if (-not [string]::IsNullOrWhiteSpace([string]$Role.value)) {
        return "value:$($Role.value)"
    }

    return "displayName:$($Role.displayName)"
}

function ConvertTo-NovariAppRoleDiffObject {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Role
    )

    return [ordered]@{
        displayName        = [string]$Role.displayName
        value              = [string]$Role.value
        description        = [string]$Role.description
        isEnabled          = [bool]$Role.isEnabled
        allowedMemberTypes = ((@($Role.allowedMemberTypes) | Sort-Object) -join ',')
        id                 = [string]$Role.id
    }
}

function Write-NovariAppRoleChanges {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetName,

        [Parameter(Mandatory = $false)]
        [object[]]$ExistingRoles = @(),

        [Parameter(Mandatory = $false)]
        [object[]]$MergedRoles = @()
    )

    $existingByKey = @{}
    foreach ($existingRole in @($ExistingRoles)) {
        $existingByKey[(Get-NovariAppRoleDiffKey -Role $existingRole)] = $existingRole
    }

    $mergedByKey = @{}
    foreach ($mergedRole in @($MergedRoles)) {
        $mergedByKey[(Get-NovariAppRoleDiffKey -Role $mergedRole)] = $mergedRole
    }

    $added = @()
    $removed = @()
    $changed = @()

    foreach ($key in @($mergedByKey.Keys | Sort-Object)) {
        if (-not $existingByKey.ContainsKey($key)) {
            $added += $mergedByKey[$key]
            continue
        }

        $before = ConvertTo-NovariAppRoleDiffObject -Role $existingByKey[$key]
        $after = ConvertTo-NovariAppRoleDiffObject -Role $mergedByKey[$key]

        $fieldChanges = @()
        foreach ($fieldName in $after.Keys) {
            if ([string]$before[$fieldName] -ne [string]$after[$fieldName]) {
                $fieldChanges += [pscustomobject]@{
                    Field  = $fieldName
                    Before = [string]$before[$fieldName]
                    After  = [string]$after[$fieldName]
                }
            }
        }

        if ($fieldChanges.Count -gt 0) {
            $changed += [pscustomobject]@{
                Key         = $key
                DisplayName = [string]$mergedByKey[$key].displayName
                Changes     = @($fieldChanges)
            }
        }
    }

    foreach ($key in @($existingByKey.Keys | Sort-Object)) {
        if (-not $mergedByKey.ContainsKey($key)) {
            $removed += $existingByKey[$key]
        }
    }

    Write-Host ""
    Write-Host "Planned app role changes for $TargetName"
    Write-Host "Added: $($added.Count), Updated: $($changed.Count), Removed: $($removed.Count)"

    if ($added.Count -gt 0) {
        Write-Host ""
        Write-Host "Added roles:"
        foreach ($role in $added) {
            Write-Host "  + $($role.displayName) [$($role.value)]"
        }
    }

    if ($changed.Count -gt 0) {
        Write-Host ""
        Write-Host "Updated roles:"
        foreach ($roleChange in $changed) {
            Write-Host "  ~ $($roleChange.DisplayName)"
            foreach ($change in @($roleChange.Changes)) {
                Write-Host "      $($change.Field): '$($change.Before)' -> '$($change.After)'"
            }
        }
    }

    if ($removed.Count -gt 0) {
        Write-Host ""
        Write-Host "Removed roles:"
        foreach ($role in $removed) {
            Write-Host "  - $($role.displayName) [$($role.value)]"
        }
    }

    return ($added.Count + $changed.Count + $removed.Count)
}

function New-NovariApplicationAppRolePlan {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ObjectId,

        [Parameter(Mandatory = $true)]
        [object[]]$JsonRoles,

        [Parameter(Mandatory = $true)]
        [string]$Organization
    )

    $application = Invoke-GraphWithRetry `
        -Method GET `
        -Uri "https://graph.microsoft.com/v1.0/applications/$ObjectId`?`$select=id,appRoles"

    $existingRoles = @()
    if ($application.appRoles) {
        $existingRoles = @($application.appRoles)
    }

    $mergedRoles = Merge-NovariAppRoles `
        -ExistingRoles $existingRoles `
        -JsonRoles $JsonRoles `
        -Organization $Organization

    return [pscustomobject]@{
        TargetName    = "App Registration"
        ObjectId      = $ObjectId
        Uri           = "https://graph.microsoft.com/v1.0/applications/$ObjectId"
        ExistingRoles = @($existingRoles)
        MergedRoles   = @($mergedRoles)
    }
}

function Confirm-NovariAppRoleChanges {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Plan
    )

    $changeCount = Write-NovariAppRoleChanges `
        -TargetName "$($Plan.TargetName) '$($Plan.ObjectId)'" `
        -ExistingRoles $Plan.ExistingRoles `
        -MergedRoles $Plan.MergedRoles

    if ($changeCount -eq 0) {
        Write-Host "No app role changes detected for $($Plan.TargetName) '$($Plan.ObjectId)'."
        return $false
    }

    Write-Host ""
    Write-Host "Apply these $changeCount planned app role changes to $($Plan.TargetName) '$($Plan.ObjectId)'?"
    Write-Host "Type 'yes' or 'y' to continue."
    $answer = Read-Host "Confirm"
    if ($answer -notin @('yes', 'y')) {
        throw "Cancelled by user. No changes were applied."
    }

    return $true
}

function Invoke-NovariApplicationAppRolePlan {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Plan
    )

    Invoke-GraphWithRetry `
        -Method PATCH `
        -Uri $Plan.Uri `
        -BodyJson (@{ appRoles = @($Plan.MergedRoles) } | ConvertTo-Json -Depth 30) | Out-Null

    return @($Plan.MergedRoles)
}

$jsonRoles = Import-NovariAppRolesJson -Path $RolesJsonPath

$applicationPlan = New-NovariApplicationAppRolePlan `
    -ObjectId $ApplicationObjectId `
    -JsonRoles $jsonRoles `
    -Organization $Organization

$applicationRoles = @($applicationPlan.MergedRoles)
$shouldApplyChanges = Confirm-NovariAppRoleChanges -Plan $applicationPlan

if ($shouldApplyChanges) {
    $applicationRoles = Invoke-NovariApplicationAppRolePlan -Plan $applicationPlan
    Write-Host "Configured App Registration app roles from JSON."
}

Write-Host "ApplicationObjectId:  $ApplicationObjectId"
Write-Host "RolesJsonPath:        $((Resolve-Path -LiteralPath $RolesJsonPath).Path)"
Write-Host "Organization:         $Organization"
Write-Host "JsonRoleCount:        $(@($jsonRoles).Count)"
Write-Host "ConfiguredRoleCount:  $(@($jsonRoles | Where-Object { -not [string]::IsNullOrWhiteSpace([string](Get-NovariJsonRoleId -Role $_)) }).Count)"
Write-Host "SkippedRoleCount:     $(@($jsonRoles | Where-Object { [string]::IsNullOrWhiteSpace([string](Get-NovariJsonRoleId -Role $_)) }).Count)"
Write-Host "ApplicationRoleCount: $(@($applicationRoles).Count)"

Write-Host "Roles:"

$jsonRoles | ForEach-Object {
    $appRoleId = Get-NovariJsonRoleId -Role $_

    if (-not [string]::IsNullOrWhiteSpace([string]$appRoleId)) {
        $displayName = [string](Get-NovariRolePropertyValue -Role $_ -Name 'displayName')
        $value = [string](Convert-NovariRoleCatalogOrganizationUrl `
            -Value (Get-NovariRolePropertyValue -Role $_ -Name 'value') `
            -Organization $Organization)

        Write-Host "  Id:          $appRoleId"
        Write-Host "  DisplayName: $displayName"
        Write-Host "  Value:       $value"
        Write-Host ""
    }
}

