package no.novari.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.exceptions.BadRequestException
import com.unboundid.scim2.common.messages.ErrorResponse
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.utils.ApiConstants
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import no.novari.keycloak.scim.context.ScimContext
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.search.ScimCursor
import no.novari.keycloak.scim.search.ScimPage
import no.novari.keycloak.scim.search.ScimUserSearch
import no.novari.keycloak.scim.search.ScimUserSearchCriteria
import no.novari.keycloak.scim.search.ScimUserSearchResult
import no.novari.keycloak.scim.utils.ScimRoles
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.RoleModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserProvider
import org.keycloak.organization.OrganizationProvider
import java.net.URI

@ExtendWith(MockKExtension::class)
class ScimUserEndpointSearchTest {
    private companion object {
        const val USERNAME = "alice.basic@telemark.no"
        const val USER_ID = "any-id"
        const val GROUP_ID = "any-org-group-id"
        const val ROLE_ID = "any-scim-role-id"
        const val UNSUPPORTED_FILTER = "attribute 'roles' is a complex SCIM role and cannot be compared in the database"
    }

    @MockK(relaxed = true)
    lateinit var keycloakSession: KeycloakSession

    @MockK(relaxed = true)
    lateinit var scimContext: ScimContext

    @MockK
    lateinit var orgProvider: OrganizationProvider

    @MockK
    lateinit var realm: RealmModel

    @MockK
    lateinit var scimRole: RoleModel

    @MockK(relaxed = true)
    lateinit var user: UserModel

    @MockK(relaxed = true)
    lateinit var userProvider: UserProvider

    lateinit var endpoint: ScimUserEndpoint
    private lateinit var userSearch: RecordingScimUserSearch

