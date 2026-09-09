package no.novari.keycloak.scim.resources

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.annotations.NotNull
import com.unboundid.scim2.common.annotations.Nullable
import com.unboundid.scim2.common.exceptions.BadRequestException
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.messages.SortOrder
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import jakarta.ws.rs.core.UriInfo

class SearchHandler<T : ScimResource> {
    @NotNull
    val filter: Filter?

    @Nullable
    val startIndex: Int?

    @Nullable
    val count: Int?

    val cursorRequested: Boolean

    @Nullable
    val cursor: String?

    /** The parsed `sortBy` path, exposed so callers can try to push sorting into the database. */
    @Nullable
    val sortBy: Path?

    @NotNull
    val sortOrder: SortOrder

    @NotNull
    val responsePreparer: ResourcePreparer<ScimResource>

    @Throws(BadRequestException::class)
    constructor(
        resourceType: ResourceTypeDefinition,
        uriInfo: UriInfo,
    ) {
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

        cursorRequested = qp.containsKey(ApiConstants.QUERY_PARAMETER_PAGE_CURSOR)
        cursor = qp.getFirst(ApiConstants.QUERY_PARAMETER_PAGE_CURSOR)

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

        this.sortBy = sortBy
        this.sortOrder = sortOrder
    }

    /**
     * Builds a [ListResponse] from resources that have **already** been filtered, sorted and paged
     * by the caller — typically by a database query.
     *
     * This applies no filter, no sort and no sub-listing; it only runs the response preparer over
     * each resource. [totalResults] is the size of the full matching set, not of [resources].
     *
     * Only use this when [filter] and [sortBy] have already been pushed down, otherwise the reported
     * [totalResults] will not agree with the returned page.
     */
    fun createPagedSearchResult(
        resources: Sequence<T>,
        totalResults: Int,
        nextCursor: String? = null,
        cursorPagination: Boolean = false,
    ): ListResponse<T> {
        val preparedResources =
            resources
                .map { it.asGenericScimResource() }
                .onEach { responsePreparer.setResourceTypeAndLocation(it) }
                .toList()

        if (cursorPagination) {
            @Suppress("UNCHECKED_CAST")
            return ListResponse(
                totalResults,
                nextCursor,
                preparedResources.size,
                preparedResources as List<T>,
            )
        }

        @Suppress("UNCHECKED_CAST")
        return ListResponse(
            totalResults,
            preparedResources as List<T>,
            (startIndex ?: 1).coerceAtLeast(1),
            preparedResources.size,
        )
    }
}
