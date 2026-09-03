package no.novari.keycloak.scim.application.endpoints

import com.fasterxml.jackson.databind.JsonNode
import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.exceptions.BadRequestException
import com.unboundid.scim2.common.messages.ListResponse
import com.unboundid.scim2.common.messages.PatchOperation
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.common.utils.JsonUtils
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import no.novari.keycloak.scim.context.ScimContext
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.resources.UserResource
import no.novari.keycloak.scim.store.ScimCursor
import no.novari.keycloak.scim.store.ScimPage
import no.novari.keycloak.scim.store.ScimUserSearch
import no.novari.keycloak.scim.store.ScimUserSearchCriteria
import no.novari.keycloak.scim.store.ScimUserSearchResult
import no.novari.keycloak.scim.types.FintUserExtension
import no.novari.keycloak.scim.utils.ScimRoles
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.models.FederatedIdentityModel
import org.keycloak.models.GroupModel
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.RoleModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserProvider
import org.keycloak.organization.OrganizationProvider
import java.net.URI
import java.util.stream.Stream

@ExtendWith(MockKExtension::class)
class ScimUserEndpointTest {
    private companion object {
        const val USERNAME = "alice.basic@telemark.no"
    }

    private val extId = "any-ext-id"
    private val userId = "any-id"
    private val groupId = "any-org-group-id"
    private val roleId = "any-scim-role-id"
    private val userUriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users/$userId"))
    private val usersUriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users"))

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
    private lateinit var nativeUserSearch: RecordingScimUserSearch

    @BeforeEach
    fun setup() {
        nativeUserSearch = RecordingScimUserSearch()
        every { scimContext.orgProvider } returns orgProvider
        every { scimContext.realm } returns realm
        every { scimContext.userSearch } returns nativeUserSearch

        every { scimContext.session } returns keycloakSession
        every { keycloakSession.users() } returns userProvider

        endpoint = ScimUserEndpoint(scimContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun usersUriInfoWith(params: Map<String, String>): UriInfo {
        val query = MultivaluedHashMap<String, String>()
        params.forEach { (k, v) -> query.add(k, v) }
        return TestUriInfo(URI("http://localhost/scim/v2/Users"), query)
    }

    private fun filter(expression: String) = mapOf(ApiConstants.QUERY_PARAMETER_FILTER to expression)

    /** Stubs what the native path needs to reach the database, without stubbing the query itself. */
    private fun stubOrganizationLookup() {
        val group = mockk<GroupModel> { every { id } returns groupId }
        every { orgProvider.getOrganizationGroup(scimContext.organization) } returns group
        every { scimRole.id } returns roleId
    }

    private fun stubNativeQuery(
        totalResults: Int,
        users: List<UserModel>,
        hasMore: Boolean = false,
    ) {
        stubOrganizationLookup()
        nativeUserSearch.result = ScimUserSearchResult.Page(users, totalResults, hasMore)
    }

    private class RecordingScimUserSearch : ScimUserSearch {
        lateinit var criteria: ScimUserSearchCriteria
        var result: ScimUserSearchResult = ScimUserSearchResult.Unsupported("not stubbed")

        override fun search(criteria: ScimUserSearchCriteria): ScimUserSearchResult {
            this.criteria = criteria
            return result
        }
    }

    fun templateUser(user: UserModel) {
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every { user.id } returns userId
        every { user.username } returns USERNAME
        every { user.isEnabled } returns true
        every { user.email } returns "alice.basic@telemark.no"
        every { user.firstName } returns "Alice"
        every { user.lastName } returns "Basic"
        every { user.getAttributeStream("rawRoles") } answers {
            listOf(
                "{\"value\":\"read\",\"display\":\"read\",\"type\":\"WindowsAzureActiveDirectoryRole\",\"primary\":false}",
            ).stream()
        }
        every { user.getFirstAttribute("externalId") } returns extId
        every { user.getFirstAttribute("userPrincipalName") } returns "alice.basic@telemark.no"
    }

    private fun identityProvider(
        alias: String,
        domain: String,
    ): IdentityProviderModel =
        mockk {
            every { this@mockk.alias } returns alias
            every { config } returns mapOf("kc.org.domain" to domain)
        }

    private fun createUserWithLinking(
        userPrincipalName: String?,
        idps: List<IdentityProviderModel>,
        email: String? = "alice.basic@telemark.no",
    ): Response {
        val scimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId
                setExtension(
                    FintUserExtension().apply {
                        this.userPrincipalName = userPrincipalName
                    },
                )
            }

        templateUser(user)
        every { user.email } returns email
        every { user.getFirstAttribute("userPrincipalName") } returns userPrincipalName
        every { userProvider.getUserById(realm, scimUser.userName) } returns null
        every { userProvider.addUser(realm, scimUser.userName) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.addManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } answers { idps.stream() }
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns Stream.empty()
        every { userProvider.getFederatedIdentity(realm, user, any()) } returns null

        return endpoint.createUser(usersUriInfo, scimUser)
    }

    @Test
    fun `getUsers returns exact totalResults from the database and only the requested page`() {
        templateUser(user)
        stubNativeQuery(totalResults = 57, users = listOf(user))

        val response = endpoint.getUsers(usersUriInfo)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val result = response.entity as ListResponse<*>
        assertEquals(57, result.totalResults)
        assertEquals(1, result.resources.size)
    }

    @Test
    fun `getUsers does not materialize all organization members on the native path`() {
        templateUser(user)
        stubNativeQuery(totalResults = 1, users = listOf(user))

        endpoint.getUsers(usersUriInfo)

        verify(exactly = 0) {
            orgProvider.getMembersStream(any(), any<Map<String, String>>(), any(), any(), any())
        }
    }

    @Test
    fun `getUsers translates startIndex and count into a database offset and limit`() {
        templateUser(user)
        stubNativeQuery(totalResults = 57, users = listOf(user))

        endpoint.getUsers(
            usersUriInfoWith(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX to "3",
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "2",
                ),
            ),
        )

        // startIndex is 1-based in SCIM, firstResult is 0-based in JPA.
        val page = nativeUserSearch.criteria.page as ScimPage.Index
        assertEquals(2, page.firstResult)
        assertEquals(2, page.maxResults)
    }

