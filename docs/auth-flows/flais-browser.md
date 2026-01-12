# Authentication Flow: flais-browser

This document describes the Keycloak Authentication Flow **flais-browser** (Default).

This flow is the entry point for browser-based login

## Flow type

-   **Type:** Browser Login
-   **Requirement:** Default
-   **Trigger:** User login via HTTP (Browser)

## Top-level executions

| Step / Subflow                      | Type           | Requirement | Notes                          |
| ----------------------------------- | -------------- | ----------- | ------------------------------ |
| Cookie                              | execution      | Alternative | Standard KC cookie SSO check   |
| flais-browser - Organization config | flow (subflow) | Alternative | Custom org/idp selection chain |

## Subflow: flais-browser - Organization config

Executions (in order):

| Step             | Requirement | Purpose                                                |
| ---------------- | ----------- | ------------------------------------------------------ |
| Org Selector     | Required    | Select eligible organization (auto-select if only one) |
| Org Idp Selector | Required    | Determine eligible IDPs for org (prompt if multiple)   |
| Org Redirector   | Required    | Redirect to chosen IDP alias                           |

## Execution sequence

The sequence diagram below illustrates how Keycloak executes
the `flais-browser` authentication flow using custom authenticators.

It reflects the execution order configured in the Keycloak Admin UI.

```mermaid
sequenceDiagram
  participant U as User (Browser)
  participant KC as Keycloak
  participant OS as OrgSelectorAuthenticator
  participant OIP as OrgIdpSelectorAuthenticator
  participant OR as OrgRedirectorAuthenticator
  participant OP as OrganizationProvider
  participant IDPS as IdentityProviderStore
  participant IDP as External IDP

  U->>KC: Open login page
  KC->>OS: authenticate()
  OS->>OP: list organizations
  OS->>KC: filter eligible organizations

  alt only one eligible org
    OS->>OS: setAuthNote(ORG_ID)
    OS->>KC: success()
  else multiple eligible orgs
    OS->>U: show org selection form
    U->>KC: submit selected org
    KC->>OS: action()
    OS->>OS: setAuthNote(ORG_ID)
    OS->>KC: success()
  end

  KC->>OIP: authenticate()
  OIP->>OIP: readAuthNote(ORG_ID)
  OIP->>IDPS: list and filter IDPs

  alt no enabled IDPs
    OIP->>U: error page
  else exactly one enabled IDP
    OIP->>OIP: setAuthNote(IDENTITY_PROVIDER)
    OIP->>KC: success()
  else multiple enabled IDPs
    OIP->>U: show IDP selection form
    U->>KC: submit selected IDP
    KC->>OIP: action()
    OIP->>OIP: setAuthNote(IDENTITY_PROVIDER)
    OIP->>KC: success()
  end

  KC->>OR: authenticate()
  OR->>OR: readAuthNote(IDENTITY_PROVIDER)
  OR->>U: redirect to IDP
  U->>IDP: authenticate
  IDP->>KC: callback
  KC->>U: login complete
```
