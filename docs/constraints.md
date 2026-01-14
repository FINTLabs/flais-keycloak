# System Constraints

This document defines rules that apply globally across the system.

## Realm

-   Realms are isolated from each other
-   Users, organizations, and IDPs do not cross realm boundaries

## Organization

-   An organization belongs to exactly one realm
-   An organization may have multiple linked IDPs

## User

-   A user exists in exactly one realm
-   A user is a member of exactly one organization
-   A user may link multiple IDPs, but only within their organization

## Role

-   Roles are stored as user attributes
-   Native Keycloak roles are not used in applications

## Client access

-   Clients can filter access to organizations
-   Organizations without access will not show up on login
