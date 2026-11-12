When working on UI components, always use the `keycloak-theme` MCP tools to access Storybook's component and documentation knowledge before answering or taking any action.

- **CRITICAL: Never hallucinate component properties!** Before using ANY property on a component from a design system (including common-sounding ones like `shadow`, etc.), you MUST use the MCP tools to check if the property is actually documented for that component.
- Query `docs-list` to get a list of all components
- Query `docs-show` for that component to see all available properties and examples
- Only use properties that are explicitly documented or shown in example stories
- If a property isn't documented, do not assume properties based on naming conventions or common patterns from other libraries. Check back with the user in these cases.
- Use the `get-storybook-story-instructions` tool to fetch the latest instructions for creating or updating stories. This will ensure you follow current conventions and recommendations.
- Run `test-run` if the connected server exposes it. Otherwise run `npm run check` and review the relevant stories in the browser. Report which checks actually ran; preview URLs alone do not verify rendering or interaction tests.
- After UI changes, use `stories-changed` and `stories-preview` to identify affected stories and share review links.
- Verify that the MCP endpoint belongs to Storybook running in the intended worktree. See the README for setup and troubleshooting.

Remember: A story name might not reflect the property name correctly, so always verify properties through documentation or example stories before using them.
