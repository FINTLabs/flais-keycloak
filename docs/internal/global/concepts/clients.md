# Clients

[Back: Concepts](README.md)

## What is a client?

A client represents an application or service integrated with Keycloak. It may
request login for a user or obtain access on its own behalf. A client is neither
a user nor an organization.

Clients belong to one [realm](realms.md). Their client IDs must be unique within
that realm; an application using several realms needs a registration in each.
Keycloak supports OpenID Connect (OIDC) and SAML clients.

## Public and confidential clients

For OIDC, a public client, such as browser JavaScript, cannot keep a client secret.
A confidential client authenticates itself, for example with a secret or signed
assertion. Confidential clients with service accounts enabled can use the client
credentials grant for service-to-service access without an interactive user.

For browser login, the authorization code flow returns a code that is exchanged
for tokens. PKCE binds that exchange to the initiating request. Redirect URIs
control where login responses may go; web origins control allowed browser origins
for cross-origin requests. They serve different purposes.

## Tokens, scopes, and mappers

| OIDC token    | Purpose                                                       |
| ------------- | ------------------------------------------------------------- |
| ID token      | Tells the client about the authenticated user and login       |
| Access token  | Presented to an API to request access                         |
| Refresh token | Used to obtain new tokens while the session and policy permit |

An API must validate the access token and enforce its own access rules. An ID
token is not an API access token.

Client scopes bundle protocol mappers and role scope mappings for reuse. Default
client scopes apply automatically; optional scopes apply when requested. Protocol
mappers determine which attributes and roles become claims and where they appear.
Assigning a role and including it in a token are separate decisions.
