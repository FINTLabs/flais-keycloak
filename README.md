# flais-keycloak

`flais-keycloak` is the user authentication system for Novari IKS, designed to
orchestrate multiple identity providers into a unified platform.
It extends [Keycloak](https://www.keycloak.org/) with custom functionality,
theming, and demo applications for testing.

## 📂 Project Structure

```
flais-keycloak/
├── keycloak/     # Keycloak
│   |── config/
│   |    ├── authentik          # Authentik configuration files
│   |    ├── nginx              # NGINX configuration files
│   |    └── kc                 # Keycloak configuration for dev/test
│   |── libs/
│   |    ├── flais-provider     # Custom Keycloak SPI provider
│   |    ├── flais-theme        # Flais Keycloak login theme
│   |    └── scim-server        # Scim server for Keycloak
│   |── tools/
│   |    └── flais-scim-client  # Custom scim client for testing
│   └── src/                    # Tests for Keycloak
├── apps/         # Applications for Keycloak
│   └── flais-keycloak-demo/  # Public client to test Keycloak
├── charts/       # Helm charts
│   └── flais-keycloak/       # The Keycloak chart for FLAIS
└── README.md     # Project documentation
```

## 🛠️ Development

Prerequisites:

- [Java 21](https://www.java.com/)
- [Gradle](https://gradle.org/)
- [Docker](https://www.docker.com/)
- [Node.js](https://nodejs.org/)

### Local development keycloak

1. Navigate to keycloak folder

    ```
    cd keycloak
    ```

2. Run task "deployDev" for full setup

    ```bash
    gradle deployDev
    ```

3. Run task "restart" to restart Keycloak for changes
    ```bash
    gradle restart
    ```
