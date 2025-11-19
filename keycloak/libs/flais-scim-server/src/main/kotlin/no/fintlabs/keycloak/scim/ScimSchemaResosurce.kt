package no.fintlabs.keycloak.scim

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.types.SchemaResource
import com.unboundid.scim2.common.utils.ApiConstants.MEDIA_TYPE_SCIM
import com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_FILTER
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import com.unboundid.scim2.server.utils.SchemaAwareFilterEvaluator
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriInfo
import kotlin.reflect.KClass

@ResourceType(
    description = "SCIM 2.0 Schema",
    name = "Schema",
    schema = SchemaResource::class,
    discoverable = false,
)
class ScimSchemaResosurce(
    private val resourceClasses: List<KClass<*>>,
) {
    @GET
    @Produces(MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun search(
        @Context uriInfo: UriInfo,
        @QueryParam(QUERY_PARAMETER_FILTER) filterString: String?,
    ): ListResponse<GenericScimResource> {
        if (!filterString.isNullOrEmpty()) {
            throw ForbiddenException("Filtering is not allowed")
        }

        val preparer = ResourcePreparer<GenericScimResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
        return ListResponse(
            getSchemas()
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
        getSchemas().forEach { schema ->
            val resource = schema.asGenericScimResource()
            if (filter.visit(filterEvaluator, resource.objectNode)) {
                preparer.setResourceTypeAndLocation(resource)
                return resource
            }
        }
        throw ResourceNotFoundException("No schema defined with id $id")
    }

    private fun getSchemas() =
        resourceClasses
            .mapNotNull { resourceClass ->
                val td =
                    ResourceTypeDefinition
                        .fromJaxRsResource(resourceClass.java)
                        ?.takeIf { it.isDiscoverable }
                        ?: return@mapNotNull null

                buildList<SchemaResource> {
                    td.coreSchema?.let { add(it) }
                    addAll(td.schemaExtensions.keys)
                }
            }.flatten()

    companion object {
        private val RESOURCE_TYPE_DEFINITION =
            ResourceTypeDefinition.fromJaxRsResource(SchemaResource::class.java)
    }
}
