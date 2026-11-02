# FINT System Constraints

[Back: FINT Constraints](README.md)

These rules apply to FINT in addition to the [global constraints](../../../global/constraints.md).

## Users and identity providers

- A user is a member of exactly one organization.
- A user can only have one domain.
- A user may link multiple IDPs, but all must belong to the user's organization.

## Application roles

- Application permissions are stored as user attributes, not native Keycloak role assignments.
- The internal `scim-managed` realm role is a separate SCIM integration requirement.

## Client access

- Clients can restrict access to selected organizations.
- Organizations that a client cannot access must not appear in its login selection.
- Login must be denied if the organization is not allowed for the client.
- The IDP used for login must belong to an organization permitted for the client.

## SCIM

- SCIM-provisioned users must be associated with an organization.
- SCIM does not create organizations.
- Group provisioning is not implemented.
