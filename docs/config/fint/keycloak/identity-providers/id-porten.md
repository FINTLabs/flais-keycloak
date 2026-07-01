# ID-Porten

Configuration of **ID-Porten**.

This configuration is based on an OpenID Connect v1.0 for ID-Porten and serves as a template.

# Settings

## General settings

| Setting       | Value / Guidance |
| ------------- | ---------------- |
| Alias         | id-porten        |
| Display name  | ID-Porten        |
| Display order | Not specified    |

## OpenID Connect settings

| Setting                               | Value                              |
| ------------------------------------- | ---------------------------------- |
| Authorization / Token / Userinfo URLs | See under                          |
| Validate signatures                   | On                                 |
| Use JWKS URL                          | On                                 |
| Use PKCE                              | On                                 |
| PKCE Method                           | `S256`                             |
| Client authentication                 | Client secret sent in request body |
| Client assertion signature algorithm  | Not specified                      |

> Use well-known endpoint to fill in required URLs: https://idporten.no/.well-known/openid-configuration

## OpenID Connect – Advanced

| Setting                                  | Value     |
| ---------------------------------------- | --------- |
| Pass login_hint                          | Off       |
| Pass max_age                             | Off       |
| Pass current locale                      | Off       |
| Backchannel logout                       | Off       |
| Send `id_token_hint` in logout requests  | Off       |
| Send `client_id` in logout requests      | On        |
| Disable user info                        | Off       |
| Disable nonce                            | Off       |
| Disable type claim check                 | Off       |
| Scopes                                   | `profile` |
| Prompt                                   | Login     |
| Accept `prompt=none` forward from client | Off       |
| Requires short state parameter           | Off       |
| Allowed clock skew                       | `300`     |
| Forwarded query parameters               | None      |

> [!NOTE]
> **Allowed clock skew** is set to `300` seconds.
>
> This matches Microsoft’s default token validation clock skew.  ID-porten expects clock-skew tolerance for `logout_token` validation.
>
> References:
> - [ID-porten: Validering av `logout_token`](https://docs.digdir.no/docs/idporten/oidc/oidc_func_backchannel_logout.html#validering-av-logout_token)
> - [Microsoft: `TokenValidationParameters.DefaultClockSkew`](https://learn.microsoft.com/en-us/dotnet/api/microsoft.identitymodel.tokens.tokenvalidationparameters.defaultclockskew)

## Advanced settings

| Setting                   | Value                       |
| ------------------------- | --------------------------- |
| Store tokens              | Off                         |
| Stored tokens readable    | Off                         |
| Access Token is JWT       | Off                         |
| Trust Email               | On                          |
| Account linking only      | Off                         |
| Hide on login page        | On                          |
| Show in account console   | Always                      |
| Verify essential claim    | Off                         |
| First login flow override | `First login flow override` |
| Post login flow           | `flais-post-login-flow`     |
| Sync mode                 | Force                       |
| Case-sensitive username   | Off                         |

> [!NOTE]
> Set **Post login flow** to `flais-post-login-flow` for every IDP.
>
> This runs `ClientOrgAccessAuthenticator` after each broker callback and helps prevent broker URL tampering attacks.
> See [flais-post-login-flow](../../../auth-flows/flais-post-login-flow.md).

# Mappers

## Username mapping

| Name                         | Mapper type                | Template       | Target            | Sync mode |
| ---------------------------- | -------------------------- | -------------- | ----------------- | --------- |
| `map_pid_as_username`        | Username Template Importer | `${CLAIM.pid}` | `LOCAL`           | Inherit   |
| `map_pid_as_brokerid`        | Username Template Importer | `${CLAIM.pid}` | `BROKER_ID`       | Inherit   |
| `map_pid_as_broker_username` | Username Template Importer | `${CLAIM.pid}` | `BROKER_USERNAME` | Inherit   |
