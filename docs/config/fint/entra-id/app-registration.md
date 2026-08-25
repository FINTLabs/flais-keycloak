# App registration

Configuration for **Microsoft Entra App Registration**.

> The App Registration is automatically created when creating an **Enterprise Application** (required for SCIM provisioning).

## Tenant model

Use a **single-tenant** application. Create one App Registration and its corresponding Enterprise Application (service principal) in each Microsoft Entra tenant.

We chose single-tenant because a [multi-tenant application has no built-in allowlist](https://learn.microsoft.com/en-us/entra/identity-platform/howto-convert-app-to-be-multi-tenant). Any Microsoft Entra tenant can create a local service principal for it, typically through user or admin consent or by using the client ID directly, and restricting that would require the application itself to validate the token's `tid` claim. Single-tenant avoids that trust boundary entirely — [each tenant only ever sees its own registration](https://learn.microsoft.com/en-us/entra/identity-platform/single-and-multi-tenant-apps).


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

We add this because the [UPN isn't included in the ID token by default](https://learn.microsoft.com/en-us/entra/identity-platform/optional-claims) — it has to be requested explicitly as an optional claim, which is how Keycloak gets it.

# API Permissions

Add the following Microsoft Graph delegated permissions.

| Permission  | Type      | Admin consent |
| ----------- | --------- | ------------- |
| `User.Read` | Delegated | yes           |
| `profile`   | Delegated | yes           |

After adding the permissions, grant admin consent for the tenant.

# App Roles

The default User role must be updated.

| Setting | Value  |
| ------- | ------ |
| Role    | `User` |
| Value   | `User` |

This role is required to have a value for SCIM provisioning.

Additional roles required by the application will also be defined here.

# Custom Claims

To add custom claims to a token, see [Entra Claims Mapping Policy](/docs/entra/custom-claims.md).

For the default setup, we rely on SCIM to provision users with the correct attributes. A custom claims mapper is only needed when importing custom attributes from the token during login.

To allow custom claims with a **Claims Mapping Policy**:

| Property                 | Value  |
| ------------------------ | ------ |
| `api.acceptMappedClaims` | `true` |

After updating the value, save the manifest.

> [!IMPORTANT]
> Only enable `api.acceptMappedClaims` for the single-tenant applications described above. Do not set this property to `true` on a multi-tenant application. Doing so would let a malicious tenant administrator create a claims-mapping policy for the application, letting it accept tokens with modified claims without needing an application-specific signing key — [this is the recommendation from Microsoft ](https://learn.microsoft.com/en-us/entra/identity-platform/reference-app-manifest#acceptmappedclaims-attribute).
