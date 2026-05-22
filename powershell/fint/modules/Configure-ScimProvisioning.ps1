param(
    [Parameter(Mandatory = $true)]
    [string]$ServicePrincipalObjectId,

    [Parameter(Mandatory = $true)]
    [string]$TenantUrl,

    [Parameter(Mandatory = $false)]
    [string]$SecretToken,

    [Parameter(Mandatory = $false)]
    [ValidateSet("On", "Off")]
    [string]$ProvisionStatus = "On",

    [Parameter(Mandatory = $false)]
    [string]$EmployeeIdSourceAttribute = "extensionAttribute10",

    [Parameter(Mandatory = $false)]
    [string]$StudentNumberSourceAttribute = "extensionAttribute9"
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

function Set-AttrMapping {
    param(
        [Parameter(Mandatory)]
        [object]$ObjectMapping,

        [Parameter(Mandatory)]
        [string]$TargetAttributeName,

        [Parameter(Mandatory)]
        [hashtable]$NewMapping
    )

    if (-not $ObjectMapping.attributeMappings) {
        $ObjectMapping.attributeMappings = @()
    }

    $idx = -1

    for ($i = 0; $i -lt $ObjectMapping.attributeMappings.Count; $i++) {
        if ($ObjectMapping.attributeMappings[$i].targetAttributeName -eq $TargetAttributeName) {
            $idx = $i
            break
        }
    }

    if ($idx -ge 0) {
        $ObjectMapping.attributeMappings[$idx] = $NewMapping
    }
    else {
        $ObjectMapping.attributeMappings += $NewMapping
    }
}

function Rename-TargetObjectDefinition {
    param(
        [Parameter(Mandatory)]
        [object]$Schema,

        [Parameter(Mandatory)]
        [string]$TargetDirectoryName,

        [Parameter(Mandatory)]
        [string]$OldObjectName,

        [Parameter(Mandatory)]
        [string]$NewObjectName
    )

    $targetDir = $Schema.directories |
    Where-Object { $_.name -eq $TargetDirectoryName } |
    Select-Object -First 1

    if (-not $targetDir) {
        throw "Could not find target directory '$TargetDirectoryName' in synchronization schema."
    }

    if (-not $targetDir.objects) {
        throw "Target directory '$TargetDirectoryName' has no objects collection."
    }

    $existingNewObject = $targetDir.objects |
    Where-Object { $_.name -eq $NewObjectName } |
    Select-Object -First 1

    if ($existingNewObject) {
        $existingNewObject.supportedApis = @($NewObjectName)
        return
    }

    $targetObject = $targetDir.objects |
    Where-Object { $_.name -eq $OldObjectName } |
    Select-Object -First 1

    if (-not $targetObject) {
        $available = @($targetDir.objects | ForEach-Object { $_.name }) -join ", "
        throw "Could not find target object '$OldObjectName' or '$NewObjectName' in '$TargetDirectoryName'. Available: $available"
    }

    $targetObject.name = $NewObjectName
    $targetObject.supportedApis = @($NewObjectName)
}

function Update-ReferencedObjectNames {
    param(
        [Parameter(Mandatory)]
        [object]$Schema,

        [Parameter(Mandatory)]
        [string]$OldObjectName,

        [Parameter(Mandatory)]
        [string]$NewObjectName
    )

    foreach ($dir in $Schema.directories) {
        if (-not $dir.objects) {
            continue
        }

        foreach ($obj in $dir.objects) {
            if (-not $obj.attributes) {
                continue
            }

            foreach ($attr in $obj.attributes) {
                if (-not $attr.referencedObjects) {
                    continue
                }

                foreach ($ref in $attr.referencedObjects) {
                    if ($ref.referencedObjectName -eq $OldObjectName) {
                        $ref.referencedObjectName = $NewObjectName
                    }
                }
            }
        }
    }
}

function Update-MetadataObjectNameReferences {
    param(
        [Parameter(Mandatory)]
        [object]$Schema,

        [Parameter(Mandatory)]
        [string]$OldObjectName,

        [Parameter(Mandatory)]
        [string]$NewObjectName
    )

    foreach ($rule in $Schema.synchronizationRules) {
        if ($rule.metadata) {
            foreach ($meta in $rule.metadata) {
                if ($null -ne $meta.value -and $meta.value -is [string]) {
                    $meta.value = $meta.value.Replace($OldObjectName, $NewObjectName)
                }
            }
        }
    }

    foreach ($dir in $Schema.directories) {
        if (-not $dir.objects) {
            continue
        }

        foreach ($obj in $dir.objects) {
            if ($obj.metadata) {
                foreach ($meta in $obj.metadata) {
                    if ($null -ne $meta.value -and $meta.value -is [string]) {
                        $meta.value = $meta.value.Replace($OldObjectName, $NewObjectName)
                    }
                }
            }

            if (-not $obj.attributes) {
                continue
            }

            foreach ($attr in $obj.attributes) {
                if (-not $attr.metadata) {
                    continue
                }

                foreach ($meta in $attr.metadata) {
                    if ($null -ne $meta.value -and $meta.value -is [string]) {
                        $meta.value = $meta.value.Replace($OldObjectName, $NewObjectName)
                    }
                }
            }
        }
    }
}

function Remove-TargetAttributeDefinitionsExcept {
    param(
        [Parameter(Mandatory)]
        [object]$Schema,

        [Parameter(Mandatory)]
        [string]$TargetDirectoryName,

        [Parameter(Mandatory)]
        [string]$TargetObjectName,

        [Parameter(Mandatory)]
        [string[]]$AllowedAttributeNames
    )

    $targetDir = $Schema.directories |
    Where-Object { $_.name -eq $TargetDirectoryName } |
    Select-Object -First 1

    if (-not $targetDir) {
        throw "Could not find target directory '$TargetDirectoryName' in synchronization schema."
    }

    if (-not $targetDir.objects) {
        throw "Target directory '$TargetDirectoryName' has no objects collection."
    }

    $targetObject = $targetDir.objects |
    Where-Object { $_.name -eq $TargetObjectName } |
    Select-Object -First 1

    if (-not $targetObject) {
        $available = @($targetDir.objects | ForEach-Object { $_.name }) -join ", "
        throw "Could not find target object '$TargetObjectName' in target directory '$TargetDirectoryName'. Available: $available"
    }

    if (-not $targetObject.attributes) {
        $targetObject.attributes = @()
        return
    }

    $targetObject.attributes = @(
        $targetObject.attributes |
        Where-Object { $AllowedAttributeNames -contains $_.name }
    )
}
function Add-TargetAttributeDefinition {
    param(
        [Parameter(Mandatory)]
        [object]$Schema,

        [Parameter(Mandatory)]
        [string]$TargetDirectoryName,

        [Parameter(Mandatory)]
        [string]$TargetObjectName,

        [Parameter(Mandatory)]
        [string]$AttributeName,

        [Parameter(Mandatory)]
        [ValidateSet("String", "Boolean", "Integer", "Reference", "Binary", "DateTime")]
        [string]$Type,

        [Parameter(Mandatory)]
        [bool]$Required,

        [Parameter(Mandatory)]
        [bool]$Multivalued,

        [Parameter(Mandatory)]
        [bool]$Anchor
    )

    $targetDir = $Schema.directories |
    Where-Object { $_.name -eq $TargetDirectoryName } |
    Select-Object -First 1

    if (-not $targetDir) {
        throw "Could not find target directory '$TargetDirectoryName' in synchronization schema."
    }

    if (-not $targetDir.objects) {
        throw "Target directory '$TargetDirectoryName' has no objects collection."
    }

    $targetObject = $targetDir.objects |
    Where-Object { $_.name -eq $TargetObjectName } |
    Select-Object -First 1

    if (-not $targetObject) {
        $available = @($targetDir.objects | ForEach-Object { $_.name }) -join ", "
        throw "Could not find target object '$TargetObjectName' in target directory '$TargetDirectoryName'. Available: $available"
    }

    if (-not $targetObject.attributes) {
        $targetObject.attributes = @()
    }

    $existing = @($targetObject.attributes | Where-Object { $_.name -eq $AttributeName })

    if ($existing.Count -eq 0) {
        $targetObject.attributes += @{
            name              = $AttributeName
            type              = $Type
            required          = $Required
            multivalued       = $Multivalued
            anchor            = $Anchor
            caseExact         = $false
            defaultValue      = $null
            flowNullValues    = $false
            mutability        = "ReadWrite"
            apiExpressions    = @()
            metadata          = @()
            referencedObjects = @()
        }
    }
    else {
        $existing[0].type = $Type
        $existing[0].required = $Required
        $existing[0].multivalued = $Multivalued
        $existing[0].anchor = $Anchor
    }
}

function New-AttributeSource {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    return @{
        type       = "Attribute"
        name       = $Name
        expression = "[$Name]"
        parameters = @()
    }
}

function New-SwitchIsSoftDeletedSource {
    return @{
        type       = "Function"
        name       = "Switch"
        expression = 'Switch([IsSoftDeleted], , "False", "True", "True", "False")'
        parameters = @(
            @{
                key   = "source"
                value = @{
                    expression = "[IsSoftDeleted]"
                    name       = "IsSoftDeleted"
                    type       = "Attribute"
                    parameters = @()
                }
            },
            @{
                key   = "switchValue"
                value = @{
                    expression = '"False"'
                    name       = "False"
                    type       = "Constant"
                    parameters = @()
                }
            },
            @{
                key   = "switchValue"
                value = @{
                    expression = '"True"'
                    name       = "True"
                    type       = "Constant"
                    parameters = @()
                }
            },
            @{
                key   = "switchValue"
                value = @{
                    expression = '"True"'
                    name       = "True"
                    type       = "Constant"
                    parameters = @()
                }
            },
            @{
                key   = "switchValue"
                value = @{
                    expression = '"False"'
                    name       = "False"
                    type       = "Constant"
                    parameters = @()
                }
            }
        )
    }
}

function New-AppRoleAssignmentsSource {
    return @{
        type       = "Function"
        name       = "AssertiveAppRoleAssignmentsComplex"
        expression = "AssertiveAppRoleAssignmentsComplex([appRoleAssignments])"
        parameters = @(
            @{
                key   = "source"
                value = @{
                    expression = "[appRoleAssignments]"
                    name       = "appRoleAssignments"
                    type       = "Attribute"
                    parameters = @()
                }
            }
        )
    }
}

function New-Mapping {
    param(
        [Parameter(Mandatory)]
        [string]$TargetAttributeName,

        [Parameter(Mandatory)]
        [hashtable]$Source,

        [Parameter(Mandatory)]
        [int]$MatchingPriority
    )

    return @{
        defaultValue            = $null
        flowType                = "Always"
        flowBehavior            = "FlowWhenChanged"
        matchingPriority        = $MatchingPriority
        exportMissingReferences = $false
        targetAttributeName     = $TargetAttributeName
        source                  = $Source
    }
}

$spId = $ServicePrincipalObjectId
$fintTenantUrl = $TenantUrl

$tplUri = "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/templates"

Start-Sleep -Seconds 5

$templates = $null

for ($i = 1; $i -le 24; $i++) {
    $templates = Invoke-GraphWithRetry -Method GET -Uri $tplUri

    if ($templates -and $templates.value -and $templates.value.Count -gt 0) {
        break
    }

    Start-Sleep -Seconds 5
}

if (-not $templates -or -not $templates.value -or $templates.value.Count -eq 0) {
    throw "Synchronization templates is empty for servicePrincipal $spId. Cannot create provisioning job."
}

$template = $templates.value |
Where-Object { $_.id -match "scim" -or $_.description -match "SCIM" } |
Select-Object -First 1

if (-not $template) {
    $template = $templates.value | Select-Object -First 1
}

Write-Host "Using synchronization templateId: $($template.id)"

$secretsBody = @{
    value = @(
        @{
            key   = "BaseAddress"
            value = $fintTenantUrl
        },
        @{
            key   = "SecretToken"
            value = $SecretToken
        },
        @{
            key   = "SyncAll"
            value = "false"
        },
        @{
            key   = "SyncNotificationSettings"
            value = '{"Enabled":false,"DeleteThresholdEnabled":true,"DeleteThresholdValue":500}'
        }
    )
} | ConvertTo-Json -Depth 20

Invoke-GraphWithRetry `
    -Method PUT `
    -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/secrets" `
    -BodyJson $secretsBody `
    -NoRetryOnBadRequest | Out-Null

Write-Host "Provisioning secrets set. Scope is assigned users/groups only; accidental deletion threshold is 500."

$jobsUri = "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs"

$existingJobs = Invoke-GraphWithRetry `
    -Method GET `
    -Uri $jobsUri

$job = $null

if ($existingJobs -and $existingJobs.value) {
    $job = $existingJobs.value |
    Where-Object { $_.templateId -eq $template.id -or $_.id -like "$($template.id).*" } |
    Select-Object -First 1
}

if ($job) {
    $jobId = $job.id
    Write-Host "Using existing synchronization jobId: $jobId"
}
else {
    $jobBody = @{
        templateId = $template.id
    } | ConvertTo-Json

    $job = Invoke-GraphWithRetry `
        -Method POST `
        -Uri $jobsUri `
        -BodyJson $jobBody `
        -NoRetryOnBadRequest

    $jobId = $job.id

    Write-Host "Created synchronization jobId: $jobId"
}

$schemaUri = "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs/$jobId/schema"
$schema = Invoke-GraphWithRetry -Method GET -Uri $schemaUri

$userRule = $null
$userMapping = $null

foreach ($rule in $schema.synchronizationRules) {
    if (-not $rule.objectMappings) {
        continue
    }

    $candidate = $rule.objectMappings |
    Where-Object {
        $_.sourceObjectName -eq "User" -and
        $_.targetObjectName -like "*User"
    } |
    Select-Object -First 1

    if ($candidate) {
        $userRule = $rule
        $userMapping = $candidate
        break
    }
}

if (-not $userMapping) {
    Write-Warning "Could not locate a User objectMapping in schema. Skipping mapping update. Inspect schema at: $schemaUri"
}
else {
    if (-not $userRule.targetDirectoryName) {
        throw "Could not determine targetDirectoryName for User mapping."
    }

    $oldTargetUserObjectName = $userMapping.targetObjectName
    $targetUserObjectName = "urn:ietf:params:scim:schemas:core:2.0:User"

    Rename-TargetObjectDefinition `
        -Schema $schema `
        -TargetDirectoryName $userRule.targetDirectoryName `
        -OldObjectName $oldTargetUserObjectName `
        -NewObjectName $targetUserObjectName

    Update-ReferencedObjectNames `
        -Schema $schema `
        -OldObjectName $oldTargetUserObjectName `
        -NewObjectName $targetUserObjectName

    Update-MetadataObjectNameReferences `
        -Schema $schema `
        -OldObjectName $oldTargetUserObjectName `
        -NewObjectName $targetUserObjectName

    $userMapping.targetObjectName = $targetUserObjectName
    $userMapping.flowTypes = "Add,Update,Delete"
    $userMapping.enabled = $true

    $groupCount = 0

    foreach ($rule in $schema.synchronizationRules) {
        if (-not $rule.objectMappings) {
            continue
        }

        foreach ($omap in $rule.objectMappings) {
            if ($omap.sourceObjectName -eq "Group") {
                $omap.enabled = $false
                $groupCount++
            }
        }
    }

    Write-Host "Group mappings disabled: $groupCount"

    $targetId = "id"
    $targetActive = "active"
    $targetWorkEmail = 'emails[type eq "work"].value'
    $targetUserName = "userName"
    $targetExternalId = "externalId"
    $targetRoles = "roles"

    $targetGivenName = "urn:ietf:params:scim:schemas:extension:fint:2.0:User:givenName"
    $targetFamilyName = "urn:ietf:params:scim:schemas:extension:fint:2.0:User:familyName"

    $targetUpn = "urn:ietf:params:scim:schemas:extension:fint:2.0:User:userPrincipalName"
    $targetEmployeeId = "urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId"
    $targetStudentNumber = "urn:ietf:params:scim:schemas:extension:fint:2.0:User:studentNumber"

    $fintAttributes = @(
        @{
            Name        = $targetId
            Type        = "String"
            Required    = $true
            Multivalued = $false
            Anchor      = $true
        },
        @{
            Name        = $targetActive
            Type        = "Boolean"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetWorkEmail
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetUserName
            Type        = "String"
            Required    = $true
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetExternalId
            Type        = "String"
            Required    = $true
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetRoles
            Type        = "String"
            Required    = $false
            Multivalued = $true
            Anchor      = $false
        },
        @{
            Name        = $targetGivenName
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetFamilyName
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetUpn
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetEmployeeId
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        },
        @{
            Name        = $targetStudentNumber
            Type        = "String"
            Required    = $false
            Multivalued = $false
            Anchor      = $false
        }
    )

    $allowedAttributeNames = @(
        $fintAttributes |
        ForEach-Object { $_.Name }
    )

    Remove-TargetAttributeDefinitionsExcept `
        -Schema $schema `
        -TargetDirectoryName $userRule.targetDirectoryName `
        -TargetObjectName $targetUserObjectName `
        -AllowedAttributeNames $allowedAttributeNames

    foreach ($attr in $fintAttributes) {
        Add-TargetAttributeDefinition `
            -Schema $schema `
            -TargetDirectoryName $userRule.targetDirectoryName `
            -TargetObjectName $targetUserObjectName `
            -AttributeName $attr.Name `
            -Type $attr.Type `
            -Required $attr.Required `
            -Multivalued $attr.Multivalued `
            -Anchor $attr.Anchor
    }

    $allowedTargets = @(
        $fintAttributes |
        Where-Object { $_.Name -ne $targetId } |
        ForEach-Object { $_.Name }
    )

    if (-not $userMapping.attributeMappings) {
        $userMapping.attributeMappings = @()
    }

    $userMapping.attributeMappings = @(
        $userMapping.attributeMappings |
        Where-Object { $allowedTargets -contains $_.targetAttributeName }
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetUserName `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetUserName `
            -MatchingPriority 1 `
            -Source (New-AttributeSource -Name "objectId")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetActive `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetActive `
            -MatchingPriority 0 `
            -Source (New-SwitchIsSoftDeletedSource)
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetWorkEmail `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetWorkEmail `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name "mail")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetExternalId `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetExternalId `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name "objectId")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetRoles `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetRoles `
            -MatchingPriority 0 `
            -Source (New-AppRoleAssignmentsSource)
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetGivenName `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetGivenName `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name "givenName")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetFamilyName `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetFamilyName `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name "surname")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetUpn `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetUpn `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name "userPrincipalName")
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetEmployeeId `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetEmployeeId `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name $EmployeeIdSourceAttribute)
    )

    Set-AttrMapping `
        -ObjectMapping $userMapping `
        -TargetAttributeName $targetStudentNumber `
        -NewMapping (
        New-Mapping `
            -TargetAttributeName $targetStudentNumber `
            -MatchingPriority 0 `
            -Source (New-AttributeSource -Name $StudentNumberSourceAttribute)
    )

    $schema."@odata.type" = "#microsoft.graph.synchronizationSchema"

    $schemaJson = $schema | ConvertTo-Json -Depth 100

    Invoke-GraphWithRetry `
        -Method PUT `
        -Uri $schemaUri `
        -BodyJson $schemaJson `
        -NoRetryOnBadRequest | Out-Null

    Write-Host "Schema updated: target User object set to '$targetUserObjectName'; FINT user attributes/mappings applied; groups disabled."
}

