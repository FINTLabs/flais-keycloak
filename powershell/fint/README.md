# Novari IDP setup

This directory contains a guided setup script for connecting Microsoft Entra ID to Keycloak.

The tool helps prepare an Entra Enterprise Application so it can be used as an identity provider and provisioning source for Keycloak.

Before running the tool, complete the initial setup in [`setup.md`](setup.md).

## What the tool configures

The setup can help configure:

- an Enterprise Application in Microsoft Entra ID
- the connected App Registration
- claims sent from Entra ID to Keycloak
- SCIM provisioning from Entra ID to Keycloak
- application roles
- a client secret for Keycloak

## Start the setup

Run the main script:

```powershell
./Novari-IDP.ps1
```

The script connects to Microsoft Graph, lets you create or select an Enterprise Application, and then shows a menu of setup actions.

## Files

### `Novari-IDP.ps1`

Main entry point for the setup.

Use this file to start the tool, connect to Microsoft Graph, select an Enterprise Application, and open the setup menu.

### `modules/`

Setup actions used by the main script.

| File | Purpose |
|---|---|
| `Create-EnterpriseApplication.ps1` | Creates the Enterprise Application in Entra. |
| `Configure-Application.ps1` | Configures the Enterprise Application and App Registration. |
| `Configure-ClaimsMappingPolicy.ps1` | Configures claims sent to Keycloak. |
| `Configure-ScimProvisioning.ps1` | Configures SCIM provisioning. |
| `Configure-AppRoles.ps1` | Configures application roles. |
| `Create-ClientSecret.ps1` | Creates a new client secret and prints the one-time value. |

### `helpers/`

Shared helper scripts used by the main script and modules.

| File | Purpose |
|---|---|
| `ConsoleHelpers.ps1` | Formats console output. |
| `EnterpriseApplicationHelpers.ps1` | Validates and displays the selected Enterprise Application. |
| `GenericHelpers.ps1` | Handles common prompts. |
| `GraphContext.ps1` | Connects to Microsoft Graph and shows the active context. |
| `GraphRetry.ps1` | Adds retry handling around Graph operations. |
| `Header.ps1` | Prints the startup header. |
| `Menu.ps1` | Defines the setup menu. |
| `RequiredScopes.ps1` | Checks required Microsoft Graph permissions. |

### `app-roles.json`

Role catalogue used when configuring application roles.

## Menu actions

| Option | Action |
|---|---|
| 1 | Create Enterprise Application, shown only when no application is connected in the current session. |
| 2 | Create and assign Claims Mapping Policy. |
| 3 | Configure Enterprise Application and App Registration settings. |
| 4 | Configure SCIM Provisioning. |
| 5 | Configure Application Roles. |
| 6 | Configure Application Owner. |
| 7 | Create Client Secret. |
| 8 | Show Active Graph Context. |
| 9 | Show Current Enterprise Application. |
| 0 | Exit. |

## Before running

See [`setup.md`](setup.md) for required prerequisites.

You should also know:

- which Entra tenant to use
- whether to create a new Enterprise Application or reuse an existing one
- the redirect URI for Keycloak
- the SCIM endpoint or base URL
- which Entra attributes to use for employee and student identifiers
- which organization value to use for organization-specific role values

## After running

Review the result in Microsoft Entra and Keycloak:

- the Enterprise Application has the expected name
- application roles are correct
- expected claims are present
- SCIM provisioning is configured
- provisioning logs do not show unexpected errors
- the client secret has been copied into Keycloak and stored securely
