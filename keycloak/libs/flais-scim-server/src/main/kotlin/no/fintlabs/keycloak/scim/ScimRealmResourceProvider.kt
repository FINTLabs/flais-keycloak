package no.fintlabs.keycloak.scim

import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.ext.Provider
import no.fintlabs.keycloak.scim.authentication.Authenticator.verifyAuthenticated
import no.fintlabs.keycloak.scim.authentication.JwtValidatorRegistry
import no.fintlabs.keycloak.scim.context.createScimContext
import no.fintlabs.keycloak.scim.endpoints.ScimRootEndpoint
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

@Provider
class ScimRealmResourceProvider(
    private val session: KeycloakSession,
    private val authValidatorRegistry: JwtValidatorRegistry,
) : RealmResourceProvider {
    @Path("v2/{organizationId}")
    fun scimOrganization(
        @PathParam("organizationId") organizationId: String,
    ): ScimRootEndpoint {
        val context = createScimContext(session, organizationId)
        verifyAuthenticated(context, authValidatorRegistry)
        return ScimRootEndpoint(context)
    }

    override fun getResource() = this

    override fun close() = Unit
}
