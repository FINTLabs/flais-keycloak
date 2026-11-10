# Authentication Flow: flais-first-broker-login-scim

[Back: Authentication Flows](README.md)

This document describes the Keycloak Authentication Flow **flais-first-broker-login-scim**,
which is executed during first login via an external Identity Provider (IDP).

This flow is responsible for:

- Requiring an existing user account provisioned through SCIM
- Linking the external identity to the existing account
- Rejecting login if no matching user exists

## Flow type

- **Type:** First Broker Login
- **Trigger:** User authenticates via an external IDP and the external identity is not yet linked to a local user account

## Top-level structure

Both executions run in the following order:

| Step                            | Type | Requirement | Purpose                                                         |
| ------------------------------- | ---- | ----------- | --------------------------------------------------------------- |
| FLAIS Detect Existing User      | Step | Required    | Detect a matching existing user and reject login if none exists |
| Automatically set existing user | Step | Required    | Select the existing user for linking to the external identity   |

## Execution: FLAIS Detect Existing User

**Requirement:** Required

This execution checks for a matching existing user account.

If no matching user exists, authentication fails. The flow does not create
a new account.

### Configuration

| Setting                         | Value                                                         | Purpose                                       |
| ------------------------------- | ------------------------------------------------------------- | --------------------------------------------- |
| Alias                           | `flais-first-broker-login-scim - detect-existing-user-config` | Identifies this execution's configuration     |
| Authenticator Reference         | Not set                                                       | No explicit value configured                  |
| Authenticator Reference Max Age | Not set                                                       | No explicit value configured                  |
| Error message key               | `userNotProvisioned`                                          | Message key used when no matching user exists |

### Notes

- The user must be provisioned through SCIM before their first broker login.
- The displayed error text depends on the translation configured for `userNotProvisioned`.

## Execution: Automatically set existing user

**Requirement:** Required

This execution selects the matching existing user so that Keycloak can
link the external identity to that account.

### Notes

- This step runs after **FLAIS Detect Existing User** succeeds.
- It does not create a new user.
- Both executions must succeed for this flow to complete.

## Responsibilities summary

This flow ensures that:

- First broker login requires an existing SCIM-provisioned account
- The external identity is linked to the matching existing account
- Login is rejected when no matching user exists
- No user account is created by this flow
- No organization onboarding execution is included in this flow
