# Users

## What is a user?

A user represents a single identity within a realm.

## Restrictions

-   A user belongs to exactly one organization
-   User identifier must be unique within a realm
-   User can only have one domain

## Identity providers

-   A user may have linked multiple IDPs
-   All linked IDPs must belong to the users organization

## Diagram

```mermaid
---
config:
    flowchart:
        defaultRenderer: elk
---
flowchart TD
    subgraph Realm: fint
        subgraph orgs[Organizations]
            org_1[Org 1]
            org_2[Org 2]
        end

        subgraph idps[IDPs]
            idp_1[IDP 1]
            idp_2[IDP 2]
        end

        subgraph users[Users]
            user_1["user@org1.no"]
            user_2["user@org2.no"]
        end
    end

    %% Membership & associations
    user_1 -->|Member of| org_1
    user_2 -->|Member of| org_2

    idp_1 -->|Linked to| org_1
    idp_2 -->|Linked to| org_2

    user_1 -->|Linked to| idp_1
    user_2 -->|Linked to| idp_2
```
