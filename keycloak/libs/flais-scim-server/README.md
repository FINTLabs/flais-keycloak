# flais-scim-server

The provider is built with [Ping Identity’s SCIM 2.0 SDK ](https://github.com/pingidentity/scim2) and gives us complete ownership and flexibility in how SCIM is implemented and provisioning is handled with Keycloak.

## 🛠️ Development

### Local development

The Dockerfile builds and bundles the provider with Keycloak. After changes, simply redeploy/restart keycloak with task: `gradle restart` as mentioned earlier.

### Test

Root project is responsible for integration tests, while unit tests stays in the module.

## HTTP debugging

Enable detailed SCIM HTTP logging when starting Keycloak:

```sh
bin/kc.sh start --log-level="INFO,no.novari.keycloak.scim:DEBUG"
```

For HTTP traffic only, use `INFO,no.novari.keycloak.scim.http:DEBUG` instead.
The equivalent environment variable is
`KC_LOG_LEVEL=INFO,no.novari.keycloak.scim:DEBUG`. If you have set a separate
console or file handler level, allow DEBUG there as well. Rebuild the provider
and redeploy it before enabling the new logging.

Every request to `/realms/{realm}/scim` and its subpaths is logged immediately,
including discovery, GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS. Each
exchange has a generated `id` that connects these records:

- `SCIM HTTP REQUEST`: method, URI, query parameters and incoming headers.
- `SCIM HTTP REQUEST BODY`: consumed JSON payload and bytes read, when a body exists.
- `SCIM HTTP RESPONSE`: status code, reason, outgoing headers and elapsed milliseconds,
  including authentication errors, unmatched routes and responses without a body.
- `SCIM HTTP RESPONSE BODY`: serialized JSON actually passed to the output stream,
  byte count, completion flag and elapsed milliseconds including serialization.
- `SCIM HTTP RESPONSE WRITE FAILED`: exception type if serialization or writing fails;
  the original exception continues through the normal error handling.

Authorization headers, cookies, password/secret/token/API-key/credential fields and
sensitive query parameters are redacted. This includes nested JSON and SCIM PATCH
operations such as `{"path":"password","value":"..."}`. JSON is compacted and
control characters are escaped so a payload cannot add fake log lines. Normal
SCIM attributes, including names and email addresses, are visible in DEBUG logs.

The body capture defaults to **65,536 bytes per request and per response**. Set
`SCIM_HTTP_LOG_MAX_BODY_BYTES=1048576` to capture up to 1 MiB, or set the JVM
property `-Dscim.http.log.max-body-bytes=1048576` (which takes precedence). The
supported range is 0–16,777,216 bytes; 0 disables body content while keeping
request/response metadata. Invalid values fall back to the default.

Bodies over the limit are omitted with an explicit marker rather than logging a
partial JSON document that might expose a credential. Malformed JSON, non-JSON
and encoded bodies are also marked as omitted. Request bodies are captured as
the application reads them: an authentication or routing failure may report
`<body not consumed>`, and an early parser failure may report an incomplete body.
The logger never drains an unread request or blocks the pre-matching event loop
to obtain its body. Response bytes continue directly to the original output
stream; the bounded capture does not replace the serializer or response entity.

Logging is limited to this SCIM provider. It does not log Keycloak login/admin
traffic, outbound JWKS fetches or requests rejected before entering Jakarta REST.
At INFO and higher there is no body capture. The provider archive includes
`META-INF/beans.xml` so Keycloak can discover the logging providers; when deploying
an optimized Keycloak image, include the updated provider during `kc.sh build`.
