package no.novari.keycloak.scim.utils

import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.types.Meta
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import com.unboundid.scim2.server.utils.ServerUtils
import jakarta.ws.rs.core.UriBuilder
import jakarta.ws.rs.core.UriInfo

class ScimResourcePreparer<R : ScimResource>(
    private val resourceTypeDefinition: ResourceTypeDefinition,
    private val uriInfo: UriInfo,
) : ResourcePreparer<R>(resourceTypeDefinition, uriInfo) {
    override fun setResourceTypeAndLocation(returnedResource: R) {
        val meta = returnedResource.meta ?: Meta()
        var metaUpdated = returnedResource.meta == null

        if (meta.resourceType == null) {
            meta.resourceType = resourceTypeDefinition.name
            metaUpdated = true
        }

        if (meta.location == null) {
            meta.location = returnedResource.id
                ?.let { id ->
                    val locationBuilder = UriBuilder.fromUri(uriInfo.absolutePath)
                    if (uriInfo.pathParameters.containsKey("id")) {
                        locationBuilder.replacePath(
                            uriInfo.absolutePath.rawPath
                                .trimEnd('/')
                                .substringBeforeLast('/'),
                        )
                    }
                    locationBuilder.segment(ServerUtils.encodeTemplateNames(id)).build()
                }
                ?: uriInfo.absolutePath
            metaUpdated = true
        }

        if (metaUpdated) {
            returnedResource.meta = meta
        }
    }
}
