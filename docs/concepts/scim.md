# SCIM

## What is SCIM?

SCIM (System for Cross-domain Identity Management) is used as the
authoritative mechanism for user provisioning
into Keycloak from external identity or user management systems.

In this architecture, SCIM is responsible for:

- Creating users
- Updating users
- Deleting users
- Supplying user attributes, such as roles

Authentication is not handled by SCIM.

## Role of SCIM in the system

SCIM operates alongside authentication flows and identity providers:

- **SCIM** → lifecycle management (users, roles)
- **IDPs** → authentication
- **Keycloak** → identity resolution/management and token issuance

SCIM does not participate in interactive login, but to provision users before they login.

## User provisioning model

### User creation

- Users may be created in advance via SCIM
- Users may also be created implicitly during first broker login
- Both paths result in the same internal user model

### User updates

- SCIM updates are treated as authoritative
- Attributes supplied by SCIM overwrite existing values

## Roles

If roles are provided:

- They replace any existing role assignments

If roles are not provided:

- Existing roles remain unchanged

## Role representation

Roles provided via SCIM are stored in two forms on the Keycloak User:

| Attribute  | Purpose                                                     |
| ---------- | ----------------------------------------------------------- |
| `rawRoles` | Original SCIM role payload, stored as JSON for traceability |
| `roles`    | List of roles `value`                                       |

- Examples:
    - rawRoles: `[{"value":"Read","display":"Read","type":"WindowsAzureActiveDirectoryRole","primary":false}]`
    - roles: `["Read"]`

## Relationship to organizations

- SCIM-provisioned users are associated with an organization
- SCIM does not create organizations
