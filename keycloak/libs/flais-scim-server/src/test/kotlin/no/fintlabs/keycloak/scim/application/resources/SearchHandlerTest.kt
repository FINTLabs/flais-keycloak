package no.fintlabs.keycloak.scim.application.resources

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.server.utils.ResourceComparator
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import com.unboundid.scim2.server.utils.SchemaAwareFilterEvaluator
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.UriInfo
import no.fintlabs.keycloak.scim.resources.SearchHandler
import no.fintlabs.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class SearchHandlerTest {
    private fun mockScimInfrastructure() {
        mockkConstructor(ResourcePreparer::class)
        mockkConstructor(SchemaAwareFilterEvaluator::class)
        mockkConstructor(ResourceComparator::class)

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
        assertNull(handler.resourceComparator)
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
    fun `constructor creates resourceComparator when sortBy is present`() {
        assertNotNull(
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_SORT_BY to "userName",
                    ApiConstants.QUERY_PARAMETER_SORT_ORDER to "descending",
                ),
            ).resourceComparator,
        )
    }

    @Test
    fun `createSearchResult with no pagination returns all resources`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(emptyMap()).createSearchResult(
                sequenceOf(
                    GenericScimResource(),
                    GenericScimResource(),
                    GenericScimResource(),
                ),
            )

        assertEquals(3, result.totalResults)
        assertEquals(3, result.resources.size)
        assertEquals(1, result.startIndex)
        assertEquals(3, result.itemsPerPage)
    }

    @Test
    fun `createSearchResult honours pagination when count is zero`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "1",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "0",
                ),
            ).createSearchResult(
                sequenceOf(
                    GenericScimResource(),
                    GenericScimResource(),
                    GenericScimResource(),
                ),
            )

        assertEquals(3, result.totalResults)
        assertEquals(1, result.startIndex)
        assertEquals(0, result.itemsPerPage)
        assertTrue(result.resources.isEmpty())
    }

    @Test
    fun `createSearchResult with high startIndex returns empty page but keeps total`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "10",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "5",
                ),
            ).createSearchResult(
                sequenceOf(
                    GenericScimResource(),
                    GenericScimResource(),
                    GenericScimResource(),
                ),
            )

        assertEquals(3, result.totalResults)
        assertEquals(10, result.startIndex)
        assertEquals(0, result.itemsPerPage)
        assertTrue(result.resources.isEmpty())
    }

    @Test
    fun `createSearchResult returns empty page and zero total when no resources`() {
        val result: ListResponse<GenericScimResource> =
            createHandler(emptyMap()).createSearchResult(emptySequence())

        assertEquals(0, result.totalResults)
        assertEquals(1, result.startIndex)
        assertEquals(0, result.itemsPerPage)
        assertTrue(result.resources.isEmpty())
    }
}
