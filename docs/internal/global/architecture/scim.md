# SCIM implementations

[Back: Shared architecture](README.md)

SCIM is a provisioning standard. see the [SCIM concept](../concepts/scim.md)
for its purpose and terminology.

SCIM support in our setup is provided by a custom provider, `flais-scim-server`,
developed for our provisioning requirements. There is a built-in Keycloak SCIM feature, but very limited.

The SCIM implementation is global, meaning all realms can use it.
Some realms may not use SCIM at all.
Each realm's documentation describes its implementation and configuration.

The provisioning source, supported attributes, update rules, and organization
mapping depend on the integration. These are not universal Keycloak settings.
