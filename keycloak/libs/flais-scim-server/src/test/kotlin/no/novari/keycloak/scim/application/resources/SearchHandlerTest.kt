package no.novari.keycloak.scim.application.resources

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.messages.SortOrder
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.UriInfo
import no.novari.keycloak.scim.resources.SearchHandler
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class SearchHandlerTest {
    private fun mockScimInfrastructure() {
        mockkConstructor(ResourcePreparer::class)

        every {
            anyConstructed<ResourcePreparer<ScimResource>>().setResourceTypeAndLocation(any())
        } just Runs
    }

    fun uriInfoWithParams(params: Map<String, String>): UriInfo {
        val query = MultivaluedHashMap<String, String>()
        params.forEach { (k, v) -> query.add(k, v) }

        return TestUriInfo(
            URI("http://localhost/scim/v2/Users"),
            query,
        )
    }

    private fun createHandler(params: Map<String, String>): SearchHandler<GenericScimResource> {
        mockScimInfrastructure()
        val resourceType = mockk<ResourceTypeDefinition>(relaxed = true)
        val uriInfo = uriInfoWithParams(params)
        return SearchHandler(resourceType, uriInfo)
    }

    @Test
    fun `constructor with no query params leaves filter and pagination null`() {
        val handler = createHandler(emptyMap())

        assertNull(handler.filter)
        assertNull(handler.startIndex)
        assertNull(handler.count)
        assertNull(handler.sortBy)
        assertEquals(SortOrder.ASCENDING, handler.sortOrder)
    }

    @Test
    fun `constructor parses filter when present`() {
        assertNotNull(
            createHandler(
                mapOf(ApiConstants.QUERY_PARAMETER_FILTER to """userName eq "alice""""),
            ).filter,
        )
    }

    @Test
    fun `constructor coerces startIndex and count to valid ranges`() {
        val handler =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "0",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "-5",
                ),
            )

        assertEquals(1, handler.startIndex)
        assertEquals(0, handler.count)
    }

    @Test
    fun `constructor parses sort parameters when sortBy is present`() {
        val handler =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_SORT_BY to "userName",
                    ApiConstants.QUERY_PARAMETER_SORT_ORDER to "descending",
                ),
            )

        assertEquals("userName", handler.sortBy.toString())
        assertEquals(SortOrder.DESCENDING, handler.sortOrder)
    }

    @Test
    fun `createPagedSearchResult returns database page with index pagination metadata`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "5",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "2",
                ),
            ).createPagedSearchResult(
                sequenceOf(
                    GenericScimResource(),
                    GenericScimResource(),
                ),
                totalResults = 10,
            )

        assertEquals(10, result.totalResults)
        assertEquals(5, result.startIndex)
        assertEquals(2, result.itemsPerPage)
        assertEquals(2, result.resources.size)
    }

    @Test
    fun `createPagedSearchResult returns cursor pagination metadata`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(
                mapOf(ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to "current-cursor"),
            ).createPagedSearchResult(
                sequenceOf(
                    GenericScimResource(),
                    GenericScimResource(),
                ),
                totalResults = 10,
                nextCursor = "next-cursor",
                cursorPagination = true,
            )

        assertEquals(10, result.totalResults)
        assertEquals("next-cursor", result.nextCursor)
        assertEquals(2, result.itemsPerPage)
        assertEquals(2, result.resources.size)
    }

    @Test
    fun `createPagedSearchResult keeps empty database page metadata`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "10",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "5",
                ),
            ).createPagedSearchResult(
                emptySequence(),
                totalResults = 3,
            )

        assertEquals(3, result.totalResults)
        assertEquals(10, result.startIndex)
        assertEquals(0, result.itemsPerPage)
        assertTrue(result.resources.isEmpty())
    }

    @Test
    fun `createPagedSearchResult defaults startIndex for index pagination`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(emptyMap()).createPagedSearchResult(
                sequenceOf(GenericScimResource()),
                totalResults = 1,
            )

        assertEquals(1, result.startIndex)
        assertEquals(1, result.itemsPerPage)
        assertEquals(1, result.resources.size)
    }

    @Test
    fun `constructor detects cursor pagination when cursor parameter is present`() {
        val handler =
            createHandler(
                mapOf(ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to ""),
            )

        assertTrue(handler.cursorRequested)
        assertEquals("", handler.cursor)
    }

    @Test
    fun `constructor leaves cursor pagination disabled when cursor parameter is absent`() {
        val handler = createHandler(emptyMap())

        assertEquals(false, handler.cursorRequested)
        assertNull(handler.cursor)
    }

    @Test
    fun `constructor leaves count null when page size is absent`() {
        assertNull(createHandler(emptyMap()).count)
    }
}
