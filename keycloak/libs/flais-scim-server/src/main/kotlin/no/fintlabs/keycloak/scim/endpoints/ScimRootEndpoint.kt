package no.fintlabs.keycloak.scim.endpoints

import jakarta.ws.rs.Path
import no.fintlabs.keycloak.scim.context.ScimContext

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
    fun schemas() = ScimSchemaEndpoint(resourceClasses)

    @Path("ServiceProviderConfig")
    fun serviceProviderConfig() = ScimServiceProviderConfigEndpoint()

    @Path("ResourceTypes")
    fun resourceTypes() = ScimResourceTypesEndpoint(resourceClasses)
}
