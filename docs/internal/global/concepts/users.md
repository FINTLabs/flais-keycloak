# Users

[Back: Concepts](README.md)

## What is a user?

A user represents an identity within a [realm](realms.md). A user has an internal
identifier and may have a username, email address, profile attributes,
credentials, role mappings, and organization memberships.

An email address or display name is not a permanent identity key. Applications
using OIDC identify a subject in the context of its issuer, using `iss` and `sub`.
The same person can have different identities in different realms.

## Where users come from

Depending on configuration, users may be created by administrators,
self-registration, first broker login, or [SCIM provisioning](scim.md).
User federation can also make identities from an external directory available.

A user authenticated through an external IDP does not necessarily have a local
Keycloak password. Linked external identities allow Keycloak to resolve that
realm user on later logins.

## Attributes, permissions, and sessions

Attributes store information about a user. [Roles](roles.md) express permissions.
Organization membership expresses a tenant relationship.

Authentication creates a session under the realm's policies. Updating stored
attributes does not rewrite tokens already issued to applications.