if ($ProvisionStatus -eq "On") {
    $currentJob = Invoke-GraphWithRetry `
        -Method GET `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs/$jobId"

    $jobState = $null

    if ($currentJob.status -and $currentJob.status.code) {
        $jobState = $currentJob.status.code
    }

    if ($jobState -in @("Paused", "Quarantine")) {
        Invoke-GraphWithRetry `
            -Method POST `
            -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs/$jobId/start" | Out-Null

        Write-Host "Provisioning job started."
    }
    elseif (-not $jobState) {
        Invoke-GraphWithRetry `
            -Method POST `
            -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs/$jobId/start" | Out-Null

        Write-Host "Provisioning job started."
    }
    else {
        Write-Host "Provisioning job not started because current state is '$jobState'."
    }
}
else {
    Invoke-GraphWithRetry `
        -Method POST `
        -Uri "https://graph.microsoft.com/v1.0/servicePrincipals/$spId/synchronization/jobs/$jobId/pause" | Out-Null

    Write-Host "Provisioning job paused/off."
}

$result = [pscustomobject]@{
    ServicePrincipalObjectId     = $spId
    SyncTemplateId               = $template.id
    SyncJobId                    = $jobId
    TenantUrl                    = $fintTenantUrl
    ProvisionStatus              = $ProvisionStatus
    TargetUserObjectName         = "urn:ietf:params:scim:schemas:core:2.0:User"
    EmployeeIdSourceAttribute    = $EmployeeIdSourceAttribute
    StudentNumberSourceAttribute = $StudentNumberSourceAttribute
}

$result | ConvertTo-Json -Depth 20
