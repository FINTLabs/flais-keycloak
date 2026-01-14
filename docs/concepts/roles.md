# Roles

## Design choice

Roles are implemented as user attributes instead of native Keycloak roles and gives:

-   Integration with external role sources
-   Flexible role formats
-   Consistent internal representation
-   Flexibility with SCIM implementation

## User attributes

-   `rawRoles` – original role payload (JSON string)
-   `roles` – normalized role values

## Role sources

-   SCIM provisioning
-   Login token claims

## Diagram

```mermaid
---
config:
    flowchart:
        defaultRenderer: elk
---
flowchart TD
  subgraph Sources["Role sources"]
    scim["SCIM provisioning"]
    login_claim["Login"]
  end

  subgraph User["KC User"]
    raw_roles["rawRoles"]
    roles_Attr["roles"]
  end

  subgraph LoginFlow["Login + token issuance"]
    mapper_in["Mapper: claim.roles → user.roles"]
    mapper_out["Mapper: user.roles → claim.roles"]
    access_token["Access Token"]
  end

  scim -->|JSON String| raw_roles
  scim -->|value| roles_Attr

  login_claim -->|incoming roles| mapper_in
  mapper_in -->|writes| roles_Attr

  roles_Attr -->|reads| mapper_out
  mapper_out -->|adds claim| access_token
```
