# Realm settings

[Back: Keycloak](README.md)

Configuration of the **Realm settings**.

## General

| Setting       | Value   |
| ------------- | ------- |
| Organizations | Enabled |

## Login

### Login screen

| Setting           | Value |
| ----------------- | ----- |
| User registration | Off   |
| Forgot password   | Off   |
| Remember me       | Off   |

### Email settings

| Setting           | Value |
| ----------------- | ----- |
| Email as username | Off   |
| Login with email  | Off   |
| Duplicate emails  | On    |
| Verify email      | Off   |

### User info

| Setting       | Value |
| ------------- | ----- |
| Edit username | Off   |

## Themes

| Setting     | Value         |
| ----------- | ------------- |
| Login theme | `flais-theme` |

## Events

Configuration of event listeners and saved events for the **FINT** realm.

### Event listeners

| Setting         | Value           |
| --------------- | --------------- |
| Event listeners | `jboss-logging` |

### User events

| Setting     | Value     |
| ----------- | --------- |
| Save events | On        |
| Expiration  | `30 days` |

#### Saved event types

The following event types are selected for saving. Names match the labels in the Keycloak Admin Console.

| Event saved type                         |
| ---------------------------------------- |
| Send reset password                      |
| Update consent error                     |
| Grant consent                            |
| Verify profile error                     |
| Remove totp                              |
| Revoke grant                             |
| Update totp                              |
| Login error                              |
| Client login                             |
| Reset password error                     |
| Update credential                        |
| Impersonate error                        |
| Code to token error                      |
| Custom required action                   |
| OAuth2 device code to token error        |
| Restart authentication                   |
| Impersonate                              |
| Update profile error                     |
| Login                                    |
| OAuth2 device verify user code           |
| Update password error                    |
| Client initiated account linking         |
| Identity provider login                  |
| OAuth2 extension grant                   |
| User disabled by permanent lockout       |
| User disabled by permanent lockout error |
| Remove credential error                  |
| Token exchange                           |
| Authreqid to token                       |
| Logout                                   |
| Register                                 |
| Delete account error                     |
| Client register                          |
| Identity provider link account           |
| User disabled by temporary lockout       |
| User disabled by temporary lockout error |
| Delete account                           |
| Update password                          |
| Client delete                            |
| Federated identity link error            |
| Identity provider first login            |
| Client delete error                      |
| Verify email                             |
| Client login error                       |
| Restart authentication error             |
| Execute actions                          |
| Remove federated identity error          |
| Token exchange error                     |
| Permission token                         |
| Federated identity link override         |
| Send identity provider link error        |
| Update credential error                  |
| Execute action token error               |
| OAuth2 extension grant error             |
| Send verify email                        |
| OAuth2 device authentication             |
| Execute actions error                    |
| Remove federated identity                |
| OAuth2 device code to token              |
| Identity provider post login             |
| Identity provider link account error     |
| Federated identity link override error   |
| OAuth2 device verify user code error     |
| Update email                             |
| Register error                           |
| Revoke grant error                       |
| Execute action token                     |
| Logout error                             |
| Update email error                       |
| Client update error                      |
| Authreqid to token error                 |
| Invite user to organization error        |
| Update profile                           |
| Client register error                    |
| Federated identity link                  |
| Invite user to organization              |
| Send identity provider link              |
| Send verify email error                  |
| Identity provider login error            |
| Reset password                           |
| Client initiated account linking error   |
| OAuth2 device authentication error       |
| Remove credential                        |
| Update consent                           |
| Remove totp error                        |
| Verify email error                       |
| Send reset password error                |
| Client update                            |
| Custom required action error             |
| Identity provider post login error       |
| Update totp error                        |
| Code to token                            |
| Verify profile                           |
| Grant consent error                      |
| Identity provider first login error      |
| Invalid signature                        |
| Invalid Signature Error                  |

### Admin events

| Setting                | Value     |
| ---------------------- | --------- |
| Save events            | On        |
| Include representation | On        |
| Expiration             | `30 days` |

## User profile

### Standard attributes

| Attribute   | Display name   | Multivalued | Required | Editable by | Visible to | Validators                                                                                 |
| ----------- | -------------- | ----------- | -------- | ----------- | ---------- | ------------------------------------------------------------------------------------------ |
| `username`  | `${username}`  | Off         | Yes      | Admin       | None       | `length(min=3,max=255)`, `username-prohibited-characters`, `up-username-not-idn-homograph` |
| `email`     | `${email}`     | Off         | Off      | Admin       | None       | `length(max=255)`, `email`                                                                 |
| `firstName` | `${firstName}` | Off         | Off      | Admin       | None       | `length(max=255)`, `person-name-prohibited-characters`                                     |
| `lastName`  | `${lastName}`  | Off         | Off      | Admin       | None       | `length(max=255)`, `person-name-prohibited-characters`                                     |

Common settings for all standard attributes:

- **Attribute group:** None
- **Enabled when:** Always
- **Annotations:** None

### Custom attributes

| Attribute           | Display name        | Multivalued | Required | Editable by | Visible to | Validators |
| ------------------- | ------------------- | ----------- | -------- | ----------- | ---------- | ---------- |
| `externalId`        | External ID         | Off         | Off      | Admin       | None       | None       |
| `roles`             | Roles               | On          | Off      | Admin       | None       | None       |
| `rawRoles`          | Raw Roles           | On          | Off      | Admin       | None       | None       |
| `userPrincipalName` | User Principal Name | Off         | Off      | Admin       | None       | None       |
| `employeeId`        | Employee ID         | Off         | Off      | Admin       | None       | None       |
| `studentNumber`     | Student Number      | Off         | Off      | Admin       | None       | None       |

Common settings for all custom attributes:

- **Default value:** None
- **Attribute group:** None
- **Enabled when:** Always
- **Annotations:** None
