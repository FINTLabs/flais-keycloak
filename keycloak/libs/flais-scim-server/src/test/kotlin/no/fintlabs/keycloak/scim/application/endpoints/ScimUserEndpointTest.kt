package no.fintlabs.keycloak.scim.application.endpoints

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
import no.fintlabs.keycloak.scim.context.ScimContext
import no.fintlabs.keycloak.scim.endpoints.ScimUserEndpoint
import no.fintlabs.keycloak.scim.resources.UserResource
import no.fintlabs.keycloak.scim.utils.ScimRoles
import no.fintlabs.keycloak.scim.utils.TestUriInfo
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
}
