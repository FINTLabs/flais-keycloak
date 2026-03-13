# Client scopes

Configuration of the **Client Scopes**.

> **Assigned type: None** means the scope is not in use and will not be automatically added to clients.

> Protocols other than OpenID Connect are removed.

# Active scopes

## acr

| Setting                             | Value                                                                                     |
| ----------------------------------- | ----------------------------------------------------------------------------------------- |
| Name                                | `acr`                                                                                     |
| Description                         | OpenID Connect scope for adding ACR (Authentication Context Class Reference) to the token |
| Type                                | Default                                                                                   |
| Protocol                            | OpenID Connect                                                                            |
| Display on consent screen           | Off                                                                                       |
| Include in token scope              | Off                                                                                       |
| Include in OpenID Provider Metadata | On                                                                                        |
| Display order                       | Not specified                                                                             |

### Mappers

| Name            | Mapper type                                  | Tokens                                      | Priority |
| --------------- | -------------------------------------------- | ------------------------------------------- | -------- |
| `acr loa level` | Authentication Context Class Reference (ACR) | ID token, Access token, Token introspection | 0        |

---

## basic

| Setting                             | Value                                                     |
| ----------------------------------- | --------------------------------------------------------- |
| Name                                | `basic`                                                   |
| Description                         | OpenID Connect scope for adding basic claims to the token |
| Type                                | Default                                                   |
| Protocol                            | OpenID Connect                                            |
| Display on consent screen           | Off                                                       |
| Include in token scope              | Off                                                       |
| Include in OpenID Provider Metadata | On                                                        |
| Display order                       | Not specified                                             |

### Mappers

| Name        | Mapper type       | Claim / Attribute       | Tokens                                                | Priority |
| ----------- | ----------------- | ----------------------- | ----------------------------------------------------- | -------- |
| `sub`       | Subject (sub)     | subject                 | Access token, Token introspection                     | -10      |
| `auth_time` | User Session Note | `AUTH_TIME → auth_time` | ID token, Access token, Userinfo, Token introspection | 0        |

---

## email

| Setting                             | Value                         |
| ----------------------------------- | ----------------------------- |
| Name                                | `email`                       |
| Description                         | OpenID Connect built-in scope |
| Type                                | Default                       |
| Protocol                            | OpenID Connect                |
| Display on consent screen           | On                            |
| Consent text                        | `${emailScopeConsentText}`    |
| Include in token scope              | On                            |
| Include in OpenID Provider Metadata | On                            |
| Display order                       | Not specified                 |

### Mappers

| Name             | Mapper type    | Source          | Claim            | Tokens                                                |
| ---------------- | -------------- | --------------- | ---------------- | ----------------------------------------------------- |
| `email`          | User Attribute | `email`         | `email`          | ID token, Access token, Userinfo, Token introspection |
| `email verified` | User Property  | `emailVerified` | `email_verified` | ID token, Access token, Userinfo, Token introspection |

---

## profile

| Setting                             | Value                         |
| ----------------------------------- | ----------------------------- |
| Name                                | `profile`                     |
| Description                         | OpenID Connect built-in scope |
| Type                                | Default                       |
| Protocol                            | OpenID Connect                |
| Display on consent screen           | On                            |
| Consent text                        | `${profileScopeConsentText}`  |
| Include in token scope              | On                            |
| Include in OpenID Provider Metadata | On                            |
| Display order                       | Not specified                 |

### Mappers

| Name          | Mapper type      | Source      | Claim                | Tokens                                                |
| ------------- | ---------------- | ----------- | -------------------- | ----------------------------------------------------- |
| `given name`  | User Attribute   | `firstName` | `given_name`         | ID token, Access token, Userinfo, Token introspection |
| `username`    | User Attribute   | `username`  | `preferred_username` | ID token, Access token, Userinfo, Token introspection |
| `full name`   | User's full name | derived     | `name`               | ID token, Access token, Userinfo, Token introspection |
| `family name` | User Attribute   | `lastName`  | `family_name`        | ID token, Access token, Userinfo, Token introspection |

---

## roles

| Setting                             | Value                               |
| ----------------------------------- | ----------------------------------- |
| Name                                | `roles`                             |
| Description                         | Adds user roles to the access token |
| Type                                | Default                             |
| Protocol                            | OpenID Connect                      |
| Display on consent screen           | On                                  |
| Consent text                        | `${rolesScopeConsentText}`          |
| Include in token scope              | Off                                 |
| Include in OpenID Provider Metadata | On                                  |

### Mappers

| Name         | Mapper type    | Source  | Claim   | Tokens       |
| ------------ | -------------- | ------- | ------- | ------------ |
| `user roles` | User Attribute | `roles` | `roles` | Access token |

---

## web-origins

| Setting                             | Value                                        |
| ----------------------------------- | -------------------------------------------- |
| Name                                | `web-origins`                                |
| Description                         | Adds allowed web origins to the access token |
| Type                                | Default                                      |
| Protocol                            | OpenID Connect                               |
| Display on consent screen           | On                                           |
| Consent text                        | `${profileScopeConsentText}`                 |
| Include in token scope              | Off                                          |
| Include in OpenID Provider Metadata | On                                           |

### Mappers

| Name                  | Mapper type         | Tokens                            |
| --------------------- | ------------------- | --------------------------------- |
| `allowed web origins` | Allowed Web Origins | Access token, Token introspection |

---

## organization

| Setting                             | Value                                                                |
| ----------------------------------- | -------------------------------------------------------------------- |
| Name                                | `organization`                                                       |
| Description                         | Additional claims describing the organization the subject belongs to |
| Type                                | Optional                                                             |
| Protocol                            | OpenID Connect                                                       |
| Display on consent screen           | On                                                                   |
| Consent text                        | `${organizationScopeConsentText}`                                    |
| Include in token scope              | On                                                                   |
| Include in OpenID Provider Metadata | On                                                                   |

### Mappers

| Name                  | Mapper type                     | Source attribute      | Claim                | Tokens                                      |
| --------------------- | ------------------------------- | --------------------- | -------------------- | ------------------------------------------- |
| `organization number` | Organization specific attribute | `ORGANIZATION_NUMBER` | `organizationnumber` | ID token, Access token                      |
| `tenant id`           | Organization specific attribute | `TENANT_ID`           | `tenantid`           | ID token, Access token                      |
| `organization`        | Organization Membership         | membership            | `organization`       | ID token, Access token, Token introspection |
| `organization id`     | Organization specific attribute | `ORGANIZATION_ID`     | `organizationid`     | ID token, Access token                      |

---

# Built-in scopes (not in use)

These scopes exist in the realm but are not assigned (`Type: None`).

| Scope              | Description                                      | Protocol       |
| ------------------ | ------------------------------------------------ | -------------- |
| `microprofile-jwt` | Microprofile JWT built-in scope                  | OpenID Connect |
| `offline_access`   | OpenID Connect built-in scope for refresh tokens | OpenID Connect |
| `phone`            | OpenID Connect built-in scope: phone claims      | OpenID Connect |
| `service_account`  | Scope used for service account enabled clients   | OpenID Connect |
