# Clients

[Back: Concepts](README.md)

## What is a client?

A client represents an application or service that uses Keycloak
to authenticate users and obtain tokens.

In practice, a client is:

- a web application
- a backend service
- or any system that relies on Keycloak for authentication and authorization of
  users

Clients do not represent users or organizations.
They represent who is asking Keycloak to authenticate someone.

## Realm boundary

Clients are defined within a single realm.

- A client belongs to exactly one realm
- Clients cannot be shared across realms
- Client identifiers must be unique within a realm

## Authentication and tokens

Clients initiate authentication requests and have protocol settings such as
redirect URIs. Client scopes and protocol mappers influence the claims included
in issued tokens.
