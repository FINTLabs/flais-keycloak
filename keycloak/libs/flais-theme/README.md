# flais-theme

Login UI is built with [Keycloakify](https://github.com/keycloakify/keycloakify). This allows us to implement the login experience in React while still adhering to Keycloak’s theming model. The theme works in tight coordination with both flais-browser flow and flais-provider.

## 🛠️ Development

### Local development

If you wanna test the provider fully, the Dockerfile builds and bundles the provider with Keycloak. After changes, simply redeploy/restart keycloak with task: `gradle restart` as mentioned earlier.

Project includes [Storybook](https://github.com/storybookjs/storybook) that can be used for building, documenting and testing UI components in isolation (without Keycloak). Simply run `npm run storybook`.

NB: This requires that the page/component is configured in `KcPageStory.tsx`.

### AI-assisted development with Storybook MCP

Use Node.js 26. Start Storybook from the theme directory in the worktree you are editing:

```sh
cd keycloak/libs/flais-theme
npm ci
npm run storybook
```

Storybook runs at `http://localhost:6006`. The MCP addon exposes an HTTP endpoint at
`http://localhost:6006/mcp`. Configure your AI client's MCP server named
`keycloak-theme` to use that URL. Client configuration is local and is not included
in this repository.

The addon and `componentsManifest` feature are enabled in `.storybook/main.ts`.
The server provides component discovery and documentation, story-writing
instructions, change tracking, and story preview URLs. Follow `AGENTS.md` when
working on the theme. A typical workflow is to discover and read component docs,
fetch story-writing instructions, make changes, then review affected stories and
share their preview links.

The tools currently exposed by this setup do not capture screenshots or provide
`test-run`. Use the browser to inspect rendering and Storybook interactions, and
run `npm run check` for lint and TypeScript validation. Generating a preview link
does not execute or verify a story's interaction tests.

#### Troubleshooting

- Start Storybook from the intended worktree. If port 6006 is already occupied,
  check which checkout owns that process and stop it before starting this one.
  On macOS, `lsof -nP -iTCP:6006 -sTCP:LISTEN` shows the listening process;
  `lsof -a -p <PID> -d cwd` shows its working directory.
- If using another port, update the MCP client's endpoint to match it.
- A missing MCP endpoint can mean dependencies are stale or the server was
  started before the addon was enabled. Run `npm ci`, restart Storybook, and
  reconnect the AI client.
- Verify the connection by listing component documentation and confirming that
  the returned stories belong to the active worktree.

### Color schemes

The login theme follows the device’s light or dark color preference. Semantic
colors are defined in `src/login/colors.css`, using the profile palette and tints
from Novari Designmanual v1.0 (2024), pages 24–26. The dark scheme uses supporting charcoal
surfaces and soft white text, with coral actions and blue-purple focus accents. To force a scheme, set
`data-theme="light"` or `data-theme="dark"` on the document’s `<html>` element.
In Storybook, System is the default. Use the theme toolbar to manually choose
Light or Dark, or switch back to System to follow the device preference.
