## Exporting Keycloak config

```
command: export --file=/opt/keycloak/data/export/external-realm.json --optimized
volumes:
  - ./exports:/opt/keycloak/data/export
```

## Running flais-scim-client locally

For windows WSL2:

Change SCIM_EXTERNAL_JWKS_URI to use ip of WSL instance.

ip -4 addr show eth0 | awk '/inet /{print $2}' | cut -d/ -f1

Example: http://172.27.58.167:9090/discovery/v2.0/keys
