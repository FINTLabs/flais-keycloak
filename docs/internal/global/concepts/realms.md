# Realms

[Back: Concepts](README.md)

## What is a realm?

A realm is an isolated identity and configuration boundary in Keycloak. It owns
its users, clients, roles, organizations, identity providers, and login settings.
The same person can have separate user accounts in different realms.

## Realm settings

A realm defines shared policies such as authentication flows, required actions,
session and token lifetimes, and signing keys. Clients can have additional
settings and supported overrides.

For OpenID Connect, each realm has its own issuer and discovery document at
`/realms/{realm-name}/.well-known/openid-configuration`, relative to the Keycloak
base URL. Applications use this metadata to locate endpoints and signing keys.
A token issued by one realm is not automatically trusted by another realm's
applications.

## Master and application realms

The built-in `master` realm is used for administration. Application identities
belong in application realms, such as `fint` or `flais`.

An [organization](organizations.md) represents a tenant inside a realm; it does
not create a separate issuer or a separate set of realm policies. Choosing
between multiple realms and organizations within one realm is an architecture
decision.
