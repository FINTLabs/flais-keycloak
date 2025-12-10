package no.fintlabs.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.GenericScimResource
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
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
import org.keycloak.models.RealmModel
import org.keycloak.models.RoleModel
import org.keycloak.models.UserModel
import org.keycloak.organization.OrganizationProvider
import java.net.URI

@ExtendWith(MockKExtension::class)
class ScimUserEndpointTest {
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

    lateinit var endpoint: ScimUserEndpoint

    @BeforeEach
    fun setup() {
        every { scimContext.orgProvider } returns orgProvider
        every { scimContext.realm } returns realm

        endpoint = ScimUserEndpoint(scimContext)
    }

    @Test
    fun `getUser returns 404 when user does not exist`() {
        val userId = "wrong-id"
        val uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users/$userId"))

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns null

        val response = endpoint.getUser(userId, uriInfo)

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }

    @Test
    fun `getUser returns 200 and translated user when exists and valid`() {
        val userId = "user-123"
        val uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users/$userId"))

        every { user.id } returns userId
        every { user.username } returns "alice.basic@telemark.no"
        every { user.isEnabled } returns true
        every { user.email } returns "alice.basic@telemark.no"
        every { user.firstName } returns "Alice"
        every { user.lastName } returns "Basic"
        every { user.getAttributeStream("rawRoles") } returns
            listOf(
                "{\"value\":\"read\",\"display\":\"read\",\"type\":\"WindowsAzureActiveDirectoryRole\",\"primary\":false}",
            ).stream()
        every { user.getFirstAttribute("externalId") } returns "external-123"

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user

        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true

        val response = endpoint.getUser(userId, uriInfo)

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertTrue(response.entity is GenericScimResource)

        val resource = response.entity as GenericScimResource
        val node = resource.objectNode

        assertEquals(userId, node["id"].asText())
        assertEquals("alice.basic@telemark.no", node["userName"].asText())
        assertEquals(true, node["active"].asBoolean())
        assertEquals("external-123", node["externalId"].asText())

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
    fun `updateUser throws NotFoundException when member lookup fails`() {
        val userId = "wrong-id"
        val uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users/$userId"))

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } throws RuntimeException()

        assertThrows<NotFoundException> {
            endpoint.updateUser(uriInfo, userId, UserResource())
        }
    }

    @Test
    fun `updateUser throws ForbiddenException when user is not org managed`() {
        val userId = "user-2"
        val uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Users/$userId"))

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } returns user

        every { realm.getRole(ScimRoles.SCIM_MANAGED_ROLE) } returns scimRole
        every { user.hasRole(scimRole) } returns true

        every {
            orgProvider.isManagedMember(scimContext.organization, user)
        } returns false

        assertThrows<ForbiddenException> {
            endpoint.updateUser(uriInfo, userId, UserResource())
        }
    }

    @Test
    fun `deleteUser throws NotFoundException when member lookup fails`() {
        val userId = "wrong-id"

        every {
            orgProvider.getMemberById(scimContext.organization, userId)
        } throws NotFoundException()

        assertThrows<NotFoundException> {
            endpoint.deleteUser(userId)
        }
    }

    @Test
    fun `deleteUser throws NotFoundException when user is not part of organization`() {
        val userId = "user-not-in-org"

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
        val userId = "user-ok"

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

        val response = endpoint.deleteUser(userId)

        assertEquals(Response.Status.NO_CONTENT.statusCode, response.status)

        verify(exactly = 1) {
            orgProvider.removeMember(any(), user)
        }
    }
}
