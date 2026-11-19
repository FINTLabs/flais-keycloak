# App registration

[Back: Microsoft Entra ID](README.md)

Configuration for **Microsoft Entra App Registration**.

> The App Registration is automatically created when creating an **Enterprise Application** (required for SCIM provisioning).

### Tenant model

Use a **single-tenant** application. Create one App Registration and its corresponding Enterprise Application (service principal) in each Microsoft Entra tenant.

We chose single-tenant because a [multi-tenant application has no built-in allowlist in GA](https://learn.microsoft.com/en-us/entra/identity-platform/howto-convert-app-to-be-multi-tenant). An [allowlist capability is available in preview](https://learn.microsoft.com/en-us/graph/api/resources/allowedtenantsaudience?view=graph-rest-beta). Any Microsoft Entra tenant can create a local service principal for it, typically through user or admin consent or by using the client ID directly, and restricting that would require the application itself to validate the token's `tid` claim.  Single-tenant avoids that trust boundary entirely — [each tenant only ever sees its own registration](https://learn.microsoft.com/en-us/entra/identity-platform/single-and-multi-tenant-apps).


## Authentication

| Setting                 | Value                         |
| ----------------------- | ----------------------------- |
| Supported account types | Single tenant only            |
| Platform                | `Web`                         |
| Redirect URI            | Keycloak redirect URI for IDP |

## Certificates & Secrets

A client secret must be created for Keycloak authentication.

| Setting     | Value / Guidance                                     |
| ----------- | ---------------------------------------------------- |
| Secret type | Client secret                                        |
| Description | Use a clear description (e.g. environment + purpose) |
| Expiration  | Recommended `180 days` (6 months)                    |

> The secret value must be shared over a secure channel.

## Token Configuration

Add an optional claim.

| Token Type | Claim |
| ---------- | ----- |
| ID token   | `upn` |

We add this because the [UPN isn't included in the ID token by default](https://learn.microsoft.com/en-us/entra/identity-platform/optional-claims) — it has to be requested explicitly as an optional claim, which is how Keycloak gets it.

## API Permissions

Add the following permissions under **Microsoft Graph**.

| Permission                      | Type        | Purpose                                                                                                                                                                                                                                           |
| ------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `User.Read`                     | Delegated   | Allows reading the signed-in user's profile through Microsoft Graph. Not required by the current login flow, but retained to support a potential alternative for retrieving user attributes if Microsoft's SCIM provisioning becomes unavailable. |
| `profile`                       | Delegated   | Allows Keycloak to receive standard profile claims, including given name, surname, preferred username, and object ID.                                                                                                                             |
| `Application.ReadWrite.OwnedBy` | Application | Allows our maintenance service to manage applications and service principals it owns without a signed-in user, so we can maintain the application configuration and credentials from our  side.                                                   |

After adding the permissions, grant admin consent for the tenant. We grant
consent centrally for the delegated permissions as well, so users do not need
to consent individually.

### Application.ReadWrite.OwnedBy

Novari is responsible for operating and maintaining the integration with
the county municipality's Microsoft Entra ID tenant. This setup allows us
to perform routine maintenance and resolve configuration issues without
involving the county municipality in every change.

We use the same App Registration for user login and application maintenance.
The application is granted `Application.ReadWrite.OwnedBy`, and its service
principal is explicitly configured as an owner of its App Registration.

This enables us to:

- Update application configuration
- Automate client secret rotation
- Maintain and troubleshoot the integration

The permission does not grant general tenant administrator privileges.
Write access to existing applications and service principals is restricted
to those the application owns. However, it does permit creating applications
and listing applications and service principals throughout the tenant.

This approach follows the principle of least privilege by using
ownership-scoped access instead of tenant-wide write permissions.

Microsoft documents `Application.ReadWrite.OwnedBy` as the least-privileged
application permission for
[adding client secrets](https://learn.microsoft.com/en-us/graph/api/application-addpassword?view=graph-rest-1.0)
and
[removing client secrets](https://learn.microsoft.com/en-us/graph/api/application-removepassword?view=graph-rest-1.0).
Ownership requirements for provisioning operations are described in
[Manage object connections](https://learn.microsoft.com/en-us/entra/identity/app-provisioning/manage-object-connections).

## App Roles

The default User role must be updated.

| Setting | Value  |
| ------- | ------ |
| Role    | `User` |
| Value   | `User` |

This role is required to have a value for SCIM provisioning.

Additional roles required by the application will also be defined here.

## Custom Claims

To add custom claims to a token, see [Entra Claims Mapping Policy](../../../../global/entra/custom-claims.md).

For the default setup, we rely on SCIM to provision users with the correct attributes. A custom claims mapper is only needed when importing custom attributes from the token during login.

To allow custom claims with a **Claims Mapping Policy**:

| Property                 | Value  |
| ------------------------ | ------ |
| `api.acceptMappedClaims` | `true` |

After updating the value, save the manifest.

> [!IMPORTANT]
> Only enable `api.acceptMappedClaims` for the single-tenant applications described above. Do not set this property to `true` on a multi-tenant application. Doing so would let a malicious tenant administrator create a claims-mapping policy for the application, letting it accept tokens with modified claims without needing an application-specific signing key — [this is the recommendation from Microsoft ](https://learn.microsoft.com/en-us/entra/identity-platform/reference-app-manifest#acceptmappedclaims-attribute).
