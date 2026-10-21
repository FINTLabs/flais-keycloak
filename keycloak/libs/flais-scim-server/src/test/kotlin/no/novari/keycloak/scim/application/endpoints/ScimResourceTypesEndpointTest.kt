package no.novari.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.messages.ListResponse
import io.mockk.every
import io.mockk.mockk
import no.novari.keycloak.scim.context.ScimContext
import no.novari.keycloak.scim.endpoints.ScimResourceTypesEndpoint
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import java.net.URI

class ScimResourceTypesEndpointTest {
    private val scimContext = mockk<ScimContext>(relaxed = true)
    private var uriInfo = TestUriInfo(URI("http://localhost/realms/test/scim/v2/test-org/ResourceTypes"))
    private val endpoint = ScimResourceTypesEndpoint(scimContext, listOf(ScimUserEndpoint::class))

    init {
        every { scimContext.uriInfo } returns uriInfo
    }

    @Test
    fun `search throws ForbiddenException when filter is provided`() {
        assertThrows<ForbiddenException> {
            endpoint.search("name eq \"User\"")
        }
    }

    @Test
    fun `search returns resource types when no filter`() {
        val response: ListResponse<ScimResource> = endpoint.search(null)

        val userResourceType =
            response.resources
                .filterIsInstance<GenericScimResource>()
                .firstOrNull { it.objectNode["name"].asText() == "User" }

        assertNotNull(userResourceType)
    }

    @Test
    fun `get returns resource type when requested by id`() {
        val resourceTypes = endpoint.getResourceTypes()
        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val node = endpoint.get(id).objectNode

        assertEquals(id, node["id"].asText())
        assertEquals(name, node["name"].asText())
    }

    @Test
    fun `get returns resource type when requested by name`() {
        val resourceTypes = endpoint.getResourceTypes()
        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val node = endpoint.get(name).objectNode

        assertEquals(id, node["id"].asText())
        assertEquals(name, node["name"].asText())
    }

    @Test
    fun `get throws ResourceNotFoundException when resource type does not exist`() {
        assertThrows<ResourceNotFoundException> {
            endpoint.get("not-a-real-resource-type")
        }
    }
}
