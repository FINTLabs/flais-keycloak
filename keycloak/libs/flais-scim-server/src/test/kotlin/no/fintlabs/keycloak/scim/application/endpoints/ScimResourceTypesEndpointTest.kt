package no.fintlabs.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.messages.ListResponse
import no.fintlabs.keycloak.scim.endpoints.ScimResourceTypesEndpoint
import no.fintlabs.keycloak.scim.endpoints.ScimUserEndpoint
import no.fintlabs.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import java.net.URI

class ScimResourceTypesEndpointTest {
    private var uriInfo = TestUriInfo(URI("http://localhost/scim/v2/ResourceTypes"))
    private val endpoint = ScimResourceTypesEndpoint(listOf(ScimUserEndpoint::class))

    @Test
    fun `search throws ForbiddenException when filter is provided`() {
        assertThrows<ForbiddenException> {
            endpoint.search(uriInfo, "name eq \"User\"")
        }
    }

    @Test
    fun `search returns resource types when no filter`() {
        val response: ListResponse<ScimResource> = endpoint.search(uriInfo, null)

        val userResourceType =
            response.resources
                .filterIsInstance<GenericScimResource>()
                .firstOrNull { it.objectNode["name"].asText() == "User" }

        assertNotNull(userResourceType)
    }

    @Test
    fun `get returns resource type when requested by id`() {
        val resourceTypes = endpoint.getResourceTypes()
        assertFalse(resourceTypes.isEmpty())

        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val resource: GenericScimResource = endpoint.get(id, uriInfo)
        val node = resource.objectNode

        assertEquals(id, node["id"].asText())
        assertEquals(name, node["name"].asText())
    }

    @Test
    fun `get returns resource type when requested by name`() {
        val resourceTypes = endpoint.getResourceTypes()
        assertFalse(resourceTypes.isEmpty())

        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val resource: GenericScimResource = endpoint.get(name, uriInfo)
        val node = resource.objectNode

        assertEquals(id, node["id"].asText())
        assertEquals(name, node["name"].asText())
    }

    @Test
    fun `get throws ResourceNotFoundException when resource type does not exist`() {
        assertThrows<ResourceNotFoundException> {
            endpoint.get("not-a-real-resource-type", uriInfo)
        }
    }
}
