# Clients

## What is a client?

A client represents an application or service that uses Keycloak
to authenticate users and obtain tokens.

In practice, a client is:

-   a web application
-   a backend service
-   or any system that relies on Keycloak for authentication and authorization of users

Clients do not represent users or organizations.
They represent who is asking Keycloak to authenticate someone.

## Client scope

Clients are defined within a single realm.

-   A client belongs to exactly one realm
-   Clients cannot be shared across realms
-   Client identifiers must be unique within a realm

## Relationship to organizations

Clients are explicitly associated with organizations.

This association controls:

-   which organizations a client can be used with
-   which users are allowed to log in through the client

During login:

1. A user selects an organization
2. Keycloak verifies that the client is allowed to access that organization
3. Login continues only if the client–organization relationship is valid

If a client is not allowed for the selected organization, access is denied.

## Authentication responsibility

Clients:

-   initiate authentication requests
-   define redirect URIs and protocol settings
-   receive tokens after successful login

Clients do not:

-   select identity providers
-   manage users
-   assign roles

## Token contents

Clients influence which claims appear in issued tokens via:

-   client scopes
-   mappers
