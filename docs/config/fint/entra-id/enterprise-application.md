# Enterprise application

Configuration for **Microsoft Entra Enterprise Application**, with support for SCIM provisioning.

# Create Enterprise Application

1. Navigate to Microsoft Entra ID
2. Go to Enterprise applications
3. Create a new application:

| Setting          | Value                                                                       |
| ---------------- | --------------------------------------------------------------------------- |
| Application type | Create your own application                                                 |
| Integration type | Integrate any other application you don't find in the gallery (Non-gallery) |

## Properties

| Setting                       | Value |
| ----------------------------- | ----- |
| Enabled for users to sign-in? | Yes   |
| Assignment required           | Yes   |
| Visible to users              | No    |

# Users and groups

Users and groups must be assigned to any application role.

This assignment controls:

- Which users are allowed to authenticate
- Which users are provisioned via SCIM
- What roles the user have

# Provisioning

## Setup

| Setting               | Value                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------- |
| Authentication method | Bearer authentication                                                                 |
| Tenant URL            | `https://keycloak.prod.infra.flais.no/realms/fint/scim/v2/<org-id>/?aadOptscim062020` |
| Secret token          | Not specified                                                                         |

## Settings

### Mappings

| Setting                             | Value |
| ----------------------------------- | ----- |
| Provision Microsoft Entra ID Users  | Yes   |
| Provision Microsoft Entra ID Groups | No    |

### Configuration

| Setting                       | Value                               |
| ----------------------------- | ----------------------------------- |
| Prevent accidental deletion   | Yes                                 |
| Accidental deletion threshold | `500`                               |
| Scope                         | Sync only assigned users and groups |
| Provisioning status           | On                                  |

## Attribute List

| Attribute                                                                | Type    | PK  | Required | Multi-value |
| ------------------------------------------------------------------------ | ------- | --- | -------- | ----------- |
| `id`                                                                     | String  | Yes | Yes      |             |
| `active`                                                                 | Boolean |     |          |             |
| `emails[type eq "work"].value`                                           | String  |     |          |             |
| `userName`                                                               | String  |     | Yes      |             |
| `externalId`                                                             | String  |     | Yes      |             |
| `roles`                                                                  | String  |     |          | Yes         |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.givenName`              | String  |     |          |             |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.familyName`             | String  |     |          |             |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:userPrincipalName` | String  |     |          |             |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId`        | String  |     |          |             |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:studentNumber`     | String  |     |          |             |

## Attribute Mapping

> `extensionAttributeXX` values may vary between Entra tenants.

| Target Attribute                                                         | Source Attribute                                              |
| ------------------------------------------------------------------------ | ------------------------------------------------------------- |
| `userName`                                                               | `objectId`                                                    |
| `active`                                                                 | `Switch([IsSoftDeleted], , "False", "True", "True", "False")` |
| `emails[type eq "work"].value`                                           | `mail`                                                        |
| `externalId`                                                             | `objectId`                                                    |
| `roles`                                                                  | `AssertiveAppRoleAssignmentsComplex([appRoleAssignments])`    |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.givenName`              | `givenName`                                                   |
| `urn:ietf:params:scim:schemas:core:2.0:User:name.familyName`             | `surname`                                                     |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:userPrincipalName` | `userPrincipalName`                                           |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId`        | `extensionAttribute10`                                        |
| `urn:ietf:params:scim:schemas:extension:fint:2.0:User:studentNumber`     | `extensionAttribute9`                                         |

## Known issues

### Entra ID

- Does not support the `remove` operation in `PATCH` when clearing an attribute value. The field is silently skipped.
    - Source: https://learn.microsoft.com/en-us/answers/questions/223936/sending-an-empty-value-with-user-provisioning-%28sci?page=1&orderby=Helpful&comment=answer-224218&translated=false
- The feature flag `aadOptscim062020` does not follow SCIM RFC 7644.
    - This is handled on our side but requires full definitions for core complex attributes (e.g., `name.givenName`) in the Attribute mapping. Example: `urn:ietf:params:scim:schemas:core:2.0:User:name.givenName`
    - RFC: https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2
    - Source: https://learn.microsoft.com/en-us/entra/identity/app-provisioning/application-provisioning-config-problem-scim-compatibility
- POST/PATCH payload is different for roles, so you end up having to wait 2 cycles for the roles to be provisioned correctly.
    - Solved by using the feature flag `aadOptscim062020`
    - Source: https://learn.microsoft.com/nb-no/entra/identity/app-provisioning/customize-application-attributes#provisioning-a-role-to-a-scim-app
- The feature flag `aadOptscim062020` does not work with on-demand provisioning.
    - Source: https://learn.microsoft.com/en-us/entra/identity/app-provisioning/application-provisioning-config-problem-scim-compatibility
- Microsoft specifies the feature flag will become default behavior over the next few months. Time of publish is unknown.
    - Source: https://learn.microsoft.com/en-us/entra/identity/app-provisioning/application-provisioning-config-problem-scim-compatibility
- Users are not deleted when unassigned to application, but instead disabled using `PATCH` request on `active`.
    - This is not an issue in itself, but will keep "noise" in Keycloak.
    - Source: Testing
- When a user is deleted from the source system, the delete request is sent after 30 days unless manually hard-deleted.
    - The deletion process in Microsoft Entra ID is considered a soft delete, where the user is disabled or marked as deleted in Entra ID first. Users are hard-deleted 30 days after they're soft-deleted.
    - Source: https://learn.microsoft.com/en-us/answers/questions/2157177/azure-scim-deleted-user-from-microsoft-entra-id-do
