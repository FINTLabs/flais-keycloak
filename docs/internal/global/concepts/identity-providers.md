# Identity providers (IDPs)

[Back: Concepts](README.md)

## What is an IDP?

An identity provider authenticates users. Keycloak can delegate authentication
to an external provider, such as Microsoft Entra ID, through identity brokering.
Providers are configured within a [realm](realms.md), typically using OIDC or SAML.

## Brokered login

1. An application starts login with Keycloak.
2. Keycloak redirects the user to the selected external IDP.
3. The IDP authenticates the user and returns a response to Keycloak.
4. Keycloak resolves the realm user, completes its authentication flow, and
   issues its own tokens or SAML response to the application.

The external identity and the Keycloak user are connected by an identity link.
First broker login controls how a new external identity is associated with an
existing account or used to create an account. Matching email addresses alone
should not be treated as proof that two accounts have the same owner.

## Mappers and related concepts

IDP mappers import information from an external login response into Keycloak.
Client protocol mappers control information sent from Keycloak to applications.

[SCIM](scim.md) provisions identities through an API. This is not the same as redirecting a user to an external login page.

Provider selection, organization links, and account-linking rules are realm
choices.
