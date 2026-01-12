# Authentication Flow: flais-first-broker-login

This document describes the Keycloak Authentication Flow**flais-first-broker-login**,
which is executed during first login via an external Identity Provider (IDP).

This flow is responsible for:

-   Creating or linking users
-   Assigning organization membership
-   Performing onboarding

## Flow type

-   **Type:** First Broker Login
-   **Requirement:** Default
-   **Trigger:** User authenticates via an external IDP and no existing broker session exists

## Top-level structure

| Step / Subflow                                                           | Type    | Requirement | Purpose                         |
| ------------------------------------------------------------------------ | ------- | ----------- | ------------------------------- |
| flais-first-broker-login – User creation or linking                      | Subflow | Required    | Resolve or create the user      |
| flais-first-broker-login – First Broker Login – Conditional Organization | Subflow | Conditional | Perform organization onboarding |

## Subflow: User creation or linking

**Requirement:** Required

This subflow determines whether the external identity should be
linked to an existing user or result in a new user being created.

### Executions (in order)

| Step                            | Type | Requirement | Purpose                                      |
| ------------------------------- | ---- | ----------- | -------------------------------------------- |
| Create User If Unique           | Step | Alternative | Create a new user if no matching user exists |
| Automatically set existing user | Step | Alternative | Link external identity to an existing user   |

### Notes

-   Exactly one of the alternatives must succeed
-   User uniqueness is evaluated within the realm

## Subflow: First Broker Login – Conditional Organization

**Requirement:** Conditional

This subflow handles organization-related onboarding logic
and only executes when its condition evaluates to true.

### Condition

| Condition                   | Requirement | Purpose                                     |
| --------------------------- | ----------- | ------------------------------------------- |
| Condition – user configured | Required    | Ensure user is not already fully configured |

If the condition fails, the subflow is skipped.

### Executions (when condition passes)

| Step                        | Type | Requirement | Purpose                                                      |
| --------------------------- | ---- | ----------- | ------------------------------------------------------------ |
| Organization Member Onboard | Step | Required    | Assign user to organization and initialize org-specific data |

## Responsibilities summary

This flow ensures that:

-   A user is always resolved (created or linked) after broker login
-   Organization onboarding only happens once
-   Organization membership is applied consistently
-   Existing users are not re-onboarded

## Notes

-   This flow must remain deterministic to avoid duplicate users
-   Organization onboarding must be idempotent
-   Any failure in required steps results in login failure
