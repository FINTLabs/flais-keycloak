package no.fintlabs.keycloak.scim.endpoints

import jakarta.ws.rs.Path
import no.fintlabs.keycloak.scim.http.scimRoute

@Path("scim/v2/{organizationId}")
class ScimRootEndpoint {
    val resourceClasses =
        listOf(
            ScimUserEndpoint::class,
        )

    @Path("/Users")
    fun users() = scimRoute { ScimUserEndpoint(it) }

    @Path("/Schemas")
    fun schemas() = scimRoute { ScimSchemaEndpoint(resourceClasses) }

    @Path("/ServiceProviderConfig")
    fun serviceProviderConfig() = scimRoute { ScimServiceProviderConfigEndpoint() }

    @Path("/ResourceTypes")
    fun resourceTypes() = scimRoute { ScimResourceTypesEndpoint(resourceClasses) }
}
