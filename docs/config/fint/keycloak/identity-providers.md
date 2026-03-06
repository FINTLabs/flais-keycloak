# Identity providers

Configuration of a single **Identity Provider (IDP)**.

This configuration is based on an Entra ID OpenID Connect connection and serves as a template.
Some settings may differ depending on the vendor or specific integration.

# Settings

## General settings

| Setting       | Value / Guidance                                                |
| ------------- | --------------------------------------------------------------- |
| Alias         | Lowercase. Use `-` for multi-word names (example: `novari-iks`) |
| Display name  | Example: `Novari IKS`                                           |
| Display order | Not specified                                                   |

## OpenID Connect settings

| Setting                               | Value                              |
| ------------------------------------- | ---------------------------------- |
| Authorization / Token / Userinfo URLs | Vendor specific                    |
| Validate signatures                   | On                                 |
| Use JWKS URL                          | On                                 |
| Use PKCE                              | On                                 |
| PKCE Method                           | `S256`                             |
| Client authentication                 | Client secret sent in request body |
| Client assertion signature algorithm  | Not specified                      |

## OpenID Connect – Advanced

| Setting                                  | Value       |
| ---------------------------------------- | ----------- |
| Pass login_hint                          | Off         |
| Pass max_age                             | Off         |
| Pass current locale                      | Off         |
| Backchannel logout                       | Off         |
| Send `id_token_hint` in logout requests  | On          |
| Send `client_id` in logout requests      | Off         |
| Disable user info                        | Off         |
| Disable nonce                            | Off         |
| Disable type claim check                 | Off         |
| Scopes                                   | `profile`   |
| Prompt                                   | Unspecified |
| Accept `prompt=none` forward from client | Off         |
| Requires short state parameter           | Off         |
| Allowed clock skew                       | `0`         |
| Forwarded query parameters               | None        |

## Advanced settings

| Setting                   | Value                       |
| ------------------------- | --------------------------- |
| Store tokens              | Off                         |
| Stored tokens readable    | Off                         |
| Access Token is JWT       | Off                         |
| Trust Email               | On                          |
| Account linking only      | Off                         |
| Hide on login page        | On                          |
| Show in account console   | Always                      |
| Verify essential claim    | Off                         |
| First login flow override | `First login flow override` |
| Post login flow           | None                        |
| Sync mode                 | Force                       |
| Case-sensitive username   | Off                         |

# Mappers

## Username mapping

| Name                  | Mapper type                | Template       | Target      | Sync mode |
| --------------------- | -------------------------- | -------------- | ----------- | --------- |
| `map_oid_as_username` | Username Template Importer | `${CLAIM.oid}` | `LOCAL`     | Inherit   |
| `map_oid_as_brokerid` | Username Template Importer | `${CLAIM.oid}` | `BROKER_ID` | Inherit   |

## Attribute mapping

| Name                             | Mapper type        | Claim        | Target attribute    | Sync mode |
| -------------------------------- | ------------------ | ------------ | ------------------- | --------- |
| `map_employee_id_to_employee_id` | Attribute importer | `employeeId` | `employeeId`        | Inherit   |
| `map_upn_to_user_principal_name` | Attribute importer | `upn`        | `userPrincipalName` | Inherit   |
| `map_roles_to_roles`             | Attribute importer | `roles`      | `roles`             | Inherit   |
| `map_oid_to_external_id`         | Attribute importer | `oid`        | `externalId`        | Inherit   |
