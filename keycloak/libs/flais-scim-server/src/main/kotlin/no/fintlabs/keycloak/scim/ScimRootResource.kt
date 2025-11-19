package no.fintlabs.keycloak.scim

import jakarta.ws.rs.Path
import no.fintlabs.keycloak.scim.route.scimRoute

@Path("scim/v2/{organizationId}")
class ScimRootResource {
    val resourceClasses =
        listOf(
            ScimUserResource::class,
        )

    @Path("/Users")
    fun users() = scimRoute { ScimUserResource(it) }

    @Path("/Schemas")
    fun schemas() = scimRoute { ScimSchemaResosurce(resourceClasses) }

    @Path("/ServiceProviderConfig")
    fun serviceProviderConfig() = scimRoute { ScimServiceProviderConfigResource() }

    @Path("/ResourceTypes")
    fun resourceTypes() = scimRoute { ScimResourceTypesResource(resourceClasses) }
}
