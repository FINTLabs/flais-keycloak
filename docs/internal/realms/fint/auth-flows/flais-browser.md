# Authentication Flow: flais-browser

[Back: Authentication Flows](README.md)

This document describes the Keycloak Authentication Flow **flais-browser**.

This flow is the entry point for browser-based login.

## Flow type

- **Type:** Browser Login
- **Trigger:** User login via HTTP (Browser)

## Top-level executions

| Step / Subflow                | Type           | Requirement | Notes                                      |
| ----------------------------- | -------------- | ----------- | ------------------------------------------ |
| FLAIS Organization SSO Cookie | execution      | Alternative | Custom SSO cookie check with org awareness |
| flais-browser - Organization  | flow (subflow) | Alternative | Custom org/idp selection chain             |

## Subflow: flais-browser - Organization

Executions (in order):

| Step                                            | Requirement | Purpose                                                        |
| ----------------------------------------------- | ----------- | -------------------------------------------------------------- |
| FLAIS Organization Selection                    | Required    | Select eligible organization (auto-select if only one)         |
| FLAIS Organization Identity Provider Selection  | Required    | Determine eligible IDPs for org (prompt if multiple)           |
| FLAIS Remember Organization Confirmation        | Required    | Remember or forget the selected org for this client            |
| FLAIS Organization Session Commit               | Required    | Commit selected org into session, auth notes, and client notes |
| FLAIS Organization Identity Provider Redirector | Required    | Redirect to chosen IDP                                         |

## Configuration

Configure execution settings under **Authentication → Flows**.
The SSO cookie, IDP selection, session commit, and redirect executions have no
custom settings.

### FLAIS Organization Selection

| Setting                           | Key                         | Default | Purpose                                                          |
| --------------------------------- | --------------------------- | ------- | ---------------------------------------------------------------- |
| Preselect remembered organization | `useRememberedOrganization` | `true`  | Preselect a remembered organization still allowed for the client |

This setting does not change the remember confirmation execution.

### FLAIS Remember Organization Confirmation

| Setting                               | Key                | Default | Purpose                                       |
| ------------------------------------- | ------------------ | ------- | --------------------------------------------- |
| Remember organization lifetime (days) | `cookieMaxAgeDays` | `30`    | Lifetime of the saved organization preference |

Accepts positive whole days from `1` to `24855`.

### Notes

- Remember confirmation runs after IDP selection and before session commit.
- A matching remembered organization skips confirmation. Otherwise, Yes saves the selection and No clears it.
- Preferences are separate for each client and must still satisfy the client's organization permissions.
- Successful SSO skips the organization subflow, including remember confirmation.

## Execution sequence

The sequence diagram below illustrates how Keycloak executes
the `flais-browser` authentication flow using custom authenticators.

```mermaid
sequenceDiagram
  participant U as User (Browser)
  participant KC as Keycloak
  participant OCA as OrgCookieAuthenticator
  participant OS as OrgSelectionUiAuthenticator
  participant OIP as OrgIdpSelectionUiAuthenticator
  participant ORM as OrgRememberUiAuthenticator
  participant OSC as OrgSessionCommitAuthenticator
  participant OR as OrgRedirectorAuthenticator
  participant OP as OrganizationProvider
  participant IDPS as IdentityProviderStore
  participant IDP as External IDP

  U->>KC: Open login page
  KC->>OCA: authenticate()

  alt Valid SSO cookie exists
    OCA->>OCA: Validate cookie
    alt Organization scope present and org allowed for client
      OCA->>OCA: Restore org context from session
      OCA->>KC: success()
      KC->>U: Return code (SSO)
    else Organization scope absent
      OCA->>KC: success()
      KC->>U: Return code (SSO)
    else Org not allowed for client
      OCA->>KC: attempted()
    end
  else No valid SSO cookie
    OCA->>KC: attempted()
  end

  Note over KC,OS: Continue here only when SSO did not complete login
  KC->>OS: authenticate()
  OS->>OP: list organizations
  OS->>KC: filter eligible organizations (whitelist/blacklist)

  alt Only one eligible org
    OS->>OS: setAuthNote(ORG_ID)
    OS->>KC: success()
  else Multiple eligible orgs
    OS->>OS: Resolve allowed remembered alias if preselection enabled
    OS->>U: show org selection form with defaultSelectedOrg
    U->>KC: submit selected org
    KC->>OS: action()
    OS->>OS: setAuthNote(ORG_ID)
    OS->>KC: success()
  end

  KC->>OIP: authenticate()
  OIP->>OIP: readAuthNote(ORG_ID)
  OIP->>IDPS: list and filter IDPs for org

  alt No enabled IDPs
    OIP->>U: error page
  else Exactly one enabled IDP
    OIP->>OIP: setAuthNote(IDENTITY_PROVIDER)
    OIP->>KC: success()
  else Multiple enabled IDPs
    OIP->>U: show IDP selection form
    U->>KC: submit selected IDP
    KC->>OIP: action()
    OIP->>OIP: setAuthNote(IDENTITY_PROVIDER)
    OIP->>KC: success()
  end

  KC->>ORM: authenticate()
  ORM->>ORM: Validate selected org is still allowed
  ORM->>ORM: Read remembered cookie for client
  alt Cookie matches selected org
    ORM->>KC: success()
  else Cookie missing or selected org changed
    ORM->>U: show remember organization confirmation
    U->>KC: submit Yes or No
    KC->>ORM: action()
    ORM->>ORM: Revalidate org and parse boolean choice
    alt Yes
      ORM->>U: Set client cookie with configured lifetime
    else No
      ORM->>U: Expire client cookie
    end
    ORM->>KC: success()
  end

  KC->>OSC: authenticate()
  OSC->>OSC: readAuthNote(ORG_ID)
  OSC->>OP: getById(orgId)
  OSC->>OSC: commit org to authSession, clientNote, userSessionNote
  OSC->>KC: success()

  KC->>OR: authenticate()
  OR->>OR: readAuthNote(IDENTITY_PROVIDER)
  OR->>U: redirect to IDP
  U->>IDP: authenticate
  IDP->>KC: callback → triggers post-broker-login flow
  KC->>U: login complete
```
