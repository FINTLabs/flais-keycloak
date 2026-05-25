package no.novari.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.utils.JsonUtils
import jakarta.ws.rs.core.Response
import no.novari.keycloak.scim.endpoints.ScimResourceTypesEndpoint
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import java.net.URI

class ScimResourceTypesEndpointTest {
    private var uriInfo = TestUriInfo(URI("http://localhost/scim/v2/ResourceTypes"))
    private val endpoint = ScimResourceTypesEndpoint(listOf(ScimUserEndpoint::class))

    private fun Response.asJsonNode(): JsonNode {
        val json = entity as String
        return JsonUtils.getObjectReader().readTree(json)
    }

    @Test
    fun `search throws ForbiddenException when filter is provided`() {
        assertThrows<ForbiddenException> {
            endpoint.search(uriInfo, "name eq \"User\"")
        }
    }

    @Test
    fun `search returns resource types when no filter`() {
        val response = endpoint.search(uriInfo, null)
        val root = response.asJsonNode()

        assertEquals(1, root["totalResults"].asInt())

        val resources = root["Resources"]
        assertTrue(resources.isArray)

        val userResourceType =
            resources.firstOrNull {
                it["name"]?.asString() == "User"
            }

        assertNotNull(userResourceType)
        assertEquals("User", userResourceType!!["id"].asString())
        assertEquals("User", userResourceType["name"].asString())
    }

    @Test
    fun `get returns resource type when requested by id`() {
        val resourceTypes = endpoint.getResourceTypes()
        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val node = endpoint.get(id, uriInfo).asJsonNode()

        assertEquals(id, node["id"].asString())
        assertEquals(name, node["name"].asString())
    }

    @Test
    fun `get returns resource type when requested by name`() {
        val resourceTypes = endpoint.getResourceTypes()
        val first = resourceTypes.first()

        val id = first.id
        val name = first.name

        val node = endpoint.get(name, uriInfo).asJsonNode()

        assertEquals(id, node["id"].asString())
        assertEquals(name, node["name"].asString())
    }

    @Test
    fun `get throws ResourceNotFoundException when resource type does not exist`() {
        assertThrows<ResourceNotFoundException> {
            endpoint.get("not-a-real-resource-type", uriInfo)
        }
    }
}
