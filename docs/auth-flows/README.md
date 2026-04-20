# Authentication Flows

This directory documents all **custom Keycloak Authentication Flows**
configured under **Authentication → Flows** in the Keycloak Admin UI.

## Flows

- [flais-browser](flais-browser.md)
    - Browser login flow with organization and IDP selection
    - SSO cookie handling with organisation context restoration

- [flais-first-broker-login](flais-first-broker-login.md)
    - First-login flow for external identity providers
    - Handles user creation, linking, and organization onboarding

- [flais-post-login-flow](flais-post-login.md)
    - Post-broker-login flow that runs after every external IDP authentication
    - Enforces that the IDP used belongs to an organisation allowed for the client

## Conventions

- "Flow" refers strictly to Keycloak Authentication Flow objects
- Each document mirrors the Keycloak UI structure
- Requirements and ordering matter
