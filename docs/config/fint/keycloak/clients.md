# Clients

Configuration of the **Clients**.

Configuration for a single Client serves as a template.
Some settings may differ depending on the use-case.

# Default clients

All default clients are disabled.

| Client                   | Status   |
| ------------------------ | -------- |
| `account`                | Disabled |
| `admin-cli`              | Disabled |
| `broker`                 | Disabled |
| `realm-management`       | Disabled |
| `security-admin-console` | Disabled |
| `_system`                | Disabled |

# Client (Public)

Example configuration for a public client.

## General settings

| Setting                  | Value / Guidance      |
| ------------------------ | --------------------- |
| Client ID                | Any unique identifier |
| Name                     | Any                   |
| Description              | Any                   |
| Always display in the UI | Off                   |

## Access settings

| Setting                         | Value / Guidance                               |
| ------------------------------- | ---------------------------------------------- |
| Root URL                        | Not specified                                  |
| Home URL                        | Not specified                                  |
| Valid redirect URIs             | Valid URI pattern the browser can redirect to  |
| Valid post logout redirect URIs | Valid URI pattern the browser can redirect to  |
| Web origins                     | `+` (same as redirect URIs) or specify origins |
| Admin URL                       | Not specified                                  |

## Capability configuration

| Setting                   | Value         |
| ------------------------- | ------------- |
| Client authentication     | Off           |
| Authorization             | Off           |
| Authentication flow       | Standard flow |
| PKCE Method               | `S256`        |
| Require DPoP bound tokens | Off           |

## Login settings

| Setting                  | Value         |
| ------------------------ | ------------- |
| Login theme              | Not specified |
| Consent required         | Off           |
| Display client on screen | Off           |
| Consent screen text      | Not specified |

## Logout settings

| Setting                               | Value         |
| ------------------------------------- | ------------- |
| Front channel logout                  | On            |
| Front-channel logout URL              | Not specified |
| Front-channel logout session required | On            |
| Logout confirmation                   | Off           |

## Roles

| Setting      | Value |
| ------------ | ----- |
| Client roles | None  |

## Client scopes

The client uses the default scopes defined in the `Client scopes` configuration.

| Scope                   | Setting            | Value |
| ----------------------- | ------------------ | ----- |
| `<client-id>-dedicated` | Full scope allowed | Off   |
