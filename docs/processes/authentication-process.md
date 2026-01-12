# Authentication Overview

This section describes the high-level authentication flow.

## Summary

1. User opens application
2. User is redirected to Keycloak
3. Organization is selected
4. IDP is selected
5. User authenticates with external IDP
6. Keycloak processes callback and redirects back

## Error handling

-   Invalid client or request
-   Client not allowed for organization
-   No enabled IDPs
-   User not allowed to authenticate

## Diagram

The diagram below shows the high-level authentication process
from the user opening the application to being redirected back
with an authorization code.

It focuses on _decisions_ and _error conditions_, not Keycloak internals.

```mermaid
flowchart TD
    is_client_and_req_valid{Is client and request valid?}
    is_client_access_to_org{Does client have access to organization?}
    is_org_multiple_idps{Does organization have multiple IDPs?}
    is_user_access_ext_idp{Does user have access to client?}

    user_opens_app[User opens application]
    user_selects_org[User selects organization]
    user_selects_idp[User selects IDP]
    user_redirected_keycloak[User is redirected to Keycloak]
    user_logs_in_with_ext_idp[User logs in with external IDP]

    err_access_denied[Access denied error]
    err_client_invalid[Invalid client/request]

    kc_redirects_ext_idp[Keycloak redirects to external IDP]
    kc_redirects_idp_selector[Keycloak redirects to page with available IDPs]
    kc_redirects_org_selector[Keycloak redirects to page with available organizations]
    kc_redirects_app[Keycloak redirects to app with code]

    idp_redirect_to_keycloak[IDP redirects back to Keycloak]

    %% ----- FLOW -----  %%
    user_opens_app --> user_redirected_keycloak
    user_redirected_keycloak --> is_client_and_req_valid

    is_client_and_req_valid -- Yes --> kc_redirects_org_selector
    is_client_and_req_valid -- No --> err_client_invalid

    kc_redirects_org_selector --> user_selects_org
    user_selects_org --> is_client_access_to_org

    is_client_access_to_org -- Yes --> is_org_multiple_idps
    is_client_access_to_org -- No --> err_access_denied
    is_org_multiple_idps -- Yes --> kc_redirects_idp_selector
    is_org_multiple_idps -- No --> kc_redirects_ext_idp

    kc_redirects_idp_selector --> user_selects_idp
    user_selects_idp --> kc_redirects_ext_idp
    kc_redirects_ext_idp --> user_logs_in_with_ext_idp

    user_logs_in_with_ext_idp --> is_user_access_ext_idp

    is_user_access_ext_idp -- Yes --> idp_redirect_to_keycloak
    is_user_access_ext_idp -- No --> err_access_denied

    idp_redirect_to_keycloak --> kc_redirects_app
```
