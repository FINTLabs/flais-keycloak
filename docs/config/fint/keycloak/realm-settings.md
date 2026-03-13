# Realm settings

Configuration of the **Realm settings**.

# General

| Setting       | Value   |
| ------------- | ------- |
| Organizations | Enabled |

# Login

## Login screen

| Setting           | Value |
| ----------------- | ----- |
| User registration | Off   |
| Forgot password   | Off   |
| Remember me       | Off   |

## Email settings

| Setting           | Value |
| ----------------- | ----- |
| Email as username | Off   |
| Login with email  | Off   |
| Duplicate emails  | On    |
| Verify email      | Off   |

## User info

| Setting       | Value |
| ------------- | ----- |
| Edit username | Off   |

# Themes

| Setting     | Value         |
| ----------- | ------------- |
| Login theme | `flais-theme` |

# Events

## User events

| Setting     | Value   |
| ----------- | ------- |
| Save events | On      |
| Expiration  | `1 day` |

## Admin events

| Setting                | Value   |
| ---------------------- | ------- |
| Save events            | On      |
| Include representation | Off     |
| Expiration             | `1 day` |

# User profile

## Standard attributes

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

## Custom attributes

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
