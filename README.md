# flais-keycloak

`flais-keycloak` is the **user authentication system for Novari IKS**, designed to orchestrate multiple identity providers into a unified platform.  
It extends [Keycloak](https://www.keycloak.org/) with custom functionality, theming, and demo applications for testing.

## 📂 Project Structure

```
flais-keycloak/
├── keycloak/     # Application
│   ├── flais-demo       # Demo application to test authentication
│   └── libs/
│       ├── flais-provider   # Custom Keycloak SPI provider
│       └── flais-theme      # Flais Keycloak login theme
│
└── README.md     # Project documentation
```

## 🛠️ Development

### Prerequisites

- [Java 21](https://www.java.com/)
- [Gradle](https://gradle.org/)
- [Docker](https://www.docker.com/)
- [Node.js](https://nodejs.org/)

### Running Locally

1. Run the docker compose (keycloak) for initial setup

   ```bash
   cd keycloak
   docker-compose up
   ```

2. Run deployDev task
   ```bash
   gradle deployDev
   ```
