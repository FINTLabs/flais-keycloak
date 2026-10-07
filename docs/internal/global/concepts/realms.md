# Realms

[Back: Concepts](README.md)

## What is a realm?

A realm is an isolated boundary in Keycloak. Users, organizations, identity
providers, clients, and configuration are scoped to a realm.

## Master realm

Keycloak ships with a built-in `master` realm used for administration.
It is separate from the realms used by applications.

## Application realms

A service or project can have its own realm with its own settings and integrations.

Examples:

- `fint`
- `flais`
