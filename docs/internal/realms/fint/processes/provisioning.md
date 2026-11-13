# User provisioning

[Back: Processes](README.md)

How SCIM creates and updates users in FINT.

## User creation

Users has to be created through SCIM before their first login.

For organizations using scim, a matching user account must already exist before first login. The login will be rejected if there are no users provisioned before login.

SCIM supplies the attributes, such as `employeeId` and `studentNumber`. The provisioned user
is associated with an organization. SCIM does not create the organization.

## User updates

SCIM updates are treated as authoritative. Attributes supplied by SCIM overwrite
existing values on the user.

## Role updates

If roles are supplied, they replace the existing application role values.
If roles are omitted, existing role values remain unchanged.

The original SCIM role payload is stored in `rawRoles`, and the role `value`
entries are stored in `roles`.
