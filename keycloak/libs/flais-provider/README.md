# flais-provider

This module provides the core login needed for organization-based login. It includes three authenticators:

-   OrgSelector - Determines which organization the login is associated with.
-   OrgIdpSelector - Choose the appropriate Identity Provider for the selected organization.
-   OrgIdpRedirector - Performs the final redirect to the external IDP.

These authenticators allow us to implement flows Keycloak does not natively support.

## 🛠️ Development

### Local development

The Dockerfile builds and bundles the provider with Keycloak. After changes, simply redeploy/restart keycloak with task: `gradle restart` as mentioned earlier.

### Test

Root project is responsible for integration tests, while unit tests stays in the module.
