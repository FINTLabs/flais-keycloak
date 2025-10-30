## Exporting Keycloak config

```
command: export --file=/opt/keycloak/data/export/external-realm.json --optimized
volumes:
  - ./exports:/opt/keycloak/data/export
```
