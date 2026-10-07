# Keycloak Architecture Overview

[Back: Documentation](../README.md)

This documentation describes how Keycloak is used to support
multi-tenancy, organizations, external identity providers (IDPs),
and role management.

## Core concepts

-   **Realm** – an isolated Keycloak setup for a single service or project
-   **Organization** – a tenant within a realm
-   **User** – an identity that exists in one realm
-   **Member** – a user belonging to an organization
-   **Identity Provider (IDP)** – an external system used for authentication
-   **Client** – client represents an application or service
-   **Roles** – permissions stored as user attributes (not native Keycloak roles)

Everything else builds on top of these concepts.

## Where to start

If you are new to this setup, start with these pages:

-   [Realms](concepts/realms.md)
-   [Organizations](concepts/organizations.md)
-   [Users](concepts/users.md)
-   [Roles](concepts/roles.md)
-   [Identity Providers](concepts/identity-providers.md)
-   [SCIM](concepts/scim.md)
-   [Clients](concepts/clients.md)

## Document structure

-   [Core concepts](concepts/README.md) – what the main entities are and how they relate
-   [Configuration](config/README.md) – documents how *realms* (like FINT) are configured in the different platforms that are used.
-   [Authentication flows](auth-flows/README.md) – custom Keycloak authentication flows (as seen in the Admin UI)
-   [Processes](processes/README.md) – high-level system behavior (login, provisioning, role lifecycle)
-   [Constraints](constraints/README.md) – rules and invariants that apply to realms and globally
-   [Microsoft Entra ID](entra/README.md) – Entra-specific configuration
