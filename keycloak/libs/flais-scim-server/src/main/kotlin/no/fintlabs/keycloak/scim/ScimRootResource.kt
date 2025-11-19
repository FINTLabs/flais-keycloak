package no.fintlabs.keycloak.scim

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import no.fintlabs.keycloak.scim.consts.ContentTypes
import org.keycloak.models.KeycloakSession
import org.keycloak.organization.OrganizationProvider

@Path("scim/v2/{organizationId}")
@Produces(ContentTypes.APPLICATION_SCIM_JSON)
@Consumes(ContentTypes.APPLICATION_SCIM_JSON)
class ScimRootResource {
    val resourceClasses =
        listOf(
            ScimUserResource::class,
        )

    @Path("/Users")
    fun users(
        @Context session: KeycloakSession,
        @PathParam("organizationId") organizationId: String,
    ) = ScimUserResource(getScimContext(session, organizationId))

    @Path("/Schemas")
    fun schemas(
        @Context session: KeycloakSession,
        @PathParam("organizationId") organizationId: String,
    ) = ScimSchemaResosurce(resourceClasses)

    @Path("/ServiceProviderConfig")
    fun serviceProviderConfig(
        @Context session: KeycloakSession,
        @PathParam("organizationId") organizationId: String,
    ) = ScimServiceProviderConfigResource()

    @Path("/ResourceTypes")
    fun resourceTypes(
        @Context session: KeycloakSession,
        @PathParam("organizationId") organizationId: String,
    ) = ScimResourceTypesResource(resourceClasses)

    private fun getScimContext(
        session: KeycloakSession,
        organizationId: String,
    ): ScimContext {
        val context = session.context
        checkNotNull(context) { "Keycloak context is not set" }

        val orgProvider = session.getProvider(OrganizationProvider::class.java)
        val organization = orgProvider.getById(organizationId) ?: throw NotFoundException("Organization not found")
        return ScimContext(
            session,
            context.realm,
            orgProvider,
            organization,
        )
    }
}
