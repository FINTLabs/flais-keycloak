package no.fintlabs.keycloak.scim

import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import no.fintlabs.keycloak.scim.consts.ContentTypes
import org.keycloak.models.KeycloakSession
import org.keycloak.organization.OrganizationProvider

@Path("scim/v2/{organizationId}")
@Produces(ContentTypes.APPLICATION_SCIM_JSON)
@Consumes(ContentTypes.APPLICATION_SCIM_JSON)
class ScimRootResource {

    @Path("/Users")
    fun users(
        @Context session: KeycloakSession,
        @PathParam("organizationId") organizationId: String
    ) = ScimUserResource(getScimContext(session, organizationId))

    private fun getScimContext(session: KeycloakSession, organizationId: String): ScimContext {
        val context = session.context
        checkNotNull(context) { "Keycloak context is not set" }

        val orgProvider = session.getProvider(OrganizationProvider::class.java)
        val organization = orgProvider.getById(organizationId) ?: throw NotFoundException("Organization not found")
        return ScimContext(
            session,
            context.realm,
            orgProvider,
            organization
        )
    }
}
