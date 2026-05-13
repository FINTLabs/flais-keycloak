# FINT Entra ID / SCIM Setup

GitHub-ready documentation for the PowerShell tooling in this repository.

This tool creates or connects a Microsoft Entra Enterprise Application for FINT, configures the related App Registration and Service Principal, creates and assigns a Claims Mapping Policy, and configures SCIM provisioning toward a FINT-compatible SCIM endpoint.

## Contents

- [What this tool does](#what-this-tool-does)
- [Repository layout](#repository-layout)
- [Prerequisites](#prerequisites)
- [Required Microsoft Graph scopes](#required-microsoft-graph-scopes)
- [Quick start](#quick-start)
- [Interactive menu](#interactive-menu)
- [End-to-end flow](#end-to-end-flow)
- [Architecture](#architecture)
- [Operation details](#operation-details)
- [SCIM provisioning details](#scim-provisioning-details)
- [Claims mapping](#claims-mapping)
- [Error handling and retry behavior](#error-handling-and-retry-behavior)
- [Troubleshooting](#troubleshooting)

## What this tool does

The setup process covers the Entra ID side of a FINT identity provider and SCIM provisioning integration.

It can:

1. Create a non-gallery Enterprise Application.
2. Connect to an existing Enterprise Application by Application AppId.
3. Create a Claims Mapping Policy.
4. Assign the Claims Mapping Policy to a Service Principal.
5. Configure App Registration settings.
6. Configure Enterprise Application settings.
7. Configure SCIM provisioning toward a FINT SCIM endpoint.
8. Start or pause the provisioning job.

The intended entrypoint is:

```powershell
./New-Novari-IDP.ps1
```

## Repository layout

```text
.
├── New-Novari-IDP.ps1
├── Create-EnterpriseApplication.ps1
├── Create-ClaimsMappingPolicy.ps1
├── Configure-EnterpriseApplication.ps1
├── Configure-ScimProvisioning.ps1
└── helpers
    ├── GraphRetry.ps1
    ├── Header.ps1
    ├── Menu.ps1
    └── RequiredScopes.ps1
```

| File | Purpose |
|---|---|
| `New-Novari-IDP.ps1` | Interactive entrypoint. Handles Graph login, menu dispatch, and prompts. |
| `Create-EnterpriseApplication.ps1` | Instantiates a non-gallery Enterprise Application from the Microsoft application template. |
| `Create-ClaimsMappingPolicy.ps1` | Creates a FINT Claims Mapping Policy and assigns it to the Service Principal. |
| `Configure-EnterpriseApplication.ps1` | Updates App Registration and Enterprise Application settings. |
| `Configure-ScimProvisioning.ps1` | Configures synchronization secrets, job, schema, mappings, and provisioning state. |
| `helpers/GraphRetry.ps1` | Wraps Microsoft Graph calls with retry and detailed error reporting. |
| `helpers/RequiredScopes.ps1` | Validates that the active Graph context has the expected scopes. |
| `helpers/Header.ps1` | Console header and logo rendering. |
| `helpers/Menu.ps1` | Interactive menu and menu action dispatch. |

## Prerequisites

Run the scripts from PowerShell with access to Microsoft Graph.

Required PowerShell modules:

```powershell
Install-Module Microsoft.Graph.Authentication -Scope CurrentUser
Install-Module Microsoft.Graph.Applications -Scope CurrentUser
```

You need a Microsoft Entra app/client that can authenticate to Microsoft Graph using client credentials:

- Tenant ID
- Client ID
- Client Secret

The script prompts for these values when `New-Novari-IDP.ps1` starts.

## Required Microsoft Graph scopes

The scripts validate the active Graph context before performing operations.

| Operation | Required scopes |
|---|---|
| Create Enterprise Application | `Application.ReadWrite.All`, `Policy.Read.All`, `Policy.ReadWrite.ApplicationConfiguration` |
| Create Claims Mapping Policy | `Application.ReadWrite.All`, `Policy.Read.All`, `Policy.ReadWrite.ApplicationConfiguration`, `Synchronization.ReadWrite.All` |
| Configure SCIM Provisioning | `Application.ReadWrite.All`, `Policy.Read.All`, `Policy.ReadWrite.ApplicationConfiguration`, `Synchronization.ReadWrite.All` |

> Note: `RequiredScopes.ps1` checks that the Graph context has exactly the expected scopes for the operation. Missing or additional scopes cause the operation to fail fast.

## Quick start

From the repository root:

```powershell
pwsh ./New-Novari-IDP.ps1
```

You will be prompted for:

- Tenant ID
- Client ID
- Client Secret
- Whether to connect an existing Enterprise Application
- Operation-specific values such as display name, redirect URI, SCIM tenant URL, and source attributes

Default FINT attribute sources:

| Value | Default source attribute |
|---|---|
| Employee ID | `extensionAttribute10` |
| Student number | `extensionAttribute9` |

## Interactive menu

After login and optional existing app selection, the script displays a menu.

```text
1. Create Enterprise Application
2. Create and assign Claims Mapping Policy
3. Configure Enterprise Application + App Registration settings
4. Configure FINT SCIM Provisioning
5. Show Active Graph Context
6. Show Current Enterprise Application
0. Exit
```

If an existing Enterprise Application is connected during startup, option `1` is hidden for that session.

## End-to-end flow

```mermaid
---
config:
    flowchart:
        defaultRenderer: elk
---
flowchart TD
    A[Start New-Novari-IDP.ps1] --> B[Load helper scripts]
    B --> C[Connect to Microsoft Graph]
    C --> D{Connect existing Enterprise Application?}

    D -- Yes --> E[Lookup Service Principal by Application AppId]
    E --> F[Lookup matching App Registration]
    F --> G[Store current application context]

    D -- No --> H[No current application context]

    G --> I[Show menu]
    H --> I

    I --> J{Menu choice}
    J -- 1 --> K[Create Enterprise Application]
    J -- 2 --> L[Create and assign Claims Mapping Policy]
    J -- 3 --> M[Configure App Registration and Enterprise Application]
    J -- 4 --> N[Configure SCIM provisioning]
    J -- 5 --> O[Show Graph context]
    J -- 6 --> P[Show current Enterprise Application]
    J -- 0 --> Q[Exit]

    K --> I
    L --> I
    M --> I
    N --> I
    O --> I
    P --> I
```

## Architecture

```mermaid
flowchart LR
    User[Operator] --> Entry[New-Novari-IDP.ps1]

    Entry --> Menu[helpers/Menu.ps1]
    Entry --> Header[helpers/Header.ps1]
    Entry --> GraphLogin[Microsoft Graph login]

    Menu --> CreateApp[Create-EnterpriseApplication.ps1]
    Menu --> Claims[Create-ClaimsMappingPolicy.ps1]
    Menu --> ConfigureApp[Configure-EnterpriseApplication.ps1]
    Menu --> Scim[Configure-ScimProvisioning.ps1]

    CreateApp --> Retry[helpers/GraphRetry.ps1]
    Claims --> Retry
    ConfigureApp --> Retry
    Scim --> Retry

    CreateApp --> ScopeCheck[helpers/RequiredScopes.ps1]
    Claims --> ScopeCheck
    Scim --> ScopeCheck

    Retry --> Graph[Microsoft Graph API]
    Graph --> Entra[Microsoft Entra ID]
    Scim --> FintScim[FINT / Keycloak SCIM endpoint]
```

## Operation details

### 1. Create Enterprise Application

Script:

```powershell
./Create-EnterpriseApplication.ps1 -DisplayName "<display-name>"
```

This operation:

1. Validates required Graph scopes.
2. Instantiates the Microsoft non-gallery application template.
3. Returns the created App Registration and Service Principal identifiers.

Template ID used:

```text
8adf8e6e-67b2-4cf2-a259-e3dc5476c621
```

Returned values:

| Property | Description |
|---|---|
| `DisplayName` | Enterprise Application display name. |
| `ApplicationObjectId` | Object ID of the App Registration. |
| `ApplicationAppId` | Application/client ID. |
| `ServicePrincipalObjectId` | Object ID of the Enterprise Application / Service Principal. |

```mermaid
sequenceDiagram
    actor Operator
    participant Script as Create-EnterpriseApplication.ps1
    participant Graph as Microsoft Graph
    participant Entra as Microsoft Entra ID

    Operator->>Script: Provide display name
    Script->>Script: Validate required Graph scopes
    Script->>Graph: POST /applicationTemplates/{nonGalleryTemplateId}/instantiate
    Graph->>Entra: Create App Registration and Service Principal
    Entra-->>Graph: Application and Service Principal
    Graph-->>Script: Creation response
    Script-->>Operator: Output object IDs and AppId
```

### 2. Create and assign Claims Mapping Policy

Script:

```powershell
./Create-ClaimsMappingPolicy.ps1 `
  -ServicePrincipalObjectId "<service-principal-object-id>" `
  -DisplayName "<policy-display-name>" `
  -EmployeeIdSourceAttribute "extensionAttribute10" `
  -StudentNumberSourceAttribute "extensionAttribute9"
```

This operation:

1. Validates required Graph scopes.
2. Creates a Claims Mapping Policy.
3. Maps selected source attributes into JWT claims.
4. Assigns the policy to the Service Principal.

Created JWT claims:

| JWT claim | Source object | Default source attribute |
|---|---|---|
| `employee_id` | `user` | `extensionAttribute10` |
| `student_number` | `user` | `extensionAttribute9` |

```mermaid
sequenceDiagram
    actor Operator
    participant Script as Create-ClaimsMappingPolicy.ps1
    participant Graph as Microsoft Graph
    participant Policy as Claims Mapping Policy
    participant SP as Service Principal

    Operator->>Script: Provide Service Principal ID and source attributes
    Script->>Script: Build ClaimsMappingPolicy JSON definition
    Script->>Graph: POST /policies/claimsMappingPolicies
    Graph-->>Script: Policy ID
    Script->>Graph: POST /servicePrincipals/{id}/claimsMappingPolicies/$ref
    Graph->>SP: Assign policy
    Script-->>Operator: Return policy and source attribute details
```

### 3. Configure Enterprise Application and App Registration

Script:

```powershell
./Configure-EnterpriseApplication.ps1 `
  -ApplicationObjectId "<application-object-id>" `
  -ApplicationAppId "<application-app-id>" `
  -ServicePrincipalObjectId "<service-principal-object-id>" `
  -RedirectUri "<keycloak-redirect-uri>" `
  -AcceptMappedClaims $true
```

This operation updates both the App Registration and Enterprise Application.

App Registration changes:

| Setting | Value |
|---|---|
| `api.acceptMappedClaims` | `true` by default |
| Web redirect URI | Prompted Keycloak redirect URI |
| App role value | Default `User` role value changed to `user` |
| Optional ID token claim | `upn` |
| Microsoft Graph delegated permissions | `User.Read`, `profile` |

Enterprise Application changes:

| Setting | Value |
|---|---|
| `accountEnabled` | `true` |
| `appRoleAssignmentRequired` | `true` |
| `tags` | Adds `HideApp` when missing |
| Default `msiam_access` role | Disabled when present |

```mermaid
flowchart TD
    A[Configure Enterprise Application] --> B[Read App Registration appRoles]
    B --> C{Default User role found?}
    C -- No --> D[Throw error]
    C -- Yes --> E[Set role value to user]
    E --> F[Patch App Registration]
    F --> G[Read Service Principal tags and appRoles]
    G --> H[Add HideApp tag]
    H --> I{msiam_access role exists?}
    I -- Yes --> J[Disable msiam_access]
    I -- No --> K[Leave roles unchanged]
    J --> L[Patch Service Principal]
    K --> L
    L --> M[Return configuration result]
```

### 4. Configure SCIM provisioning

Script:

```powershell
./Configure-ScimProvisioning.ps1 `
  -ServicePrincipalObjectId "<service-principal-object-id>" `
  -TenantUrl "https://keycloak.example/realms/fint/scim/v2/<org-id>/" `
  -SecretToken "" `
  -ProvisionStatus On `
  -EmployeeIdSourceAttribute "extensionAttribute10" `
  -StudentNumberSourceAttribute "extensionAttribute9"
```

This operation:

1. Validates required Graph scopes.
2. Locates a SCIM synchronization template.
3. Sets synchronization secrets.
4. Reuses or creates a synchronization job.
5. Reads the synchronization schema.
6. Updates the target user object to the SCIM core User schema.
7. Adds FINT target attributes.
8. Replaces user attribute mappings.
9. Disables group mappings.
10. Starts or pauses the provisioning job.

```mermaid
sequenceDiagram
    actor Operator
    participant Script as Configure-ScimProvisioning.ps1
    participant Graph as Microsoft Graph
    participant Sync as Entra Synchronization Job
    participant SCIM as FINT SCIM endpoint

    Operator->>Script: Provide Service Principal ID and SCIM Tenant URL
    Script->>Graph: GET /servicePrincipals/{id}/synchronization/templates
    Graph-->>Script: SCIM template
    Script->>Graph: PUT /servicePrincipals/{id}/synchronization/secrets
    Script->>Graph: GET /servicePrincipals/{id}/synchronization/jobs
    alt Existing job found
        Graph-->>Script: Existing job ID
    else No existing job
        Script->>Graph: POST /servicePrincipals/{id}/synchronization/jobs
        Graph-->>Script: New job ID
    end
    Script->>Graph: GET /servicePrincipals/{id}/synchronization/jobs/{jobId}/schema
    Script->>Script: Rename target User object and apply mappings
    Script->>Graph: PUT /servicePrincipals/{id}/synchronization/jobs/{jobId}/schema
    alt ProvisionStatus is On
        Script->>Graph: POST /servicePrincipals/{id}/synchronization/jobs/{jobId}/start
    else ProvisionStatus is Off
        Script->>Graph: POST /servicePrincipals/{id}/synchronization/jobs/{jobId}/pause
    end
    Sync->>SCIM: Provision assigned users via SCIM
```

## SCIM provisioning details

Synchronization secrets configured by the script:

| Key | Value |
|---|---|
| `BaseAddress` | The supplied SCIM tenant URL. |
| `SecretToken` | The supplied secret token. The interactive entrypoint currently passes an empty string. |
| `SyncAll` | `false`, meaning assigned users/groups only. |
| `SyncNotificationSettings` | Delete threshold enabled with value `500`; notifications disabled. |

Provisioning behavior:

| Area | Behavior |
|---|---|
| Template selection | Prefers a template whose ID or description indicates SCIM. Falls back to first template. |
| Job creation | Reuses existing matching job when possible; otherwise creates a new job. |
| Target user object | Renamed to `urn:ietf:params:scim:schemas:core:2.0:User`. |
| User flow types | `Add,Update,Delete`. |
| Groups | Group mappings are disabled. |
| Scope | Assigned users/groups only through `SyncAll=false`. |
| Accidental delete threshold | Enabled with threshold `500`. |

### SCIM target attributes

The SCIM schema is updated with these target attributes:

| Target attribute | Type | Required | Multivalued | Anchor |
|---|---:|---:|---:|---:|
| `id` | `String` | Yes | No | Yes |
| `active` | `Boolean` | No | No | No |
| `emails[type eq "work"].value` | `String` | No | No | No |
| `userName` | `String` | Yes | No | No |
| `externalId` | `String` | Yes | No | No |
| `roles` | `String` | No | Yes | No |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.givenName` | `String` | No | No | No |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.familyName` | `String` | No | No | No |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:userPrincipalName` | `String` | No | No | No |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId` | `String` | No | No | No |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:studentNumber` | `String` | No | No | No |

### SCIM attribute mappings

| Source attribute / expression | Target attribute | Matching priority |
|---|---|---:|
| `objectId` | `userName` | `1` |
| Soft-delete switch expression | `active` | `0` |
| `mail` | `emails[type eq "work"].value` | `0` |
| `objectId` | `externalId` | `0` |
| App role assignments expression | `roles` | `0` |
| `givenName` | `urn:ietf:params:scim:schemas:core:2.0:User:name.givenName` | `0` |
| `surname` | `urn:ietf:params:scim:schemas:core:2.0:User:name.familyName` | `0` |
| `userPrincipalName` | `urn:ietf:params:scim:schemas:extension:fint:2.0:User:userPrincipalName` | `0` |
| `extensionAttribute10` by default | `urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId` | `0` |
| `extensionAttribute9` by default | `urn:ietf:params:scim:schemas:extension:fint:2.0:User:studentNumber` | `0` |

```mermaid
flowchart LR
    subgraph Entra[Microsoft Entra user]
        OID[objectId]
        Mail[mail]
        Given[givenName]
        Surname[surname]
        UPN[userPrincipalName]
        Emp[extensionAttribute10]
        Student[extensionAttribute9]
        Assignments[appRoleAssignments]
        SoftDeleted[IsSoftDeleted]
    end

    subgraph SCIM[FINT SCIM User]
        UserName[userName]
        Active[active]
        Email[emails work value]
        ExternalId[externalId]
        Roles[roles]
        GivenTarget[name.givenName]
        FamilyTarget[name.familyName]
        UpnTarget[fint:userPrincipalName]
        EmpTarget[fint:employeeId]
        StudentTarget[fint:studentNumber]
    end

    OID --> UserName
    OID --> ExternalId
    Mail --> Email
    Given --> GivenTarget
    Surname --> FamilyTarget
    UPN --> UpnTarget
    Emp --> EmpTarget
    Student --> StudentTarget
    Assignments --> Roles
    SoftDeleted --> Active
```

## Claims mapping

The Claims Mapping Policy adds FINT-specific claims to issued tokens.

```mermaid
flowchart LR
    subgraph Source[Entra user source attributes]
        Emp[extensionAttribute10 or configured employee source]
        Student[extensionAttribute9 or configured student source]
    end

    subgraph Policy[Claims Mapping Policy]
        EmpClaim[employee_id]
        StudentClaim[student_number]
    end

    subgraph Token[Token issued to application]
        JWT[JWT / ID token claims]
    end

    Emp --> EmpClaim --> JWT
    Student --> StudentClaim --> JWT
```

Example policy definition shape:

```json
{
  "ClaimsMappingPolicy": {
    "Version": 1,
    "IncludeBasicClaimSet": "true",
    "ClaimsSchema": [
      {
        "Source": "user",
        "ID": "extensionAttribute10",
        "JwtClaimType": "employee_id"
      },
      {
        "Source": "user",
        "ID": "extensionAttribute9",
        "JwtClaimType": "student_number"
      }
    ]
  }
}
```

## Error handling and retry behavior

All Graph operations that use `Invoke-GraphWithRetry` get shared retry behavior.

```mermaid
flowchart TD
    A[Invoke Graph request] --> B{Request succeeds?}
    B -- Yes --> C[Return response]
    B -- No --> D[Extract Graph error details]
    D --> E[Write warning with status, error code, message, innerError, raw body]
    E --> F{Request body exists?}
    F -- Yes --> G[Write failed request body to temp JSON file]
    F -- No --> H[No body file written]
    G --> I{NoRetryOnBadRequest and HTTP 400?}
    H --> I
    I -- Yes --> J[Throw immediately]
    I -- No --> K{Max attempts reached?}
    K -- Yes --> J
    K -- No --> L[Sleep with exponential backoff]
    L --> A
```

Retry defaults:

| Setting | Default |
|---|---:|
| Maximum attempts | `12` |
| Initial delay | `5` seconds |
| Maximum delay | `60` seconds |

When a request with a JSON body fails, the body is written to a temporary file named like:

```text
graph-failed-request-<timestamp>-<guid>.json
```

This helps debug schema and provisioning payload issues.

## Troubleshooting

### Graph context has missing or extra scopes

The scripts intentionally fail when the active context does not exactly match required scopes.

Check the active context from the menu:

```text
5. Show Active Graph Context
```

Then reconnect using the expected permissions for the operation.

### Existing Enterprise Application cannot be found

When connecting an existing application, the script expects an Application AppId GUID.

It then looks up:

1. A matching Service Principal using `appId`.
2. A matching App Registration using the same `appId`.

The script fails if either lookup returns zero or multiple matches.

### Default User app role is missing

`Configure-EnterpriseApplication.ps1` expects a default User role and changes its value to `user`.

The script searches for a role where:

- `displayName` is `User`, or
- `value` is `User`, or
- `value` is `user`

If none is found, configuration stops.

### SCIM template list is empty

`Configure-ScimProvisioning.ps1` waits and retries while Graph prepares synchronization templates.

If templates are still empty after the retry loop, the script stops because it cannot create a provisioning job without a template.

### SCIM user object mapping cannot be found

The script searches the synchronization schema for an object mapping where:

- `sourceObjectName` is `User`
- `targetObjectName` ends with `User`

If this mapping is not found, the script warns and skips mapping updates. Inspect the schema URL shown in the warning.

### Graph returns HTTP 400 during schema or secret updates

Some calls use `-NoRetryOnBadRequest`, so HTTP 400 errors fail immediately.

Check the warning output and the temporary failed request body JSON file for the exact payload sent to Microsoft Graph.

## Security notes

- Do not commit client secrets or SCIM tokens.
- Prefer environment-specific secret handling outside the repository.
- Review the generated Claims Mapping Policy before using it in production.
- Review SCIM source attributes before enabling provisioning.
- Confirm the SCIM tenant URL points to the intended FINT tenant/organization.

## Suggested run order

For a new setup:

```mermaid
flowchart TD
    A[Run New-Novari-IDP.ps1] --> B[Login to Microsoft Graph]
    B --> C[1. Create Enterprise Application]
    C --> D[2. Create and assign Claims Mapping Policy]
    D --> E[3. Configure Enterprise Application and App Registration]
    E --> F[4. Configure FINT SCIM Provisioning]
    F --> G[Assign users or groups to Enterprise Application]
    G --> H[Verify provisioning in Entra and target SCIM system]
```

For an existing setup:

```mermaid
flowchart TD
    A[Run New-Novari-IDP.ps1] --> B[Login to Microsoft Graph]
    B --> C[Connect existing Enterprise Application by Application AppId]
    C --> D[2. Create and assign Claims Mapping Policy if missing]
    D --> E[3. Reconfigure application settings if needed]
    E --> F[4. Configure or repair SCIM provisioning]
```
