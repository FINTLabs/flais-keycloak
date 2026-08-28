# Organizations

Configuration of a single **Organization**.

This configuration is based on a single County organization and serves as a template.
Some settings may differ depending on the vendor or specific integration.

# Settings

| Setting      | Value / Guidance                                                |
| ------------ | --------------------------------------------------------------- |
| Name         | Example: `Novari IKS`                                           |
| Alias        | Lowercase. Use `-` for multi-word names (example: `novari-iks`) |
| Domains      | Add valid domains                                               |
| Redirect URL | Not specified                                                   |
| Description  | Any                                                             |

# Attributes

## SCIM attributes

Required for **SCIM integration**.

| Attribute                  | Value / Guidance |
| -------------------------- | ---------------- |
| `SCIM_EXTERNAL_JWKS_URI`   | Vendor specific  |
| `SCIM_EXTERNAL_AUDIENCE`   | Vendor specific  |
| `SCIM_LINK_IDP`            | `true` / `false` |
| `SCIM_EXTERNAL_ISSUER`     | Vendor specific  |
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`       |

## Organization-specific attributes

| Attribute             | Description                            |
| --------------------- | -------------------------------------- |
| `TENANT_ID`           | Tenant identifier for the organization |
| `ORGANIZATION_ID`     | Internal organization identifier       |
| `ORGANIZATION_NUMBER` | Official organization number           |

# Identity providers

Associated Identity Providers (IDPs) must be linked to the organization.

| Setting     | Value                                   |
| ----------- | --------------------------------------- |
| Linked IDPs | Link relevant IDPs for the organization |
