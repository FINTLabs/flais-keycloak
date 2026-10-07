# SCIM server implementation

[Back: Architecture Decision Records](README.md)

## Status

Accepted

## Context

We require a SCIM implementation for provisioning users into Keycloak. Keycloak does not support this natively, but two major plugins are available: one open-source and one closed-source.

Our requirements:

- Supports SCIM server functionality.
- Receives updates and ongoing development.
- Provides access to source code.
- Supports backup and recovery of configuration.

## Alternatives

| Option                                                                                                       | Pros                                                                                                                                                                                                                                                                                                          | Cons                                                                                                                                                                                                                                                                              |
| ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **scim-for-keycloak** ([SCIM for Keycloak](https://scim-for-keycloak.de/))                                   | Paid provider with more developer support for fixes and updates. More features for edge cases.                                                                                                                                                                                                                | Closed-source, although a copy of the source code is provided upon purchase. No way to contribute to development. Requires database modifications to store configuration. Runs as middleware, so a failure can affect Keycloak itself. Has some issues with Azure authentication. |
| **keycloak-scim-server** ([Metatavu/keycloak-scim-server](https://github.com/Metatavu/keycloak-scim-server)) | Open source under the Apache-2.0 license, allowing us to contribute to development. Simple implementation with no database modifications. Runs as a provider rather than middleware, so a failure does not take down Keycloak. Referenced in Keycloak documentation. Better support for Azure authentication. | Maintainers use it for their internal Keycloak setup, so updates follow their Keycloak version and needs. Missing some features. Relatively new provider that may have bugs or unsupported edge cases.                                                                            |
| **flais-scim-server**                                                                                        | Custom provider tailored directly to our needs. Full ownership of the code. No dependency on external parties to implement features or merge pull requests. Updates can be coordinated with the rest of our Keycloak solution.                                                                                | Another module to maintain. No easy access to features developed by others in the future. Requires more resources and time.                                                                                                                                                       |

## Decision

We have chosen flais-scim-server for SCIM after testing the other solutions. This decision is based on:

- Full ownership of the code and implementation.
- A simple implementation with code that is easy to understand.
- Provider failures do not break Keycloak itself.
- The ability to tailor the implementation to our needs instead of relying on generic solutions.
- Compliance with the SCIM 2.0 standard.

## Consequences

### Positive

- Full ownership of code and releases.
- Simple implementation with no modifications to the Keycloak database.
- Runs as a provider rather than middleware, so if it fails, Keycloak itself continues running.
- Best support for Azure authentication, reducing integration risks.
- Ability to create tests tailored to our needs.

### Trade-offs

- Another module to maintain.
- No direct benefit from contributions to public solutions.
- Some advanced and edge-case features found in paid alternatives must be implemented ourselves if needed.
