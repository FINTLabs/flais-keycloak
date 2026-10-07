# Organizations

[Back: Concepts](README.md)

## What is an organization?

An organization represents a tenant within a realm. Each realm has its own set of organizations.

## Responsibilities

-   Group users
-   Hold organization-specific attributes
-   Define which IDPs are available
-   Linked domains

## Relationships

-   Realm → many organizations
-   Organization → many members
-   Organization → many IDPs
-   Organization → many domains
-   Organization → many attributes

The choice to use organizations for multi-tenancy, and restrictions on user
membership or client access, belong to the realm's design.
