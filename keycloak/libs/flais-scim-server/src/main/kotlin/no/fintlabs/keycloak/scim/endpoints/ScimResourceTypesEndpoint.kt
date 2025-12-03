package no.fintlabs.keycloak.scim.endpoints

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.types.ResourceTypeResource
import com.unboundid.scim2.common.utils.ApiConstants.MEDIA_TYPE_SCIM
import com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_FILTER
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.SchemaAwareFilterEvaluator
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriInfo
import no.fintlabs.keycloak.scim.utils.ResourcePath
import no.fintlabs.keycloak.scim.utils.ResourceTypeDefinitionUtil.createResourceTypeDefinition
import kotlin.reflect.KClass

@ResourceType(
    description = "SCIM 2.0 Resource Type",
    name = "ResourceType",
    schema = ResourceTypeResource::class,
    discoverable = false,
)
@ResourcePath("ResourceTypes")
class ScimResourceTypesEndpoint(
    private val resourceClasses: List<KClass<*>>,
) {
    @GET
    @Produces(MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun search(
        @Context uriInfo: UriInfo,
        @QueryParam(QUERY_PARAMETER_FILTER) filterString: String?,
    ): ListResponse<ScimResource> {
        if (!filterString.isNullOrEmpty()) {
            throw ForbiddenException("Filtering is not allowed")
        }

        val preparer = ResourcePreparer<GenericScimResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
        return ListResponse(
            getResourceTypes()
                .map { schema ->
                    schema.asGenericScimResource().apply {
                        preparer.setResourceTypeAndLocation(this)
                    }
                }.toList(),
        )
    }

    @GET
    @Path("/{id}")
    @Produces(MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun get(
        @PathParam("id") id: String,
        @Context uriInfo: UriInfo,
    ): GenericScimResource {
        val filter =
            Filter.or(
                Filter.eq("id", id),
                Filter.eq("name", id),
            )
        val filterEvaluator = SchemaAwareFilterEvaluator(RESOURCE_TYPE_DEFINITION)
        val preparer = ResourcePreparer<GenericScimResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
        getResourceTypes().forEach { schema ->
            val resource = schema.asGenericScimResource()
            if (filter.visit(filterEvaluator, resource.objectNode)) {
                preparer.setResourceTypeAndLocation(resource)
                return resource
            }
        }
        throw ResourceNotFoundException("No schema defined with id $id")
    }

    fun getResourceTypes() =
        resourceClasses
            .mapNotNull { resourceClass ->
                createResourceTypeDefinition(resourceClass)
                    .takeIf { it.isDiscoverable }
                    ?.toScimResource()
            }

    companion object {
        private val RESOURCE_TYPE_DEFINITION = createResourceTypeDefinition<ScimResourceTypesEndpoint>()
    }
}
