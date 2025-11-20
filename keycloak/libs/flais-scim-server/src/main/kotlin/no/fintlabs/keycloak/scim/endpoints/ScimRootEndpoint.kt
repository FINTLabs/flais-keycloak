package no.fintlabs.keycloak.scim.endpoints

import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import no.fintlabs.keycloak.scim.http.scimRoute

class ScimRootEndpoint {
    val resourceClasses =
        listOf(
            ScimUserEndpoint::class,
        )

    @Path("v2/{organizationId}/test")
    fun test(): Response = Response.ok().build()

    @Path("v2/{organizationId}/Users")
    fun users() = scimRoute { ScimUserEndpoint(it) }

    @Path("v2/{organizationId}/Schemas")
    fun schemas() = scimRoute { ScimSchemaEndpoint(resourceClasses) }

    @Path("v2/{organizationId}/ServiceProviderConfig")
    fun serviceProviderConfig() = scimRoute { ScimServiceProviderConfigEndpoint() }

    @Path("v2/{organizationId}/ResourceTypes")
    fun resourceTypes() = scimRoute { ScimResourceTypesEndpoint(resourceClasses) }
}
