# flais-scim-server

The provider is built with [Ping Identity’s SCIM 2.0 SDK ](https://github.com/pingidentity/scim2) and gives us complete ownership and flexibility in how SCIM is implemented and provisioning is handled with Keycloak.

## 🛠️ Development

### Local development

The Dockerfile builds and bundles the provider with Keycloak. After changes, simply redeploy/restart keycloak with task: `gradle restart` as mentioned earlier.

### Test

Root project is responsible for integration tests, while unit tests stays in the module.
