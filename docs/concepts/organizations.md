# Organizations

## What is an organization?

An organization represents a tenant within a realm. Each realm has its own set of organizations.

## Responsibilities

-   Group users
-   Hold tenant-specific attributes
-   Define which IDPs are available
-   Linked domains

## Multi-tenancy

Multi-tenancy is implemented using organizations inside a realm,
not by creating multiple realms.

## Relationships

-   Realm → many organizations
-   Organization → many members
-   Organization → many IDPs
-   Organization → many domains
-   Organization → many attributes

## Diagram

```mermaid
---
config:
    flowchart:
        defaultRenderer: elk
---
flowchart TD
    subgraph Realm[Realm]

        users_box[Users]
        idps_box[Identity Providers]

        subgraph org_1[Organization 1]
            org1_members[Members]
            org1_attrs[Attributes]
            org1_idps[Linked IDPs]
        end

        subgraph org_2[Organization 2]
            org2_members[Members]
            org2_attrs[Attributes]
            org2_idps[Linked IDPs]
        end
    end

    users_box -->|membership| org1_members
    users_box -->|membership| org2_members

    idps_box -->|linked to| org1_idps
    idps_box -->|linked to| org2_idps
```
