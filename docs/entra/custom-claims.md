# Configure Custom Claims with Claims Mapping Policy (Microsoft Graph)

This guide explains how to:

- Create a Claims Mapping Policy
- Assign it to a Service Principal (Enterprise Application)
- Update an existing policy
- Remove or delete a policy

The goal is to emit custom claims (e.g., `employeeId`, `studentNumber`) in issued tokens.

# 1. Prerequisites

## 1.1 Required Microsoft Graph API Permissions

The Service Principal used to configure the policy must have the following permissions:

- `Application.ReadWrite.All`
- `Policy.Read.All`
- `Policy.ReadWrite.ApplicationConfiguration`

> ⚠️ These permissions require admin consent.

## 1.2 Software Requirements

- PowerShell 7
- Microsoft Graph PowerShell SDK

More information on Graph SDK:
https://learn.microsoft.com/en-us/powershell/microsoftgraph/installation?view=graph-powershell-1.0

# 2. Connect to Microsoft Graph

Import the required module and authenticate.

Example using Client Secret authentication:

> ⚠️ Replace:
>
> - `TENANT_ID_HERE` with your Tenant ID
> - `CLIENT_ID_HERE` with the Client ID of the Application
> - `CLIENT_SECRET_HERE` with the generated secret value

```powershell
Import-Module Microsoft.Graph.Identity.SignIns

$tenantId     = "TENANT_ID_HERE"
$clientId     = "CLIENT_ID_HERE"
$clientSecret = "CLIENT_SECRET_HERE"

$sec  = ConvertTo-SecureString $clientSecret -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential($clientId, $sec)

Connect-MgGraph -TenantId $tenantId -ClientSecretCredential $cred
```

Verify the connection:

```powershell
Get-MgContext
```

# 3. Create and Assign a Claims Mapping Policy

This process:

1. Creates a Claims Mapping Policy
2. Defines custom claims
3. Assigns the policy to a Service Principal

> ⚠️ Replace:
>
> - `NAME_HERE` with your policy name
> - `SERVICE_PRINCIPAL_ID_HERE` with the Object ID of the Enterprise Application
> - `extensionAttributeXX` with correct user attributes

## 3.1 Script

```powershell
# ==========================
# Configurable Variables
# ==========================
$DisplayName        = "NAME_HERE"
$ServicePrincipalId = "SERVICE_PRINCIPAL_ID_HERE"
$EmployeeIdSource    = "extensionAttributeXX"
$StudentNumberSource = "extensionAttributeXX"

# ==========================
# Build Claims Mapping JSON
# ==========================
$ClaimsMapping = @{
    ClaimsMappingPolicy = @{
        Version = 1
        IncludeBasicClaimSet = "true"
        ClaimsSchema = @(
            @{
                Source = "user"
                ID = $EmployeeIdSource
                JwtClaimType = "employeeId"
            },
            @{
                Source = "user"
                ID = $StudentNumberSource
                JwtClaimType = "studentNumber"
            }
        )
    }
} | ConvertTo-Json -Depth 10 -Compress

# ==========================
# Create Claims Mapping Policy
# ==========================
$Policy = New-MgPolicyClaimMappingPolicy `
    -Definition @($ClaimsMapping) `
    -DisplayName $DisplayName

Write-Host "Created Claims Mapping Policy:"
Write-Host "  Name: $($Policy.DisplayName)"
Write-Host "  Id:   $($Policy.Id)"

# ==========================
# Assign Policy to Service Principal
# ==========================
$Body = @{
    "@odata.id" = "https://graph.microsoft.com/v1.0/policies/claimsMappingPolicies/$($Policy.Id)"
}

New-MgServicePrincipalClaimMappingPolicyByRef `
    -ServicePrincipalId $ServicePrincipalId `
    -BodyParameter $Body

Write-Host "Assigned policy $($Policy.Id) to service principal $ServicePrincipalId"
```

# 4. Update an Existing Claims Mapping Policy

You can update the definition without removing it from the Service Principal.

## 4.1 Retrieve the Policy

We need the Policy ID to update the existing definition. We can search for the Policy using the DisplayName property:

```powershell
$DisplayName = "DISPLAY_NAME_HERE"

Get-MgPolicyClaimMappingPolicy `
    -Filter "startswith(displayName,'$DisplayName')" `
    -Property Id,DisplayName |
Select-Object Id,DisplayName
```

## 4.2 Modify the Claims Definition

Example adding a `department` claim:

```powershell
# ==========================
# Configurable Variables
# ==========================
$EmployeeIdSource    = "extensionAttribute10"
$StudentNumberSource = "extensionAttribute11"
$DepartmentSource    = "department"

# ==========================
# Build Claims Mapping JSON
# ==========================
$UpdatedClaimsMapping = @{
    ClaimsMappingPolicy = @{
        Version = 1
        IncludeBasicClaimSet = "true"
        ClaimsSchema = @(
            @{
                Source = "user"
                ID = $EmployeeIdSource
                JwtClaimType = "employeeId"
            },
            @{
                Source = "user"
                ID = $StudentNumberSource
                JwtClaimType = "studentNumber"
            },
            @{
                Source = "user"
                ID = $DepartmentSource
                JwtClaimType = "department"
            }
        )
    }
} | ConvertTo-Json -Depth 10 -Compress
```

## 4.3 Apply the Update

```powershell
$PolicyId = "POLICY_ID_HERE"

Update-MgPolicyClaimMappingPolicy `
    -ClaimsMappingPolicyId $PolicyId `
    -Definition @($UpdatedClaimsMapping)
```

# 5. Remove a Policy from a Service Principal

This removes the assignment only (the policy remains in the tenant).

## 5.1 Check Assigned Policy

```powershell
$ServicePrincipalId = "SERVICE_PRINCIPAL_ID_HERE"

Get-MgServicePrincipalClaimMappingPolicy `
    -ServicePrincipalId $ServicePrincipalId
```

## 5.2 Remove the Assignment

```powershell
$ServicePrincipalId = "SERVICE_PRINCIPAL_ID_HERE"
$PolicyId = "POLICY_ID_HERE"

Remove-MgServicePrincipalClaimMappingPolicyByRef `
    -ServicePrincipalId $ServicePrincipalId `
    -ClaimsMappingPolicyId $PolicyId
```

# 6. Delete a Claims Mapping Policy

This permanently removes the policy from the tenant.

> ⚠️ The policy must not be assigned to any Service Principal.

```powershell
$PolicyId = "POLICY_ID_HERE"

Remove-MgPolicyClaimMappingPolicy `
    -ClaimsMappingPolicyId $PolicyId
```

# 8. Important Notes

## 8.1 One Policy Per Service Principal

A Service Principal can have **only one Claims Mapping Policy assigned at a time**.

## 8.2 Token Configuration

The application must request a token type that supports custom claims (ID token or Access token depending on scenario).

## 8.4 Propagation Time

Changes may take several minutes before appearing in newly issued tokens.

# 9. Quick Command Reference

| Action            | Command                                            |
| ----------------- | -------------------------------------------------- |
| Create policy     | `New-MgPolicyClaimMappingPolicy`                   |
| Update policy     | `Update-MgPolicyClaimMappingPolicy`                |
| Delete policy     | `Remove-MgPolicyClaimMappingPolicy`                |
| Assign policy     | `New-MgServicePrincipalClaimMappingPolicyByRef`    |
| Remove assignment | `Remove-MgServicePrincipalClaimMappingPolicyByRef` |
