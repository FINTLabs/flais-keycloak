# flais-keycloak

`flais-keycloak` is the user authentication system for Novari IKS, designed to
orchestrate multiple identity providers into a unified platform.
It extends [Keycloak](https://www.keycloak.org/) with custom functionality,
theming, and demo applications for testing.

## 📚 Documentation

See the [documentation](docs/README.md) for the full documentation.

## 📂 Project Structure

```
flais-keycloak/
├── keycloak/                         # Keycloak
│   ├── config/
│   │   ├── mock-idp/                 # Mock IDP configuration files
│   │   ├── kc/                       # Keycloak configuration for dev/test
│   │   ├── nginx/                    # NGINX configuration files
│   │   └── scimverify/               # Config for scimverify (compliance tests)
│   ├── libs/
│   │   ├── flais-provider/           # Flais Keycloak provider
│   │   ├── flais-theme/              # Flais Keycloak login theme
│   │   └── flais-scim-server/        # Flais SCIM server implementation
│   ├── tools/
│   │   ├── flais-scim-auth/          # Custom SCIM auth application for flais-scim-server
│   │   └── scimverify/               # Dockerfile for scimverify
│   └── src/
│       └── test/                     # Tests for Keycloak
├── apps/
│   └── flais-keycloak-demo/          # Public client to test Keycloak
├── charts/
│   └── flais-keycloak/               # The Keycloak chart for FLAIS
├── docs/                             # Internal and external documentation
└── README.md                         # Project documentation
```

## 🛠️ Development

Prerequisites:

-   [Java 21](https://www.java.com/)
-   [Gradle](https://gradle.org/)
-   [Docker](https://www.docker.com/)
-   [Node.js](https://nodejs.org/)

### Local development keycloak

1. Navigate to keycloak folder

    ```
    cd keycloak
    ```

2. Run task "runDev" for full setup

    ```bash
    gradle runDev
    ```

3. Run task "restartDev" to restart Keycloak for changes
    ```bash
    gradle restartDev
    ```

4. Run task "stopDev" to stop Keycloak dev environment
   ```bash
   gradle stopDev
   ```

5. Run task "cleanupDev" to stop and cleanup Keycloak dev environment
   ```bash
   gradle cleanupDev
   ```
