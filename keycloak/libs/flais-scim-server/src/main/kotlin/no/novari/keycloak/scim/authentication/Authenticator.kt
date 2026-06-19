package no.novari.keycloak.scim.authentication

import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.core.HttpHeaders
import no.novari.keycloak.scim.context.ScimContext
import org.jboss.logging.Logger

object Authenticator {
    private val logger: Logger = Logger.getLogger(Authenticator::class.java)

    fun verifyAuthenticated(
        scimContext: ScimContext,
        validatorRegistry: JwtValidatorRegistry,
    ) {
        val context =
            requireNotNull(scimContext.session.context) {
                "Keycloak context is not set"
            }

        val authorization = context.requestHeaders.getHeaderString(HttpHeaders.AUTHORIZATION)
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            logger.warnf(
                "SCIM authentication failed: missing or invalid Authorization header. org=%s realm=%s",
                scimContext.organization.alias,
                scimContext.realm.name,
            )
            throw NotAuthorizedException("Missing or invalid Authorization header")
        }

        val token = authorization.substringAfter("Bearer ")
        if (!validatorRegistry.getOrCreate(scimContext.organization.id, scimContext.config).isValid(token)) {
            logger.warnf(
                "SCIM authentication failed: invalid bearer token. org=%s realm=%s",
                scimContext.organization.alias,
                scimContext.realm.name,
            )
            throw NotAuthorizedException("Invalid token")
        }
    }
}
