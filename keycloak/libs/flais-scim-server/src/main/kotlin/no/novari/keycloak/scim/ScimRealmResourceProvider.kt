package no.novari.keycloak.scim

import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo
import no.novari.keycloak.scim.authentication.Authenticator.verifyAuthenticated
import no.novari.keycloak.scim.authentication.JwtValidatorRegistry
import no.novari.keycloak.scim.context.ScimUriInfo
import no.novari.keycloak.scim.context.createScimContext
import no.novari.keycloak.scim.endpoints.ScimRootEndpoint
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

class ScimRealmResourceProvider(
    private val session: KeycloakSession,
    private val authValidatorRegistry: JwtValidatorRegistry,
) : RealmResourceProvider {
    @Path("v2/{organizationId}")
    fun scimOrganization(
        @Context uriInfo: UriInfo,
        @PathParam("organizationId") organizationId: String,
    ): ScimRootEndpoint {
        val baseUri =
            uriInfo.baseUriBuilder
                .path("scim")
                .path("v2")
                .path(organizationId)
                .build()

        val context = createScimContext(session, organizationId, ScimUriInfo(uriInfo, baseUri))
        verifyAuthenticated(context, authValidatorRegistry)
        return ScimRootEndpoint(context)
    }

    override fun getResource() = this

    override fun close() = Unit
}
