# flais-theme

Login UI is built with [Keycloakify](https://github.com/keycloakify/keycloakify). This allows us to implement the login experience in React while still adhering to Keycloak’s theming model. The theme works in tight coordination with both flais-browser flow and flais-provider.

## 🛠️ Development

### Local development

If you wanna test the provider fully, the Dockerfile builds and bundles the provider with Keycloak. After changes, simply redeploy/restart keycloak with task: `gradle restart` as mentioned earlier.

Project includes [Storybook](https://github.com/storybookjs/storybook) that can be used for building, documenting and testing UI components in isolation (without Keycloak). Simply run `npm run storybook`.

NB: This requires that the page/component is configured in `KcPageStory.tsx`.
