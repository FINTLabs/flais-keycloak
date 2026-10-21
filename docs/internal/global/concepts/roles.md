# Roles

[Back: Concepts](README.md)

## What is a role?

In Keycloak terminology, roles refer to Keycloak's built-in role support:
realm roles and client roles. The assignment, inheritance, and token mapping
described below refer to these native Keycloak roles.

A role names a permission or responsibility, such as `reader` or `administrator`.
Applications decide what operations those names allow.

| Role type   | Scope                  | Example                  |
| ----------- | ---------------------- | ------------------------ |
| Realm role  | Shared within a realm  | `support`                |
| Client role | Defined for one client | `reports-api` → `reader` |

The same client role name can exist on different clients without representing
the same permission.

Realms can also represent application roles in other ways, such as user
attributes. These alternative representations are not native Keycloak roles
and do not automatically participate in Keycloak's role assignment or inheritance.

## Assignment and inheritance

Users can receive native Keycloak roles directly or through group membership.
Composite roles include other roles, allowing a broader responsibility to grant
several smaller permissions. Groups organize users; roles describe access.

## Roles in tokens

Effective role assignments, role scope mappings, and protocol mappers determine
which native Keycloak roles reach a client. Standard OIDC mappings commonly use
`realm_access.roles` for realm roles and `resource_access.<client-id>.roles` for
client roles. Custom mappers can produce a different token structure.

A token claim named `roles` does not necessarily contain native Keycloak roles.
Its meaning and source depend on the realm's configuration and protocol mappers.

Receiving a role claim does not enforce access automatically: the application
or API must check the relevant permission.
