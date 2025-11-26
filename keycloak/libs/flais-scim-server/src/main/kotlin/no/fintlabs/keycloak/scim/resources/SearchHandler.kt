package no.fintlabs.keycloak.scim.resources

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.annotations.NotNull
import com.unboundid.scim2.common.annotations.Nullable
import com.unboundid.scim2.common.exceptions.BadRequestException
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.messages.SortOrder
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.server.utils.ResourceComparator
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import com.unboundid.scim2.server.utils.SchemaAwareFilterEvaluator
import jakarta.ws.rs.core.UriInfo

class SearchHandler<T : ScimResource> {
    @NotNull
    val filter: Filter?

    @Nullable
    val startIndex: Int?

    @Nullable
    val count: Int?

    @NotNull
    val filterEvaluator: SchemaAwareFilterEvaluator

    @Nullable
    val resourceComparator: ResourceComparator<ScimResource>?

    @NotNull
    val responsePreparer: ResourcePreparer<ScimResource>

    @Throws(BadRequestException::class)
    constructor(
        resourceType: ResourceTypeDefinition,
        uriInfo: UriInfo,
    ) {
        filterEvaluator = SchemaAwareFilterEvaluator(resourceType)
        responsePreparer = ResourcePreparer(resourceType, uriInfo)

        val qp = uriInfo.queryParameters

        filter =
            qp
                .getFirst(ApiConstants.QUERY_PARAMETER_FILTER)
                ?.let { Filter.fromString(it) }

        startIndex =
            qp
                .getFirst(ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX)
                ?.toIntOrNull()
                ?.coerceAtLeast(1)

        count =
            qp
                .getFirst(ApiConstants.QUERY_PARAMETER_PAGE_SIZE)
                ?.toIntOrNull()
                ?.coerceAtLeast(0)

        val sortByString = qp.getFirst(ApiConstants.QUERY_PARAMETER_SORT_BY)
        val sortOrderString = qp.getFirst(ApiConstants.QUERY_PARAMETER_SORT_ORDER)

        val sortBy =
            try {
                sortByString?.let { Path.fromString(it) }
            } catch (e: BadRequestException) {
                throw BadRequestException.invalidValue(
                    "'$sortByString' is not a valid value for the sortBy parameter: ${e.message}",
                )
            }

        val sortOrder =
            sortOrderString
                ?.let(SortOrder::fromName)
                ?: SortOrder.ASCENDING

        resourceComparator =
            sortBy?.let {
                ResourceComparator(it, sortOrder, resourceType)
            }
    }

    fun createSearchResult(resources: Sequence<T>): SearchResults<T> {
        val resolvedStartIndex = (startIndex ?: 1).coerceAtLeast(1)
        val resolvedCount = count ?: Int.MAX_VALUE

        var preparedResources =
            resources
                .map { it.asGenericScimResource() }
                .filter { prepareAndFilter(it) }
                .toList()

        val totalCount = preparedResources.size
        if (totalCount == 0) {
            return SearchResults(
                startIndex = resolvedStartIndex,
            )
        }

        resourceComparator?.let { comparator ->
            preparedResources =
                preparedResources.sortedWith(comparator)
        }

        val fromIndex = (resolvedStartIndex - 1).coerceAtMost(totalCount)
        val toIndex = (fromIndex + resolvedCount).coerceAtMost(totalCount)
        val page =
            if (fromIndex >= toIndex) {
                emptyList()
            } else {
                preparedResources.subList(fromIndex, toIndex)
            }

        @Suppress("UNCHECKED_CAST")
        return SearchResults(
            page as List<T>,
            page.size,
            totalCount,
            resolvedStartIndex,
        )
    }

    private fun prepareAndFilter(resource: GenericScimResource): Boolean {
        responsePreparer.setResourceTypeAndLocation(resource)
        val currentFilter = filter ?: return true
        return currentFilter.visit(filterEvaluator, resource.objectNode)
    }
}
