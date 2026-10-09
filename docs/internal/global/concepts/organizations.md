# Organizations

[Back: Concepts](README.md)

## What is an organization?

An organization represents a tenant within a [realm](realms.md), for example a
customer institution. It groups members and holds tenant information while using
the realm's clients, authentication infrastructure, and issuer.

## Relationships

| Related resource   | Relationship                                 |
| ------------------ | -------------------------------------------- |
| Realm              | Owns the organization                        |
| Users              | Become members of the organization           |
| Identity providers | Can be linked for organization-aware login   |
| Domains            | Associate domain names with the organization |
| Attributes         | Hold tenant-specific metadata                |

A domain can help resolve an organization during login. It is not, by itself,
proof of membership or permission to access an application.

## Membership and access

Keycloak distinguishes managed and unmanaged members. Managed membership ties
the user's lifecycle to the organization; unmanaged membership associates an
existing realm user without the same lifecycle dependency. Removing managed
members can delete the underlying user, so the membership type matters.

Membership, application permissions, and organization information in tokens are
separate concerns. Clients must request the relevant scope and use appropriate
mappers to receive organization claims.
