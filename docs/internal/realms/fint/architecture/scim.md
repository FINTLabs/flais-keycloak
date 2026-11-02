# SCIM integration

[Back: FINT architecture](README.md)

## Provider

FINT uses the custom `flais-scim-server` provider for SCIM support. This is a custom provider used by our deployment, not a built-in Keycloak feature.

## Provisioning and authentication

FINT uses SCIM as the authoritative mechanism for provisioning users and their
attributes from external identity or user management systems.

Provisioning and interactive login are independent. A user may be provisioned
before login or created during first broker login, then updated through SCIM.
External IDPs handle authentication. The SCIM integration handles provisioning.

SCIM-provisioned users are associated with an organization.
