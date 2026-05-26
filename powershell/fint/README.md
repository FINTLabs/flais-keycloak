# Overview

This directory contains a guided setup script for connecting Microsoft Entra ID to Keycloak.

It is intended for people who need to prepare an Entra Enterprise Application so it can work as an identity provider and provisioning source. In practical terms, the scripts help set up the application, define the information Keycloak should receive about users, and configure automatic user provisioning through SCIM.

## What problem this solves

A integration with Microsoft Entra ID needs several pieces to line up:

- an Enterprise Application in Entra
- an App Registration connected to that application
- the right claims so Keycloak receives the user identifiers it expects
- SCIM provisioning so users and access information can be synchronized
- application roles that describe what users are allowed to access

Doing this manually can be repetitive and easy to get slightly wrong. This directory provides a guided setup flow that keeps those steps consistent.

## Start

The setup works like this:

1. The user starts the main script.
2. The script connects to Microsoft Graph.
3. The user either creates a new Enterprise Application or connects to an existing one.
4. The user chooses setup actions from a menu.
5. Each menu action delegates to a focused module.
6. Shared helper files handle things like input prompts, Graph connection checks, retries, and readable console output.

The main script is the front door. The modules do the actual setup work. The helpers keep the user experience and safety checks consistent.


### `Novari-IDP.ps1`

This is the main entry point.

It shows the header, asks for Microsoft Graph connection details, lets the user connect to an existing Enterprise Application if needed, and then presents the setup menu.

Think of this file as the coordinator for the whole setup.

### `modules/`

This folder contains the main setup actions.

| File | Purpose |
|---|---|
| `Create-EnterpriseApplication.ps1` | Creates the Enterprise Application that represents the integration in Entra. |
| `Configure-Application.ps1` | Applies the expected settings to the Enterprise Application and App Registration. |
| `Configure-ClaimsMappingPolicy.ps1` | Defines which user information is sent as claims to. |
| `Configure-ScimProvisioning.ps1` | Configures SCIM provisioning so users and access information can be synchronized. |
| `Configure-AppRoles.ps1` | Applies the application roles used to represent access in related services. |

Each module focuses on one part of the overall setup. This makes it easier to review, reuse, and troubleshoot a single area without reading the whole project at once.

### `helpers/`

This folder contains shared support code used by the main script and modules.

| File | Purpose |
|---|---|
| `ConsoleHelpers.ps1` | Makes console output easier to read. |
| `EnterpriseApplicationHelpers.ps1` | Validates and displays information about the selected Enterprise Application. |
| `GenericHelpers.ps1` | Handles common prompts and yes/no style input. |
| `GraphContext.ps1` | Connects to Microsoft Graph and shows the active connection. |
| `GraphRetry.ps1` | Adds retry and error handling around Microsoft Graph operations. |
| `Header.ps1` | Prints the Novari header shown when the tool starts. |
| `Menu.ps1` | Defines the interactive menu and routes choices to the right setup action. |
| `RequiredScopes.ps1` | Checks that the Microsoft Graph connection has the expected permissions. |

The helpers are not separate setup steps. They are there to keep the experience predictable and to avoid repeating the same checks in every module.

### `app-roles.json`

This file is the role catalogue used when configuring application roles.

It describes the roles that can be added to the application, including their names, values, and descriptions. The setup uses this catalogue so that role definitions are managed in one place instead of being scattered across scripts.


## Conceptual flow

```text
Start tool
   ↓
Connect to Microsoft Graph
   ↓
Create or select Enterprise Application
   ↓
Configure claims
   ↓
Configure application settings
   ↓
Configure SCIM provisioning
   ↓
Configure roles
   ↓
Review in Microsoft Entra
```

The exact order may vary depending on whether the Enterprise Application already exists, but this is the intended shape of the setup.

## Key concepts

### Enterprise Application

The Enterprise Application is the tenant-side representation of the integration in Microsoft Entra ID. It is the object administrators see and manage when assigning users, configuring provisioning, and reviewing sign-in/provisioning status.

### App Registration

The App Registration is the application definition behind the Enterprise Application. Some settings live here, such as redirect behavior and token-related configuration.

### Claims

Claims are pieces of user information sent during authentication. This setup includes a claims mapping policy so Keycloak receives the identifiers it expects, such as employee or student identifiers.

### SCIM provisioning

SCIM provisioning is used to synchronize users from Entra to Keycloak.

## Design principles

This directory is organized around a few simple principles:

- **One main entry point:** Start from `Novari-IDP.ps1`.
- **One responsibility per module:** Each setup area has its own file.
- **Shared safety checks:** Graph permissions, selected application details, and retry handling are centralized.
- **Readable interaction:** The tool is menu-driven and prints context while it runs.

## Before running it

Before using the tool, the person running it should know:

- which Microsoft Entra tenant the integration belongs to
- whether a new Enterprise Application should be created or an existing one should be reused
- the expected redirect URI for the identity provider setup
- the SCIM endpoint/base URL
- which Entra attributes should be used for employee and student identifiers
- which organization value should be used when applying organization-specific role values

The person running the tool also needs a Microsoft Graph connection with the required rights to create and update the relevant Entra objects.

## What to review after running it

After the setup has been run, review the result in Microsoft Entra and Keycloak:

- the Enterprise Application exists and has the expected name
- application roles look correct
- the expected claims are present
- provisioning is configured and has the expected status
- SCIM provisioning logs do not show unexpected errors
