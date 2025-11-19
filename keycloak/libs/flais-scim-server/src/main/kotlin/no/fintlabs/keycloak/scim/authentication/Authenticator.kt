package no.fintlabs.keycloak.scim.authentication

import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.core.HttpHeaders
import no.fintlabs.keycloak.scim.context.ScimContext

object Authenticator {
    private val validatorRegistry = JwtValidatorRegistry()

    fun verifyAuthenticated(scimContext: ScimContext) {
        val context =
            requireNotNull(scimContext.session.context) {
                "Keycloak context is not set"
            }

        val authorization = context.requestHeaders.getHeaderString(HttpHeaders.AUTHORIZATION)
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw NotAuthorizedException("Missing or invalid Authorization header")
        }

        val token = authorization.substringAfter("Bearer ")
        if (!validatorRegistry.getOrCreate(scimContext.organization.id, scimContext.config).isValid(token)) {
            throw NotAuthorizedException("Invalid token")
        }
    }
}