    @Test
    fun `getUsers pushes a supported filter into the database instead of scanning`() {
        templateUser(user)
        stubNativeQuery(totalResults = 1, users = listOf(user))

        val response = endpoint.getUsers(usersUriInfoWith(filter("""userName eq "$USERNAME"""")))

        val result = response.entity as ListResponse<*>
        assertEquals(1, result.totalResults)
        assertEquals(1, result.resources.size)
        assertNotNull(nativeUserSearch.criteria.filter)
        verify(exactly = 0) {
            orgProvider.getMembersStream(any(), any<Map<String, String>>(), any(), any(), any())
        }
    }

    @Test
    fun `getUsers pushes a supported sort into the database`() {
        templateUser(user)
        stubNativeQuery(totalResults = 1, users = listOf(user))

        endpoint.getUsers(
            usersUriInfoWith(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_SORT_BY to "userName",
                    ApiConstants.QUERY_PARAMETER_SORT_ORDER to "descending",
                ),
            ),
        )

        assertEquals("userName", nativeUserSearch.criteria.sortBy.toString())
        assertEquals(false, nativeUserSearch.criteria.sortAscending)
    }

    @Test
    fun `getUsers can start cursor pagination with a blank cursor`() {
        templateUser(user)
        stubNativeQuery(totalResults = 57, users = listOf(user), hasMore = true)

        val response =
            endpoint.getUsers(
                usersUriInfoWith(
                    mapOf(
                        ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to "",
                        ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
                    ),
                ),
            )

        val result = response.entity as ListResponse<*>
        val page = nativeUserSearch.criteria.page as ScimPage.Keyset
        assertNull(page.after)
        assertEquals(1, page.maxResults)
        assertNull(result.startIndex)
        assertEquals(1, result.itemsPerPage)
        assertEquals(
            ScimCursor(ScimCursor.queryHash(null, groupId), userId).encode(),
            result.nextCursor,
        )
    }

    @Test
    fun `getUsers resumes cursor pagination from a matching cursor`() {
        templateUser(user)
        stubNativeQuery(totalResults = 57, users = listOf(user))
        val cursor = ScimCursor(ScimCursor.queryHash(null, groupId), "previous-user-id").encode()

        endpoint.getUsers(
            usersUriInfoWith(
                mapOf(
                    ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor,
                    ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
                ),
            ),
        )

        val page = nativeUserSearch.criteria.page as ScimPage.Keyset
        assertEquals("previous-user-id", page.after)
        assertEquals(1, page.maxResults)
    }

    @Test
    fun `getUsers rejects a cursor from a different query`() {
        templateUser(user)
        stubOrganizationLookup()
        val cursor = ScimCursor(ScimCursor.queryHash(null, groupId), "previous-user-id").encode()

        assertThrows<BadRequestException> {
            endpoint.getUsers(
                usersUriInfoWith(
                    filter("""userName eq "$USERNAME"""") +
                        (ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor),
                ),
            )
        }
    }

    @Test
    fun `getUsers rejects a cursor from a different organization`() {
        templateUser(user)
        stubOrganizationLookup()
        val cursor = ScimCursor(ScimCursor.queryHash(null, "another-org-group-id"), "previous-user-id").encode()

        assertThrows<BadRequestException> {
            endpoint.getUsers(
                usersUriInfoWith(
                    mapOf(
                        ApiConstants.QUERY_PARAMETER_PAGE_CURSOR to cursor,
                        ApiConstants.QUERY_PARAMETER_PAGE_SIZE to "1",
                    ),
                ),
            )
        }
    }

    @Test
    fun `getUsers falls back to in-memory filtering for filters that cannot be pushed down`() {
        val user2 = mockk<UserModel>(relaxed = true)
        templateUser(user)
        stubOrganizationLookup()
        nativeUserSearch.result = ScimUserSearchResult.Unsupported("attribute 'roles.value' may be stored in LONG_VALUE")
        every { user2.hasRole(scimRole) } returns false
        every {
            orgProvider.getMembersStream(scimContext.organization, emptyMap(), true, null, null)
        } returns listOf(user, user2).stream()

        // User attribute values can be stored in LONG_VALUE, so text comparisons fall back to the
        // SDK evaluator. The value is one templateUser actually has, so a passing assertion means the
        // fallback really evaluated the filter rather than short-circuiting.
        val response =
            endpoint.getUsers(
                usersUriInfoWith(filter("""roles.value co "read"""")),
            )

        // Only the scim-managed member is counted; user2 is excluded.
        assertEquals(1, (response.entity as ListResponse<*>).totalResults)
    }

    @Test
    fun `getUsers falls back to in-memory filtering for a sort it cannot push down`() {
        templateUser(user)
        stubOrganizationLookup()
        nativeUserSearch.result = ScimUserSearchResult.Unsupported("roles cannot be sorted in the database")
        val organization = scimContext.organization
        every {
            orgProvider.getMembersStream(organization, emptyMap(), true, null, null)
        } returns listOf(user).stream()

        // roles is multivalued, so ordering by it has no single well-defined key.
        endpoint.getUsers(usersUriInfoWith(mapOf(ApiConstants.QUERY_PARAMETER_SORT_BY to "roles")))

        // Sorting spans the whole result set, so it has to be pushed down with the filter or not at all.
        verify(exactly = 1) {
            orgProvider.getMembersStream(organization, emptyMap(), true, null, null)
        }
    }

    @Test
    fun `getUser returns 404 when user does not exist`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns null

        assertEquals(Response.Status.NOT_FOUND.statusCode, endpoint.getUser(userId, userUriInfo).status)
    }

    @Test
    fun `getUser returns 200 and translated user when exists and valid`() {
        templateUser(user)
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole

        val response = endpoint.getUser(userId, userUriInfo)

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertTrue(response.entity is GenericScimResource)

        val resource = response.entity as GenericScimResource
        val node = resource.objectNode

        assertEquals(userId, node["id"].asText())
        assertEquals("alice.basic@telemark.no", node["userName"].asText())
        assertEquals(true, node["active"].asBoolean())
        assertEquals(extId, node["externalId"].asText())

        val emailNode = node["emails"].get(0)
        assertEquals("alice.basic@telemark.no", emailNode.get("value").asText())
        assertEquals(true, emailNode.get("primary").asBoolean())

        val extNode = node["urn:ietf:params:scim:schemas:extension:fint:2.0:User"]
        assertTrue(extNode != null && extNode.isObject)

        assertEquals("Alice", extNode["givenName"].asText())
        assertEquals("Basic", extNode["familyName"].asText())

        val roleNode = node["roles"]
        assertEquals(
            """[{"value":"read","display":"read","type":"WindowsAzureActiveDirectoryRole","primary":false}]""",
            roleNode.toString(),
        )
    }

    @Test
    fun `createUser creates user and returns 201`() {
        val scimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId
            }

        templateUser(user)

        every {
            userProvider.getUserById(realm, scimUser.userName)
        } returns null
        every {
            userProvider.addUser(realm, scimUser.userName)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.addManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()

        val response = endpoint.createUser(usersUriInfo, scimUser)

        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertTrue(response.location.toString().contains(userId))

        verify(exactly = 1) { user.grantRole(scimRole) }
        verify { orgProvider.addManagedMember(scimContext.organization, user) }

        verify { user.username = scimUser.userName }
        verify { user.isEnabled = scimUser.active!! }
        verify { user.setSingleAttribute("externalId", scimUser.externalId) }
    }

    @Test
    fun `createUser does not crash when userPrincipalName is null and skips idp linking`() {
        val scimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId
            }

        templateUser(user)
        every { user.getFirstAttribute("userPrincipalName") } returns null

        every { userProvider.getUserById(realm, scimUser.userName) } returns null
        every { userProvider.addUser(realm, scimUser.userName) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.addManagedMember(scimContext.organization, user) } returns true

        val response = endpoint.createUser(usersUriInfo, scimUser)

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 0) { orgProvider.getIdentityProviders(any()) }
        verify(exactly = 0) { userProvider.getFederatedIdentity(any(), any(), any()) }
        verify(exactly = 0) { userProvider.addFederatedIdentity(any(), any(), any()) }
    }

    @Test
    fun `createUser links idp using userPrincipalName domain instead of email domain`() {
        val response =
            createUserWithLinking(
                userPrincipalName = "alice.basic@telemark.no",
                idps =
                    listOf(
                        identityProvider("email-domain-idp", "wrong.no"),
                        identityProvider("upn-domain-idp", "TELEMARK.NO"),
                    ),
                email = "alice.basic@wrong.no",
            )

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 1) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "upn-domain-idp" && it.userId == extId },
            )
        }
        verify(exactly = 0) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "email-domain-idp" },
            )
        }
    }

    @Test
    fun `createUser links only idp matching non Telemark userPrincipalName domain`() {
        val response =
            createUserWithLinking(
                userPrincipalName = "alice.basic@rogaland.no",
                idps =
                    listOf(
                        identityProvider("telemark-idp", "telemark.no"),
                        identityProvider("rogaland-idp", "rogaland.no"),
                        identityProvider("novari-idp", "novari.no"),
                    ),
            )

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 1) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "rogaland-idp" && it.userId == extId },
            )
        }
        verify(exactly = 0) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "telemark-idp" || it.identityProvider == "novari-idp" },
            )
        }
    }

    @Test
    fun `createUser links idp using Microsoft external userPrincipalName domain`() {
        val response =
            createUserWithLinking(
                userPrincipalName = "john.doe_example.com#EXT#@tenant.onmicrosoft.com",
                idps =
                    listOf(
                        identityProvider("source-domain-idp", "example.com"),
                        identityProvider("tenant-idp", "*.onmicrosoft.com"),
                    ),
                email = "john.doe@example.com",
            )

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 1) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "tenant-idp" && it.userId == extId },
            )
        }
        verify(exactly = 0) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "source-domain-idp" },
            )
        }
    }

    @Test
    fun `createUser links any domain idp for valid userPrincipalName domain`() {
        val response =
            createUserWithLinking(
                userPrincipalName = "alice.basic@whatever.no",
                idps = listOf(identityProvider("any-idp", " aNy ")),
            )

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 1) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider == "any-idp" && it.userId == extId },
            )
        }
    }

    @Test
    fun `createUser links wildcard idp for base and nested userPrincipalName domains`() {
        listOf(
            "alice.basic@test.com",
            "alice.basic@whatever.test.com",
            "alice.basic@a.b.test.com",
        ).forEach { userPrincipalName ->
            createUserWithLinking(
                userPrincipalName = userPrincipalName,
                idps = listOf(identityProvider("wildcard-idp-$userPrincipalName", "*.TEST.COM")),
            )
        }

        verify(exactly = 3) {
            userProvider.addFederatedIdentity(
                realm,
                user,
                match { it.identityProvider.startsWith("wildcard-idp-") },
            )
        }
    }

    @Test
    fun `createUser does not link non matching exact or wildcard idp domains`() {
        val response =
            createUserWithLinking(
                userPrincipalName = "alice.basic@other.com",
                idps =
                    listOf(
                        identityProvider("exact-idp", "test.com"),
                        identityProvider("wildcard-idp", "*.test.com"),
                    ),
            )

        assertEquals(Response.Status.CREATED.statusCode, response.status)

        verify(exactly = 0) {
            userProvider.addFederatedIdentity(realm, user, any())
        }
    }

    @Test
    fun `createUser ignores malformed userPrincipalName and idp domain configs without crashing`() {
        val invalidUpnResponse =
            createUserWithLinking(
                userPrincipalName = "alice.basic@invalid domain",
                idps = listOf(identityProvider("any-idp", "ANY")),
            )

        val invalidConfigResponse =
            createUserWithLinking(
                userPrincipalName = "alice.basic@test.com",
                idps = listOf(identityProvider("invalid-domain-idp", "invalid domain")),
            )

        assertEquals(Response.Status.CREATED.statusCode, invalidUpnResponse.status)
        assertEquals(Response.Status.CREATED.statusCode, invalidConfigResponse.status)

        verify(exactly = 0) {
            userProvider.addFederatedIdentity(realm, user, any())
        }
    }

    @Test
    fun `updateUser throws NotFoundException when member lookup fails`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } throws RuntimeException()

        assertThrows<NotFoundException> {
            endpoint.updateUser(userUriInfo, userId, UserResource())
        }
    }

    @Test
    fun `updateUser throws ForbiddenException when user is not org managed`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every {
            orgProvider.isManagedMember(scimContext.organization, user)
        } returns false

        assertThrows<ForbiddenException> {
            endpoint.updateUser(userUriInfo, userId, UserResource())
        }
    }

    @Test
    fun `deleteUser throws NotFoundException when member lookup fails`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } throws NotFoundException()

        assertThrows<NotFoundException> {
            endpoint.deleteUser(userId)
        }
    }

    @Test
    fun `deleteUser throws ForbiddenException when user is not scim managed`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns false

        val ex =
            assertThrows<ForbiddenException> {
                endpoint.deleteUser(userId)
            }
        assertTrue(ex.message!!.contains("User is not SCIM-Managed"))
    }

    @Test
    fun `deleteUser throws NotFoundException when user is not part of organization`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every {
            orgProvider.isManagedMember(scimContext.organization, user)
        } returns false

        val ex =
            assertThrows<NotFoundException> {
                endpoint.deleteUser(userId)
            }
        assertTrue(ex.message!!.contains("User is not part of the organization"))

        verify(exactly = 0) {
            orgProvider.removeMember(any(), any())
        }
    }

    @Test
    fun `deleteUser removes member and returns 204 when user is scim and org managed`() {
        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every {
            orgProvider.isManagedMember(scimContext.organization, user)
        } returns true
        every {
            orgProvider.removeMember(any(), user)
        } returns true

        assertEquals(Response.Status.NO_CONTENT.statusCode, endpoint.deleteUser(userId).status)

        verify(exactly = 1) {
            orgProvider.removeMember(any(), user)
        }
    }

    @Test
    fun `patchUser applies patch and returns 200`() {
        templateUser(user)

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true
        every {
            orgProvider.getIdentityProviders(scimContext.organization)
        } returns emptyList<IdentityProviderModel>().stream()
        every {
            userProvider.getFederatedIdentitiesStream(realm, user)
        } returns emptyList<FederatedIdentityModel>().stream()

        val response =
            endpoint.patchUser(
                userUriInfo,
                userId,
                PatchRequest(
                    listOf(
                        PatchOperation.replace(
                            "urn:ietf:params:scim:schemas:extension:fint:2.0:User:givenName",
                            "new",
                        ),
                        PatchOperation.replace(
                            "roles",
                            JsonUtils.getObjectReader().readTree(
                                """
                                [
                                  {"value":"admin","display":"admin","type":"WindowsAzureActiveDirectoryRole","primary":false},
                                  {"value":"manager","display":"manager","type":"WindowsAzureActiveDirectoryRole","primary":false}
                                ]
                                """.trimIndent(),
                            ),
                        ),
                    ),
                ),
            )

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify { user.firstName = "new" }

        val rolesSlot = slot<List<String>>()
        verify {
            user.removeAttribute("roles")
            user.setAttribute("roles", capture(rolesSlot))
        }
        assertEquals(listOf("admin", "manager"), rolesSlot.captured)
    }

    @Test
    fun `patchUser REMOVE extension attributes removes keycloak extension attributes and returns 200`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns
            emptyList<FederatedIdentityModel>().stream()

        val response =
            endpoint.patchUser(
                userUriInfo,
                userId,
                PatchRequest(
                    listOf(
                        PatchOperation.remove("urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId"),
                    ),
                ),
            )

        assertEquals(Response.Status.OK.statusCode, response.status)

        verify(exactly = 1) { user.removeAttribute("employeeId") }
        verify(exactly = 0) { user.setAttribute(eq("employeeId"), any<List<String>>()) }
    }

    @Test
    fun `patchUser REMOVE extension givenName clears keycloak first name and returns 200`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns
            emptyList<FederatedIdentityModel>().stream()

        val response =
            endpoint.patchUser(
                userUriInfo,
                userId,
                PatchRequest(
                    listOf(
                        PatchOperation.remove(
                            "urn:ietf:params:scim:schemas:extension:fint:2.0:User:givenName",
                        ),
                    ),
                ),
            )

        assertEquals(Response.Status.OK.statusCode, response.status)

        verify { user.firstName = null }
        verify(exactly = 0) { user.removeAttribute("givenName") }
    }

    @Test
    fun `patchUser REMOVE extension familyName clears keycloak last name and returns 200`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns
            emptyList<FederatedIdentityModel>().stream()

        val response =
            endpoint.patchUser(
                userUriInfo,
                userId,
                PatchRequest(
                    listOf(
                        PatchOperation.remove(
                            "urn:ietf:params:scim:schemas:extension:fint:2.0:User:familyName",
                        ),
                    ),
                ),
            )

        assertEquals(Response.Status.OK.statusCode, response.status)

        verify { user.lastName = null }
        verify(exactly = 0) { user.removeAttribute("familyName") }
    }

    @Test
    fun `updateUser clears first and last name when fint extension is null`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true

        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns
            emptyList<FederatedIdentityModel>().stream()

        val updatedScimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId
            }

        val response = endpoint.updateUser(userUriInfo, userId, updatedScimUser)

        assertEquals(Response.Status.OK.statusCode, response.status)

        verify { user.firstName = null }
    }

    @Test
    fun `patchUser REMOVE roles removes keycloak roles attribute and returns 200`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true
        every { orgProvider.getIdentityProviders(scimContext.organization) } returns
            emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns
            emptyList<FederatedIdentityModel>().stream()

        val response =
            endpoint.patchUser(
                userUriInfo,
                userId,
                PatchRequest(
                    listOf(
                        PatchOperation.remove("roles"),
                    ),
                ),
            )

        assertEquals(Response.Status.OK.statusCode, response.status)

        verify(exactly = 1) { user.removeAttribute("roles") }
        verify(exactly = 0) { user.setAttribute(eq("roles"), any<List<String>>()) }
    }

    @Test
    fun `getUser returns all attributes from fint extension`() {
        templateUser(user)

        val stringExtensionFields =
            FintUserExtension::class.java.declaredFields
                .filter { !it.isSynthetic }
                .filter { it.name !in setOf("givenName", "familyName") }
                .map { it.name }
                .sorted()

        val expected: Map<String, String?> =
            stringExtensionFields
                .associateWith { propName -> "value-for-$propName" }
                .toMutableMap()
                .apply {
                    if (containsKey("employeeId")) this["employeeId"] = ""
                }

        stringExtensionFields.forEach { prop ->
            every { user.getFirstAttribute(prop) } returns expected[prop]
        }

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole

        val response = endpoint.getUser(userId, userUriInfo)
        assertEquals(Response.Status.OK.statusCode, response.status)

        val resource = response.entity as GenericScimResource
        val extNode = resource.objectNode["urn:ietf:params:scim:schemas:extension:fint:2.0:User"]
        assertTrue(extNode != null && extNode.isObject)

        stringExtensionFields.forEach { prop ->
            val jsonValue: String? =
                extNode.get(prop)?.takeUnless(JsonNode::isNull)?.asText()

            assertEquals(expected[prop], jsonValue)
        }

        assertEquals("Alice", extNode["givenName"].asText())
        assertEquals("Basic", extNode["familyName"].asText())

        val jsonFields = extNode.fieldNames().asSequence().toSet()
        assertEquals((stringExtensionFields + listOf("givenName", "familyName")).toSet(), jsonFields)
    }

    @Test
    fun `updateUser maps all fint extension attributes to keycloak user attributes`() {
        templateUser(user)

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every { orgProvider.isManagedMember(scimContext.organization, user) } returns true

        every { orgProvider.getIdentityProviders(scimContext.organization) } returns emptyList<IdentityProviderModel>().stream()
        every { userProvider.getFederatedIdentitiesStream(realm, user) } returns emptyList<FederatedIdentityModel>().stream()

        val stringExtensionFields =
            FintUserExtension::class.java.declaredFields
                .asSequence()
                .filter { !it.isSynthetic }
                .filter { it.name !in setOf("givenName", "familyName") }
                .onEach { it.isAccessible = true }
                .map { it.name }
                .sorted()
                .toList()

        val expected =
            stringExtensionFields
                .associateWith { "value-for-$it" }
                .toMutableMap()
                .apply {
                    if (containsKey("employeeId")) this["employeeId"] = ""
                }

        val ext =
            FintUserExtension().apply {
                givenName = "Updated"
                familyName = "Name"
            }

        FintUserExtension::class.java.declaredFields
            .filter { !it.isSynthetic }
            .filter { it.name !in setOf("givenName", "familyName") }
            .onEach { it.isAccessible = true }
            .forEach { field ->
                field.set(ext, expected[field.name])
            }

        val updatedScimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId

                setExtension(ext)
            }

        val response = endpoint.updateUser(userUriInfo, userId, updatedScimUser)
        assertEquals(Response.Status.OK.statusCode, response.status)

        stringExtensionFields.forEach { attr ->
            verify { user.setSingleAttribute(attr, expected[attr]) }
        }

        verify { user.firstName = "Updated" }
        verify { user.lastName = "Name" }
        verify(exactly = 0) { user.setSingleAttribute(eq("givenName"), any()) }
        verify(exactly = 0) { user.setSingleAttribute(eq("familyName"), any()) }
    }
}