    @BeforeEach
    fun setup() {
        userSearch = RecordingScimUserSearch()

        every { scimContext.orgProvider } returns orgProvider
        every { scimContext.realm } returns realm
        every { scimContext.userSearch } returns userSearch
        every { scimContext.session } returns keycloakSession
        every { keycloakSession.users() } returns userProvider

        endpoint = ScimUserEndpoint(scimContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getUsers returns exact totalResults from the database and only the requested page`() {
        stubUser()
        stubSearchPage(totalResults = 57, users = listOf(user))

        val response = endpoint.getUsers(usersUriInfo())

        val result = response.assertListResponse()
        assertEquals(57, result.totalResults)
        assertEquals(1, result.resources.size)
    }

    @Test
    fun `getUsers does not materialize all organization members on the native path`() {
        stubUser()
        stubSearchPage(totalResults = 1, users = listOf(user))

        endpoint.getUsers(usersUriInfo())

        verifyNoOrganizationScan()
    }

    @Test
    fun `getUsers translates startIndex and count into a database offset and limit`() {
        stubUser()
        stubSearchPage(totalResults = 57, users = listOf(user))

        endpoint.getUsers(
            usersUriInfo(
                ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "3",
                ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "2",
            ),
        )

        // startIndex is 1-based in SCIM, firstResult is 0-based in JPA.
        val page = userSearch.criteria.page as ScimPage.Index
        assertEquals(2, page.firstResult)
        assertEquals(2, page.maxResults)
    }

    @Test
    fun `getUsers pushes a supported filter into the database instead of scanning`() {
        stubUser()
        stubSearchPage(totalResults = 1, users = listOf(user))

        val response = endpoint.getUsers(usersUriInfo(filter("""userName eq "$USERNAME"""")))

        val result = response.assertListResponse()
        assertEquals(1, result.totalResults)
        assertEquals(1, result.resources.size)
        assertNotNull(userSearch.criteria.filter)
        verifyNoOrganizationScan()
    }

    @Test
    fun `getUsers pushes a supported sort into the database`() {
        stubUser()
        stubSearchPage(totalResults = 1, users = listOf(user))

        endpoint.getUsers(
            usersUriInfo(
                ApiConstants.QUERY_PARAMETER_SORT_BY to "userName",
                ApiConstants.QUERY_PARAMETER_SORT_ORDER to "descending",
            ),
        )

        assertEquals("userName", userSearch.criteria.sortBy.toString())
        assertEquals(false, userSearch.criteria.sortAscending)
    }

    @Test
    fun `getUsers can start cursor pagination with a blank cursor`() {
        stubUser()
        stubSearchPage(totalResults = 57, users = listOf(user), hasMore = true)

        val response =
            endpoint.getUsers(
                usersUriInfo(
                    ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to "",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
                ),
            )

        val result = response.assertListResponse()
        val page = userSearch.criteria.page as ScimPage.Keyset
        assertNull(page.after)
        assertEquals(1, page.maxResults)
        assertNull(result.startIndex)
        assertEquals(1, result.itemsPerPage)
        assertEquals(ScimCursor(ScimCursor.queryHash(null, GROUP_ID), USER_ID).encode(), result.nextCursor)
    }

    @Test
    fun `getUsers resumes cursor pagination from a matching cursor`() {
        stubUser()
        stubSearchPage(totalResults = 57, users = listOf(user))
        val cursor = ScimCursor(ScimCursor.queryHash(null, GROUP_ID), "previous-user-id").encode()

        endpoint.getUsers(
            usersUriInfo(
                ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor,
                ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
            ),
        )

        val page = userSearch.criteria.page as ScimPage.Keyset
        assertEquals("previous-user-id", page.after)
        assertEquals(1, page.maxResults)
    }

    @Test
    fun `getUsers rejects a cursor from a different query`() {
        stubUser()
        stubOrganizationLookup()
        val cursor = ScimCursor(ScimCursor.queryHash(null, GROUP_ID), "previous-user-id").encode()

        assertThrows<BadRequestException> {
            endpoint.getUsers(
                usersUriInfo(
                    filter("""userName eq "$USERNAME""""),
                    ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor,
                ),
            )
        }
    }

    @Test
    fun `getUsers rejects a cursor from a different organization`() {
        stubUser()
        stubOrganizationLookup()
        val cursor = ScimCursor(ScimCursor.queryHash(null, "another-org-group-id"), "previous-user-id").encode()

        assertThrows<BadRequestException> {
            endpoint.getUsers(
                usersUriInfo(
                    ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor,
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
                ),
            )
        }
    }

    @Test
    fun `getUsers returns 400 for filters that cannot be pushed down`() {
        stubUser()
        stubOrganizationLookup()
        userSearch.result = ScimUserSearchResult.Unsupported(UNSUPPORTED_FILTER)

        val response = endpoint.getUsers(usersUriInfo(filter("""roles.value co "read"""")))

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals(UNSUPPORTED_FILTER, (response.entity as ErrorResponse).detail)
        verifyNoOrganizationScan()
    }

    @Test
    fun `getUsers returns 400 for a sort it cannot push down`() {
        stubUser()
        stubOrganizationLookup()
        userSearch.result = ScimUserSearchResult.Unsupported("roles cannot be sorted in the database")

        val response = endpoint.getUsers(usersUriInfo(ApiConstants.QUERY_PARAMETER_SORT_BY to "roles"))

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals("roles cannot be sorted in the database", (response.entity as ErrorResponse).detail)
        verifyNoOrganizationScan()
    }

    private fun stubUser() {
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every { user.id } returns USER_ID
        every { user.username } returns USERNAME
        every { user.isEnabled } returns true
        every { user.email } returns USERNAME
        every { user.firstName } returns "Alice"
        every { user.lastName } returns "Basic"
        every { user.getAttributeStream("rawRoles") } answers {
            listOf(
                "{\"value\":\"read\",\"display\":\"read\",\"type\":\"WindowsAzureActiveDirectoryRole\",\"primary\":false}",
            ).stream()
        }
        every { user.getFirstAttribute("externalId") } returns "any-ext-id"
        every { user.getFirstAttribute("userPrincipalName") } returns USERNAME
    }

    private fun stubOrganizationLookup() {
        val group = mockk<GroupModel> { every { id } returns GROUP_ID }
        every { orgProvider.getOrganizationGroup(scimContext.organization) } returns group
        every { scimRole.id } returns ROLE_ID
    }

    private fun stubSearchPage(
        totalResults: Int,
        users: List<UserModel>,
        hasMore: Boolean = false,
    ) {
        stubOrganizationLookup()
        userSearch.result = ScimUserSearchResult.Page(users, totalResults, hasMore)
    }

    private fun usersUriInfo(vararg params: Pair<String, String>): UriInfo {
        val query = MultivaluedHashMap<String, String>()
        params.forEach { (key, value) -> query.add(key, value) }
        return TestUriInfo(URI("http://localhost/scim/v2/Users"), query)
    }

    private fun filter(expression: String) = ApiConstants.QUERY_PARAMETER_FILTER to expression

    private fun jakarta.ws.rs.core.Response.assertListResponse(): ListResponse<*> {
        assertEquals(Response.Status.OK.statusCode, status)
        return entity as ListResponse<*>
    }

    private fun verifyNoOrganizationScan() {
        verify(exactly = 0) {
            orgProvider.getMembersStream(any(), any<Map<String, String>>(), any(), any(), any())
        }
    }

    private class RecordingScimUserSearch : ScimUserSearch {
        lateinit var criteria: ScimUserSearchCriteria
        var result: ScimUserSearchResult = ScimUserSearchResult.Unsupported("not stubbed")

        override fun search(criteria: ScimUserSearchCriteria): ScimUserSearchResult {
            this.criteria = criteria
            return result
        }
    }
}
