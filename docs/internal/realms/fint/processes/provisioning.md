# User provisioning

[Back: Processes](README.md)

How SCIM creates and updates users in FINT.

## User creation

Users can be created through SCIM before their first login. A user may also be
created during first broker login and updated later by SCIM. The two processes
are independent.

SCIM supplies the `employeeId` and `studentNumber` attributes. The provisioned user
is associated with an organization. SCIM does not create the organization.

## User updates

SCIM updates are treated as authoritative. Attributes supplied by SCIM overwrite
existing values on the user.

## Role updates

If roles are supplied, they replace the existing application role values.
If roles are omitted, existing role values remain unchanged.

The original SCIM role payload is stored in `rawRoles`, and the role `value`
entries are stored in `roles`.
