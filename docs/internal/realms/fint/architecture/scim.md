# SCIM integration

[Back: FINT architecture](README.md)

## Provider

FINT uses the custom `flais-scim-server` provider for SCIM support. This is a custom provider used by our deployment, not a built-in Keycloak feature.

## Provisioning and authentication

FINT uses SCIM as the authoritative mechanism for provisioning users and their
attributes from external identity or user management systems.

Provisioning and interactive login are separate processes. For configurations
using SCIM, users must be provisioned to Keycloak before their first login.
Users are not created during broker login. Login cannot complete if the
user has not been provisioned.

External IDPs handle authentication. The SCIM integration handles provisioning.

When Microsoft Entra ID is used for provisioning, only direct membership in
an assigned group is supported. Users who are members through a nested group
are not provisioned through that assignment.

SCIM-provisioned users are associated with an organization.
