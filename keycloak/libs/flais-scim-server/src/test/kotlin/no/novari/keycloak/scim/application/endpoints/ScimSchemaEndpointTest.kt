package no.novari.keycloak.scim.application.endpoints

import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.utils.JsonUtils
import jakarta.ws.rs.core.Response
import no.novari.keycloak.scim.endpoints.ScimSchemaEndpoint
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import java.net.URI

class ScimSchemaEndpointTest {
    private var uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Schemas"))
    private val endpoint = ScimSchemaEndpoint(listOf(ScimUserEndpoint::class))

    private fun Response.asJsonNode(): JsonNode {
        val json = entity as String
        return JsonUtils.getObjectReader().readTree(json)
    }

    @Test
    fun `search throws ForbiddenException when filter is provided`() {
        assertThrows<ForbiddenException> {
            endpoint.search(uriInfo, "id eq \"test\"")
        }
    }

    @Test
    fun `search returns schemas and user schema contains externalId attribute`() {
        val response = endpoint.search(uriInfo, null)
        val root = response.asJsonNode()

        val resources = root["Resources"]
        assertTrue(resources.isArray)

        val userSchema =
            resources.firstOrNull {
                it["id"]?.asString() == "urn:ietf:params:scim:schemas:core:2.0:User"
            }

        assertNotNull(userSchema)

        val attributesNode = userSchema!!["attributes"] as ArrayNode

        val externalAttr =
            attributesNode.firstOrNull { attr ->
                attr["name"]?.asString() == "externalId"
            }

        assertNotNull(externalAttr)

        assertEquals("string", externalAttr!!["type"].asString())
        assertEquals(false, externalAttr["multiValued"].asBoolean())
        assertEquals("Identifier for the User as defined by the client.", externalAttr["description"].asString())
        assertEquals(false, externalAttr["required"].asBoolean())
        assertEquals(true, externalAttr["caseExact"].asBoolean())
        assertEquals("readWrite", externalAttr["mutability"].asString())
        assertEquals("default", externalAttr["returned"].asString())
        assertEquals("none", externalAttr["uniqueness"].asString())
    }

    @Test
    fun `get returns schema when requested by id`() {
        val id = "urn:ietf:params:scim:schemas:core:2.0:User"
        val node = endpoint.get(id, uriInfo).asJsonNode()

        assertEquals(id, node["id"].asString())
        assertEquals("User", node["name"].asString())
    }

    @Test
    fun `get returns schema when requested by name`() {
        val node = endpoint.get("User", uriInfo).asJsonNode()

        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", node["id"].asString())
        assertEquals("User", node["name"].asString())
    }

    @Test
    fun `get throws ResourceNotFoundException when schema does not exist`() {
        assertThrows<ResourceNotFoundException> {
            endpoint.get("does-not-exist", uriInfo)
        }
    }
}
