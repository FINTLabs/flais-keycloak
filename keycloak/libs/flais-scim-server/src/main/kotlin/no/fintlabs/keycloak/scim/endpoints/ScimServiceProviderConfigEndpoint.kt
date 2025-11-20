package no.fintlabs.keycloak.scim.endpoints

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.types.AuthenticationScheme
import com.unboundid.scim2.common.types.BulkConfig
import com.unboundid.scim2.common.types.ChangePasswordConfig
import com.unboundid.scim2.common.types.ETagConfig
import com.unboundid.scim2.common.types.FilterConfig
import com.unboundid.scim2.common.types.PatchConfig
import com.unboundid.scim2.common.types.ServiceProviderConfigResource
import com.unboundid.scim2.common.types.SortConfig
import com.unboundid.scim2.common.utils.ApiConstants.MEDIA_TYPE_SCIM
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import jakarta.ws.rs.GET
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriInfo
import java.net.URI

@ResourceType(
    description = "SCIM 2.0 Service Provider Config",
    name = "ServiceProviderConfig",
    schema = ServiceProviderConfigResource::class,
    discoverable = false,
)
class ScimServiceProviderConfigEndpoint {
    @GET
    @Produces(MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun get(
        @Context uriInfo: UriInfo,
    ): GenericScimResource {
        return serviceProviderConfig().asGenericScimResource().apply {
            ResourcePreparer<GenericScimResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
                .setResourceTypeAndLocation(this)
        }
    }

    fun serviceProviderConfig(): ServiceProviderConfigResource =
        ServiceProviderConfigResource(
            "https://example.com/help/scim.html",
            PatchConfig(true),
            BulkConfig(false, 1000, 1048567),
            FilterConfig(true, 200),
            ChangePasswordConfig(false),
            SortConfig(true),
            ETagConfig(true),
            listOf(
                AuthenticationScheme(
                    "OAuth Bearer Token",
                    "Authentication scheme using the OAuth Bearer Token Standard",
                    URI.create("https://www.rfc-editor.org/info/rfc6750"),
                    URI.create("https://example.com/help/oauth.html"),
                    "oauthbearertoken",
                    true,
                ),
            ),
        )

    companion object {
        private val RESOURCE_TYPE_DEFINITION =
            ResourceTypeDefinition.fromJaxRsResource(ServiceProviderConfigResource::class.java)
    }
}
