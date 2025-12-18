# flais-scim-auth

Very simple application that exposes endpoints that can be used by flais-scim-server for auth requests.

## 🛠️ Development

### Local development

To test the application with Keycloak running, you can spin it up but need to make some adjustments to the JWKS_URI that flais-scim-server uses for the organization.

-   windows WSL2:
    -   Change SCIM_EXTERNAL_JWKS_URI for org to use ip of WSL instance.
    -   Get IP: `ip -4 addr show eth0 | awk '/inet /{print $2}' | cut -d/ -f1`

Example: http://172.27.58.167:9090/discovery/v2.0/keys
