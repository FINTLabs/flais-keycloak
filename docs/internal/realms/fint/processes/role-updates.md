# Role updates and token issuance

[Back: Processes](README.md)

FINT receives application roles through SCIM and login token claims.

## SCIM provisioning

SCIM stores the original role payload in `rawRoles` and its role values in `roles`.
Supplied role values replace existing values. Omitted roles leave them unchanged.

## Login and token issuance

During login, the incoming `roles` claim is mapped to the user's `roles` attribute.
Outgoing token mappers read that attribute and add role claims to application tokens.

The diagram shows how both sources feed the role attributes used for token issuance.

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
