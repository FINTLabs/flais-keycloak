# Application roles

[Back: FINT architecture](README.md)

## Design choice

FINT application roles are implemented as user attributes instead of native Keycloak roles. This provides:

- Integration with external role sources
- Flexible role formats
- Consistent internal representation
- Flexibility with SCIM implementation

The internal `scim-managed` realm role is used by the SCIM integration.

## Representation

| User attribute | Purpose                                                                               |
| -------------- | ------------------------------------------------------------------------------------- |
| `rawRoles`     | Original SCIM role payload, stored as JSON for traceability and the Entra SCIM schema |
| `roles`        | Normalized role values used for application permissions                               |

For example, `rawRoles` may contain
`[{"value":"Read","display":"Read","type":"WindowsAzureActiveDirectoryRole","primary":false}]`,
while `roles` contains `["Read"]`.

The `scim-managed` realm role is separate from these application permissions.

## Sources

Application roles are supplied through SCIM provisioning and login token claims.
