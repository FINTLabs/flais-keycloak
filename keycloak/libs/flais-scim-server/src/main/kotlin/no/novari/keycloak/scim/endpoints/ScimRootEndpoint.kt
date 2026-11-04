package no.novari.keycloak.scim.endpoints

import jakarta.ws.rs.Path
import no.novari.keycloak.scim.context.ScimContext

class ScimRootEndpoint(
    private val scimContext: ScimContext,
) {
    val resourceClasses =
        listOf(
            ScimUserEndpoint::class,
        )

    @Path("Users")
    fun users() = ScimUserEndpoint(scimContext)

    @Path("Schemas")
    fun schemas() = ScimSchemaEndpoint(scimContext.session, resourceClasses)

    @Path("ServiceProviderConfig")
    fun serviceProviderConfig() = ScimServiceProviderConfigEndpoint(scimContext.session)

    @Path("ResourceTypes")
    fun resourceTypes() = ScimResourceTypesEndpoint(scimContext.session, resourceClasses)
}
