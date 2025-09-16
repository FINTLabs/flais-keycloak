# local-dev

Scripts, Docker configs, and setup for running Keycloak locally with the custom provider and theme.

## Running Locally

1. Run the docker compose (apps/local-dev) for initial setup

   ```bash
   cd apps/local-dev
   docker-compose up
   ```

2. Run deployDev task
   ```bash
   gradle deployDev
   ```
