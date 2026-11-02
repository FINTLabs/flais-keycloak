# SCIM

[Back: Concepts](README.md)

## What is SCIM?

SCIM (System for Cross-domain Identity Management) is a standard for managing
identities across systems. It defines common resource schemas and a protocol
for creating, finding, updating, and deleting identity resources.

SCIM 2.0 includes User and Group schemas and an HTTP-based protocol using JSON.

## Provisioning client and service provider

The provisioning client sends identity changes to a SCIM service provider.
The service provider receives these requests and manages the corresponding
resources in the target system.

These terms describe provisioning responsibilities, not which system
authenticates users.

## Identity lifecycle

Provisioning keeps identities aligned as people join, change responsibilities,
or leave an organization. It can:

- Create accounts before their first login
- Synchronize profile attributes
- Maintain group memberships
- Deactivate accounts or delete resources when access ends

Deactivation and deletion are different operations. Setting a user's `active`
attribute to `false` changes their administrative status; deleting the resource
removes it.

## Resource identifiers

The service provider assigns each resource an `id`. The provisioning client
can supply an `externalId` to associate that resource with an identity in
its own system.

These identifiers help systems maintain the relationship between corresponding
identities over time.

## Provisioning and authentication

Provisioning manages which identities exist and their associated information.
Authentication verifies an identity during login.

SCIM does not perform interactive login or issue application login tokens.
[Identity providers](identity-providers.md) handle authentication.
