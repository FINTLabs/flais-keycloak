package no.novari.keycloak.scim.application.endpoints

import com.fasterxml.jackson.databind.JsonNode
import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.messages.PatchOperation
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.utils.JsonUtils
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import no.novari.keycloak.scim.context.ScimContext
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.resources.UserResource
import no.novari.keycloak.scim.types.FintUserExtension
import no.novari.keycloak.scim.utils.ScimRoles
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.models.FederatedIdentityModel
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.RoleModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserProvider
import org.keycloak.organization.OrganizationProvider
import java.net.URI

@ExtendWith(MockKExtension::class)
class ScimUserEndpointTest {
    private val extId = "any-ext-id"
    private val userId = "any-id"
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

    @BeforeEach
    fun setup() {
        every { scimContext.orgProvider } returns orgProvider
        every { scimContext.realm } returns realm

        every { scimContext.session } returns keycloakSession
        every { keycloakSession.users() } returns userProvider

        endpoint = ScimUserEndpoint(scimContext)
    }

    fun templateUser(user: UserModel) {
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true
        every { user.id } returns userId
        every { user.username } returns "alice.basic@telemark.no"
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
    }

    @Test
    fun `getUsers returns only SCIM managed users`() {
        val user2 = mockk<UserModel>(relaxed = true)
        templateUser(user)

        every { user2.hasRole(scimRole) } returns false
        every {
            scimContext.orgProvider.getMembersStream(scimContext.organization, emptyMap(), true, null, null)
        } returns listOf(user, user2).stream()

        val response = endpoint.getUsers(usersUriInfo)
        assertEquals(Response.Status.OK.statusCode, response.status)
        assertTrue(response.entity != null)
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

        val nameNode = node["name"]
        assertEquals("Alice", nameNode.get("givenName").asText())
        assertEquals("Basic", nameNode.get("familyName").asText())

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
    fun `createUser does not crash when user email is null and skips idp linking`() {
        val scimUser =
            UserResource().apply {
                userName = "alice.basic@telemark.no"
                active = true
                externalId = extId
            }

        templateUser(user)
        every { user.email } returns null

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
                        PatchOperation.replace("name.givenName", "new"),
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

        val extensionFields =
            FintUserExtension::class.java.declaredFields
                .filter { !it.isSynthetic }
                .map { it.name }
                .sorted()

        val expected: Map<String, String?> =
            extensionFields
                .associateWith { propName -> "value-for-$propName" }
                .toMutableMap()
                .apply {
                    if (containsKey("employeeId")) this["employeeId"] = ""
                }

        extensionFields.forEach { prop ->
            every { user.getFirstAttribute(prop) } returns expected[prop]
        }

        every { orgProvider.getMemberById(scimContext.organization, userId) } returns user
        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole

        val response = endpoint.getUser(userId, userUriInfo)
        assertEquals(Response.Status.OK.statusCode, response.status)

        val resource = response.entity as GenericScimResource
        val extNode = resource.objectNode["urn:ietf:params:scim:schemas:extension:fint:2.0:User"]
        assertTrue(extNode != null && extNode.isObject)

        extensionFields.forEach { prop ->
            val jsonValue: String? =
                extNode.get(prop)?.takeUnless(JsonNode::isNull)?.asText()

            assertEquals(expected[prop], jsonValue)
        }

        val jsonFields = extNode.fieldNames().asSequence().toSet()
        assertEquals(extensionFields.toSet(), jsonFields)
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

        val extensionFields =
            FintUserExtension::class.java.declaredFields
                .filter { !it.isSynthetic }
                .onEach { it.isAccessible = true }
                .map { it.name }
                .sorted()

        val expected =
            extensionFields
                .associateWith { "value-for-$it" }
                .toMutableMap()
                .apply {
                    if (containsKey("employeeId")) this["employeeId"] = ""
                }

        val ext = FintUserExtension()
        FintUserExtension::class.java.declaredFields
            .filter { !it.isSynthetic }
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

        extensionFields.forEach { attr ->
            verify { user.setSingleAttribute(attr, expected[attr]) }
        }
    }
}
