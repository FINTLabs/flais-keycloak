# App registration

Configuration for **Microsoft Entra App Registration**.

> The App Registration is automatically created when creating an **Enterprise Application** (required for SCIM provisioning).

# Authentication

| Setting                 | Value                         |
| ----------------------- | ----------------------------- |
| Supported account types | Single tenant only            |
| Platform                | `Web`                         |
| Redirect URI            | Keycloak redirect URI for IDP |

# Certificates & Secrets

A client secret must be created for Keycloak authentication.

| Setting     | Value / Guidance                                     |
| ----------- | ---------------------------------------------------- |
| Secret type | Client secret                                        |
| Description | Use a clear description (e.g. environment + purpose) |
| Expiration  | Recommended `180 days` (6 months)                    |

> The secret value must be shared over a secure channel.

# Token Configuration

Add an optional claim.

| Token Type | Claim |
| ---------- | ----- |
| ID token   | `upn` |

This allows Keycloak to receive the User Principal Name (UPN) in the ID token.

# API Permissions

Add the following Microsoft Graph delegated permissions.

| Permission  | Type      | Admin consent |
| ----------- | --------- | ------------- |
| `User.Read` | Delegated | yes           |
| `profile`   | Delegated | yes           |

After adding permissions grant the admin consent for the tenant.

# App Roles

The default User role must be updated.

| Setting | Value  |
| ------- | ------ |
| Role    | `User` |
| Value   | `User` |

This role is required to have a value for SCIM provisioning.

Additional roles required by the application will also be defined here.

# Custom Claims

To add custom claims to token see [Entra Claims Mapping Policy](/docs/entra/custom-claims.md)

To allow custom claims with **Claims Mapping Policy**, update the application manifest.

| Property                 | Value  |
| ------------------------ | ------ |
| `api.acceptMappedClaims` | `true` |

After updating the value, save the manifest.
